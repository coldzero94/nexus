package com.nexus.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexus.app.R
import com.nexus.app.data.ExpeditionStore
import com.nexus.core.ExpeditionEngine
import com.nexus.core.ExpeditionState
import com.nexus.core.NotificationPolicy
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 원정 완료 알림 (#71, E5-10) — 출발 시 8시간 뒤로 예약되는 1회성 워커.
 * 규율(#33)은 리마인더와 **같은 카운터·같은 판정**을 쓴다: 일 2건 상한·조용 시간 21~09시.
 * 발송 조건: 알림 토글 ON + 권한 + 규율 통과 + **여전히 개봉 대기 상태**(이미 개봉했으면 침묵).
 * 조용 시간·상한에 걸리면 그냥 보내지 않는다 — 원정은 앱을 열면 어차피 보이는 상태라
 * 재예약으로 조를 이유가 없다(알림은 신뢰 자원).
 */
class ExpeditionReturnWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val ledger = NotificationLedger(context)
        if (shouldNotify(context, ledger)) {
            notify(context)
            ledger.recordSent(LocalDate.now().toEpochDay())
        }
        return Result.success() // 미발송도 성공 — 재예약으로 조르지 않는다(KDoc)
    }

    private fun shouldNotify(context: Context, ledger: NotificationLedger): Boolean {
        if (!NotificationSettings(context).enabled) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val state = ExpeditionEngine.stateAt(ExpeditionStore(context).startedAtMillis, System.currentTimeMillis())
        if (state != ExpeditionState.ReadyToOpen) return false // 이미 개봉·미진행 → 침묵
        return NotificationPolicy.canNotify(LocalDateTime.now().hour, ledger.sentCount(LocalDate.now().toEpochDay()))
    }

    private fun notify(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.character_idle_0)
            .setContentTitle(context.getString(R.string.notify_expedition_title))
            .setContentText(context.getString(R.string.notify_expedition_body))
            .setAutoCancel(true)
            .build()
        manager.notify(EXPEDITION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "nexus_reminder" // 리마인더와 같은 채널(캐릭터 알림 일원화)
        private const val EXPEDITION_ID = 1002
        private const val UNIQUE_NAME = "nexus_expedition_return"

        /** 출발 시 호출 — 8시간 뒤 1회 예약(기존 예약은 대체). */
        fun scheduleFor(context: Context, delayMillis: Long = ExpeditionEngine.DURATION_MILLIS) {
            val request = OneTimeWorkRequestBuilder<ExpeditionReturnWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** 개봉 시 호출 — 대기 중 예약 취소(이미 확인한 원정을 알리지 않는다). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
