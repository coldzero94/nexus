package com.nexus.app.backup

import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.util.Log
import com.nexus.app.data.EnergyStore
import com.nexus.app.data.ExpeditionStore
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardEventEntity
import com.nexus.app.home.EveningJournalStore
import com.nexus.app.home.MorningCardStore
import com.nexus.app.home.SettlementStore
import com.nexus.app.settings.GoalStore
import com.nexus.app.settings.RestModeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "BackupManager"

/** 가져오기 파일 크기 상한 — 원장 수년치도 수 MB 수준(BACKEND.md §3), 그 이상은 손상/오파일. */
private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024

/**
 * 수동 백업 (#51, E8-6) — SAF Uri로 내보내고/가져온다. 실패는 false(호출부가 토스트 안내,
 * #130 catch 계약). 가져오기는 **멱등 병합**: 원장은 (idempotencyKey, type) IGNORE 삽입이라
 * 중복·재가져오기에 안전하고, 기존 기록을 지우지 않는다(불변 원장 원칙).
 */
object BackupManager {

    suspend fun exportTo(context: Context, uri: Uri): Boolean = runGuarded("export") {
        val text = withContext(Dispatchers.IO) { BackupCodec.encode(collect(context)) }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: error("output stream unavailable")
        }
        true
    }

    suspend fun importFrom(context: Context, uri: Uri): Boolean = runGuarded("import") {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // 상한+1까지만 읽어 초과를 판정 — 전량 로드 후 검사하면 상한이 OOM을 못 막는다(#51 리뷰)
                stream.readNBytes(MAX_IMPORT_BYTES + 1)
                    .also { require(it.size <= MAX_IMPORT_BYTES) { "backup file too large" } }
            }?.toString(Charsets.UTF_8) ?: error("input stream unavailable")
        }
        val payload = BackupCodec.decode(text)
        withContext(Dispatchers.IO) { restore(context, payload) }
        true
    }

    private suspend fun collect(context: Context): BackupPayload {
        val events = NexusDatabase.get(context).rewardEventDao().all()
        return BackupPayload(
            backupVersion = BackupCodec.VERSION,
            exportedAtEpochMillis = System.currentTimeMillis(),
            events = events.map {
                BackupEvent(
                    idempotencyKey = it.idempotencyKey,
                    xp = it.xp,
                    type = it.type,
                    dataOrigin = it.dataOrigin,
                    recordingMethod = it.recordingMethod,
                    formulaVersion = it.formulaVersion,
                    epochMillis = it.epochMillis,
                    epochDay = it.epochDay,
                )
            },
            snapshot = BackupSnapshot(
                energyTotalSpent = EnergyStore(context).totalSpent,
                expeditionStartedAtMillis = ExpeditionStore(context).startedAtMillis,
                settlementLastSeenXp = SettlementStore(context).lastSeenXp.takeIf { it != SettlementStore.UNSET },
                morningLastShownEpochDay =
                MorningCardStore(context).lastShownEpochDay.takeIf { it != MorningCardStore.UNSET },
                journalLastShownEpochDay =
                EveningJournalStore(context).lastShownEpochDay.takeIf { it != EveningJournalStore.UNSET },
                weeklyGoalDays = GoalStore(context).weeklyGoalDays,
                restModeEnabled = RestModeStore(context).enabled,
                restModeSinceEpochDay = RestModeStore(context).sinceEpochDay.takeIf { it != 0L },
            ),
        )
    }

    private suspend fun restore(context: Context, payload: BackupPayload) {
        val dao = NexusDatabase.get(context).rewardEventDao()
        payload.events.forEach { e ->
            dao.insert(
                RewardEventEntity(
                    idempotencyKey = e.idempotencyKey,
                    xp = e.xp,
                    type = e.type,
                    dataOrigin = e.dataOrigin,
                    recordingMethod = e.recordingMethod,
                    formulaVersion = e.formulaVersion,
                    epochMillis = e.epochMillis,
                    epochDay = e.epochDay,
                ),
            )
        }
        with(payload.snapshot) {
            EnergyStore(context).restoreTotalSpent(energyTotalSpent)
            expeditionStartedAtMillis?.let { ExpeditionStore(context).start(it) }
            settlementLastSeenXp?.let { SettlementStore(context).markSeen(it) }
            morningLastShownEpochDay?.let { MorningCardStore(context).markShown(it) }
            journalLastShownEpochDay?.let { EveningJournalStore(context).markShown(it) }
            weeklyGoalDays?.let { GoalStore(context).weeklyGoalDays = it }
            // 시작일까지 복원 — 복원일로 리셋되면 이전 휴식일이 면제에서 빠져 컨디션이 왜곡된다(#51 리뷰 F2)
            if (restModeEnabled && restModeSinceEpochDay != null) {
                RestModeStore(context).setEnabled(true, restModeSinceEpochDay)
            } else {
                RestModeStore(context).setEnabled(restModeEnabled)
            }
        }
    }

    private suspend fun runGuarded(op: String, block: suspend () -> Boolean): Boolean = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.w(TAG, "$op IO failure", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "$op permission failure", e)
        false
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "$op invalid payload", e) // SerializationException 포함(하위 타입)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "$op state failure", e)
        false
    } catch (e: SQLException) {
        Log.w(TAG, "$op db failure", e)
        false
    }
}
