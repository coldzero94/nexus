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

    /** 개봉 — 시작 시각 제거(보상 지급은 E5-7에서 이 지점에 연결). */
    fun open() {
        prefs.edit().remove(KEY_STARTED_AT).apply()
    }

    private companion object {
        const val PREFS = "nexus_expedition"
        const val KEY_STARTED_AT = "started_at_millis"
    }
}
