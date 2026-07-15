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
import java.io.IOException
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
            if (outcome.deletedRecordIds.isNotEmpty()) {
                // #133: 삭제 감지 → 보상 취소 대상. 실제 RewardLedger.cancel() 배선은 원장 영속화(Room) 후.
                Log.i(TAG, "deleted records to compensate: ${outcome.deletedRecordIds}")
            }
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
        } catch (e: IllegalStateException) {
            Log.w(TAG, "health sync state failure", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
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
