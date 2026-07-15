package com.nexus.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexus.app.R
import com.nexus.core.NotificationPolicy
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 저녁 리마인더 1종 (#33, E4-9) — 캐릭터가 부르는 가벼운 알림.
 * 규율은 core [NotificationPolicy](일 2건 상한·조용 시간 21~09시)가 판정하고,
 * 발송 카운트는 [NotificationLedger]가 일 단위로 기록한다. 알림 권한이 없으면 조용히 건너뛴다
 * (권한은 설정 토글에서 명시 요청 — 온보딩에서 조르지 않는다).
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!NotificationSettings(context).enabled) return Result.success()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success() // 권한 회수됨 — 재시도 무의미, 토글을 다시 켤 때 재요청
        }
        val ledger = NotificationLedger(context)
        val now = LocalDateTime.now()
        if (!NotificationPolicy.canNotify(now.hour, ledger.sentCount(LocalDate.now().toEpochDay()))) {
            return Result.success()
        }
        notify(context)
        ledger.recordSent(LocalDate.now().toEpochDay())
        return Result.success()
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
            .setContentTitle(context.getString(R.string.notify_reminder_title))
            .setContentText(context.getString(R.string.notify_reminder_body))
            .setAutoCancel(true)
            .build()
        manager.notify(REMINDER_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "nexus_reminder"
        private const val REMINDER_ID = 1001
        private const val UNIQUE_NAME = "nexus_evening_reminder"
        private const val CHECK_INTERVAL_HOURS = 4L

        /** 리마인더 등록 — 4시간 주기 점검(조용 시간·상한은 매 실행에서 판정). */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
