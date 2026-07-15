package com.nexus.app.notify

import android.content.Context

/** 리마인더 토글 상태 (#33) — 기본 꺼짐(알림은 옵트인, 온보딩에서 조르지 않는다). */
class NotificationSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    private companion object {
        const val PREFS = "nexus_notify_settings"
        const val KEY_ENABLED = "reminder_enabled"
    }
}
