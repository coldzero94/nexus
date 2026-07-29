package com.nexus.app.data

import android.content.Context

/**
 * 원정 시작 시각 영속화 (#34) — 상태는 core [com.nexus.core.ExpeditionEngine]이
 * 이 시각에서만 파생한다(재시작 정합). 0 = 미진행.
 */
class ExpeditionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val startedAtMillis: Long?
        get() = prefs.getLong(KEY_STARTED_AT, 0L).takeIf { it != 0L }

    fun start(nowMillis: Long) {
        prefs.edit().putLong(KEY_STARTED_AT, nowMillis).apply()
    }

    /** 완료한 원정 수 (#204) — 탐험가 배지 해금 신호. 개봉할 때만 증가한다(출발만으론 안 셈). */
    val completedCount: Int
        get() = prefs.getInt(KEY_COMPLETED, 0)

    /**
     * 개봉 — 시작 시각 제거 + 완료 카운터 증가(보상 지급은 E5-7에서 이 지점에 연결).
     *
     * 진행 중인 원정이 없으면 카운터를 올리지 않는다 — 중복 호출·재구성으로 배지가 거저 해금되지
     * 않게(#204). 두 값은 같은 edit으로 원자적으로 쓴다.
     */
    fun open() {
        if (startedAtMillis == null) return
        prefs
            .edit()
            .remove(KEY_STARTED_AT)
            .putInt(KEY_COMPLETED, completedCount + 1)
            .apply()
    }

    private companion object {
        const val PREFS = "nexus_expedition"
        const val KEY_STARTED_AT = "started_at_millis"
        const val KEY_COMPLETED = "completed_count"
    }
}
