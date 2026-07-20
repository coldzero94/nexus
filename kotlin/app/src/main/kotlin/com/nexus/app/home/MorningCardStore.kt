package com.nexus.app.home

import android.content.Context

/**
 * 아침 카드 노출 기록 (#36, E5-3) — 하루 1회(완료 기준). 확인한 날짜를 저장하고,
 * 소비는 확인 시점(#61 패턴) — 미확인 재진입엔 다시 뜨고, 확인하면 그날은 끝.
 */
class MorningCardStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val lastShownEpochDay: Long
        get() = prefs.getLong(KEY_LAST_SHOWN, 0L)

    fun markShown(epochDay: Long) {
        prefs.edit().putLong(KEY_LAST_SHOWN, epochDay).apply()
    }

    private companion object {
        const val PREFS = "nexus_morning_card"
        const val KEY_LAST_SHOWN = "last_shown_epoch_day"
    }
}
