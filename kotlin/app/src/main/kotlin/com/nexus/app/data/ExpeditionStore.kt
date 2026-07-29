package com.nexus.app.data

import android.content.Context

/**
 * 원정 상태 영속화 (#34·#204) — 진행 중인 원정의 시작 시각(상태는 core
 * [com.nexus.core.ExpeditionEngine]이 이 시각에서만 파생한다, 재시작 정합)과 완료 누적 수를 보관한다.
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
     * 진행 중인 원정이 없으면 아무것도 하지 않고 **false**를 돌려준다 — 개봉 버튼 연타처럼 같은
     * 개봉이 두 번 불려도 카운터·계측·(후속) 보상이 중복되지 않게(#204 리뷰). 호출자는 반환값으로
     * 부수효과를 게이트한다. 두 값은 같은 edit으로 원자적으로 쓴다.
     *
     * @return 실제로 개봉했으면 true, 열 원정이 없었으면 false
     */
    fun open(): Boolean {
        if (startedAtMillis == null) return false
        prefs
            .edit()
            .remove(KEY_STARTED_AT)
            .putInt(KEY_COMPLETED, completedCount + 1)
            .apply()
        return true
    }

    /** 백업 복원용 (#204) — 완료 수 승계. [EnergyStore.restoreTotalSpent]와 같은 패턴. */
    fun restoreCompletedCount(count: Int) {
        prefs.edit().putInt(KEY_COMPLETED, count.coerceAtLeast(0)).apply()
    }

    private companion object {
        const val PREFS = "nexus_expedition"
        const val KEY_STARTED_AT = "started_at_millis"
        const val KEY_COMPLETED = "completed_count"
    }
}
