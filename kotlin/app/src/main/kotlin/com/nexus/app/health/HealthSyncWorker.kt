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
import com.nexus.app.widget.WidgetUpdater
import java.io.IOException
import java.time.LocalDate
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
        } catch (e: android.database.SQLException) {
            // 원장 DB 문제 — 크래시 루프 대신 백오프 재시도 (#163)
            Log.w(TAG, "ledger db failure", e)
            Result.retry()
        }
    }

    /**
     * 원장 append (#162): 최근 세션 멱등 지급 + 삭제 감지분 보상 취소(#133).
     * 지급 규칙은 [RewardLedgerRepository.grantSessions] 단일 진입점 (#163).
     */
    private suspend fun appendToLedger(client: HealthConnectClient, deletedIds: List<String>) {
        val ledger = RewardLedgerRepository(NexusDatabase.get(applicationContext).rewardEventDao())
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val sessions = ExerciseRepository(client).readRecentSessions(days = GRANT_WINDOW_DAYS)
        ledger.grantSessions(sessions, zone, epochMillis = now)
        deletedIds.forEach { id ->
            if (ledger.cancel(id, now)) Log.i(TAG, "reward cancelled for deleted record")
        }
        // 위젯 갱신 (#40): 동기화가 위젯의 유일한 백그라운드 갱신원 — 15분 준실시간 한계.
        // 기분(#212)의 풍부한 신호(개인계수·주간목표)는 델타만 읽는 워커엔 없어 활동 기반 walk/idle만
        // 전달한다 — 백그라운드 활동의 liveness 유지(#40의 존재 이유). 표정 아트(#66) 랜딩 시엔
        // 홈이 쓴 표정을 워커 walk/idle이 덮어쓰지 않도록 위젯 기분 배선을 재검토해야 한다(#212 리뷰 W1).
        val todayEpoch = LocalDate.now(zone).toEpochDay()
        val todayActive = sessions.any {
            it.type != null && it.start.atZone(zone).toLocalDate().toEpochDay() == todayEpoch
        }
        WidgetUpdater.update(
            context = applicationContext,
            cappedTotalXp = ledger.cappedTotalXp(),
            todayXp = ledger.cappedXpOn(todayEpoch),
            spriteState = if (todayActive) "walk" else "idle",
        )
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
        private const val GRANT_WINDOW_DAYS = 7
        private const val UNIQUE_NAME = "nexus_health_sync"

        /** 동기화 주기(분) — HC 준실시간 한계(30~60분 지연)와 배터리 사이 절충. */
        private const val SYNC_INTERVAL_MINUTES = 15L

        /** 실패 재시도 지수 백오프 시작(초). */
        private const val BACKOFF_START_SECONDS = 30L

        /** 15분 주기 워커 등록(중복 무시). 온보딩 연결 성공 후 호출. */
        fun enqueuePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<HealthSyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_START_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
