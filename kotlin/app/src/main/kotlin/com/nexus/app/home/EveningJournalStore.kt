package com.nexus.app.home

import android.content.Context

/**
 * 저녁 일지 노출 기록 (#70, E5-9) — 하루 1회 생성(Pikmin 일기 패턴).
 * 아침 카드(#36)와 대칭: 확인 시점 소비, 최초는 기준점만.
 */
class EveningJournalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 0 = 최초(카드 없이 오늘로 기준점만 설정). */
    val lastShownEpochDay: Long
        get() = prefs.getLong(KEY_LAST_SHOWN, UNSET)

    fun markShown(epochDay: Long) {
        prefs.edit().putLong(KEY_LAST_SHOWN, epochDay).apply()
    }

    companion object {
        const val UNSET = 0L

        /** 일지가 열리는 시각 — 하루가 어느 정도 쌓인 저녁부터. */
        const val OPEN_HOUR = 18

        private const val PREFS = "nexus_evening_journal"
        private const val KEY_LAST_SHOWN = "last_shown_epoch_day"
    }
}
