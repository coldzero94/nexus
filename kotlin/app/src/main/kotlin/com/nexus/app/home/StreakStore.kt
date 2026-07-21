package com.nexus.app.home

import android.content.Context

/**
 * 최장 기세 영속 (#214) — 창(window) 밖으로 밀려난 과거 기록도 잃지 않게 최장값만 단조 저장한다.
 * 현재 기세는 원장에서 매번 계산하므로 저장하지 않는다(단일 진실 = 원장).
 */
class StreakStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val longest: Int
        get() = prefs.getInt(KEY_LONGEST, 0)

    /** 관측된 최장값으로 갱신 — 단조 증가만(퇴행 없음, 불변식 ④). */
    fun observe(longest: Int) {
        if (longest > this.longest) prefs.edit().putInt(KEY_LONGEST, longest).apply()
    }

    private companion object {
        const val PREFS = "nexus_streak"
        const val KEY_LONGEST = "longest"
    }
}
