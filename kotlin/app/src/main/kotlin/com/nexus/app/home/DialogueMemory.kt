package com.nexus.app.home

import android.content.Context

/**
 * 대사 노출 기록 (#29, E4-5) — 반복 회피 선택기([com.nexus.core.DialogueSelector])의 입력.
 * 오래된 순 목록을 구분자 문자열로 영속화(줄 수가 작아 SharedPreferences로 충분).
 */
class DialogueMemory(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var recent: List<String>
        get() = prefs.getString(KEY_RECENT, "").orEmpty().split(SEP).filter { it.isNotEmpty() }
        set(value) {
            prefs.edit().putString(KEY_RECENT, value.joinToString(SEP)).apply()
        }

    companion object {
        /** 회피 창 — 풀 최소 크기(대사 5개)보다 작게 유지해야 항상 신선한 후보가 남는다. */
        const val RECENT_CAPACITY = 3

        private const val PREFS = "nexus_dialogue"
        private const val KEY_RECENT = "recent_lines"
        private const val SEP = "\u0001" // 대사에 나올 수 없는 제어문자
    }
}
