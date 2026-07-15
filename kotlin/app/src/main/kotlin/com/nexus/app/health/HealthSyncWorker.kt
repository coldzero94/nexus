package com.nexus.app.health

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.core.TrustPolicy
import com.nexus.core.XpEngine
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * 백그라운드 증분 동기화 워커 (#8). 15분 주기로 Changes 델타를 읽는다.
 * 완료 기준: 앱 안 열고 운동 → 다음 주기에 반영. 실패는 지수 백오프 재시도(레이트리밋 준수).
 */
class HealthSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (HealthConnectClient.getSdkStatus(applicationContext) != HealthConnectClient.SDK_AVAILABLE) {
            return Result.success() // HC 미가용 → 재시도 무의미
        }
        val client = HealthConnectClient.getOrCreate(applicationContext)
        val store = TokenStore(applicationContext)
        return try {
            val outcome = HealthConnectSync(client, store).sync()
            store.lastSyncEpochMillis = System.currentTimeMillis()
            store.lastChangeCount = outcome.upserts + outcome.deletions
            appendToLedger(client, outcome.deletedRecordIds)
            Result.success()
        } catch (e: CancellationException) {
            throw e // 코루틴 취소는 전파(삼키지 않음)
        } catch (e: IOException) {
            Log.w(TAG, "health sync IO failure", e)
            Result.retry()
        } catch (e: RemoteException) {
            Log.w(TAG, "health sync remote failure", e)
            Result.retry()
        } catch (e: SecurityException) {
            Log.w(TAG, "health sync permission failure — not retrying", e)
            Result.failure() // 권한 문제는 재시도 무의미
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "health sync invalid-argument failure — not retrying", e)
            Result.failure() // 인자/레코드 이상은 재시도 무의미 (#130 재감사)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "health sync state failure", e)
            Result.retry()
        }
    }

    /**
     * 원장 append (#162): 최근 세션을 멱등 지급하고(이미 지급된 키는 DB가 무시),
     * 삭제 감지분은 보상 취소(#133). 지급 XP = 기본점수 × 개인 신뢰 계수(무상한 —
     * 일 상한은 합산 시점 규칙, LedgerMath KDoc). Tier C·3축 외는 지급 제외.
     */
    private suspend fun appendToLedger(client: HealthConnectClient, deletedIds: List<String>) {
        val ledger = RewardLedgerRepository(NexusDatabase.get(applicationContext).rewardEventDao())
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        ExerciseRepository(client).readRecentSessions(days = GRANT_WINDOW_DAYS).forEach { session ->
            val type = session.type ?: return@forEach
            if (!TrustPolicy.isXpEligible(session.trustTier)) return@forEach
            val xp = (
                XpEngine.baseScore(type, session.durationMinutes.toInt()) *
                    session.trustTier.personalXpMultiplier
                ).toInt()
            ledger.grant(
                idempotencyKey = session.id,
                xp = xp,
                dataOrigin = session.dataOrigin,
                recordingMethod = session.recordingMethod,
                epochMillis = now,
                epochDay = session.start.atZone(zone).toLocalDate().toEpochDay(),
            )
        }
        deletedIds.forEach { id ->
            if (ledger.cancel(id, now)) Log.i(TAG, "reward cancelled for deleted record")
        }
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
        private const val GRANT_WINDOW_DAYS = 7
        private const val UNIQUE_NAME = "nexus_health_sync"

        /** 15분 주기 워커 등록(중복 무시). 온보딩 연결 성공 후 호출. */
        fun enqueuePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<HealthSyncWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
