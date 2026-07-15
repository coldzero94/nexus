package com.nexus.app.notify

import android.content.Context

/** 일자별 알림 발송 수 (#33) — 규율(일 2건)의 카운터. 날이 바뀌면 자동 리셋(키에 날짜 포함). */
class NotificationLedger(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sentCount(epochDay: Long): Int = if (prefs.getLong(KEY_DAY, 0L) == epochDay) prefs.getInt(KEY_COUNT, 0) else 0

    fun recordSent(epochDay: Long) {
        val current = sentCount(epochDay)
        prefs.edit()
            .putLong(KEY_DAY, epochDay)
            .putInt(KEY_COUNT, current + 1)
            .apply()
    }

    private companion object {
        const val PREFS = "nexus_notify"
        const val KEY_DAY = "sent_day"
        const val KEY_COUNT = "sent_count"
    }
}
