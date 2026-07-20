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
        // 위젯 갱신 (#40): 동기화가 위젯의 유일한 백그라운드 갱신원 — 15분 준실시간 한계
        val todayEpoch = LocalDate.now(zone).toEpochDay()
        WidgetUpdater.update(
            context = applicationContext,
            cappedTotalXp = ledger.cappedTotalXp(),
            todayXp = ledger.cappedXpOn(todayEpoch),
            todayActive = sessions.any {
                it.type != null &&
                    it.start.atZone(zone).toLocalDate().toEpochDay() == todayEpoch
            },
        )
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
