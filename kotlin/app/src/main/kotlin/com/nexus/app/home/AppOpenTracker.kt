package com.nexus.app.home

import android.content.Context
import java.time.LocalDate

/**
 * 앱 실행일 마커 (#30, E4-6) — 복귀 감지의 기준점. 0 = 최초 실행.
 * 판정과 마커 갱신을 분리해, 환영 씬이 뜨기 전에 기준점이 지워지지 않게 한다.
 */
class AppOpenTracker(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val lastOpenEpochDay: Long
        get() = prefs.getLong(KEY_LAST_OPEN, 0L)

    fun recordOpen(todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        prefs.edit().putLong(KEY_LAST_OPEN, todayEpochDay).apply()
    }

    private companion object {
        const val PREFS = "nexus_app_open"
        const val KEY_LAST_OPEN = "last_open_epoch_day"
    }
}
