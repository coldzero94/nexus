package com.nexus.app.onboarding

import android.content.Context

/**
 * 온보딩 완료 영속화 (#44) — 완료가 프로세스 사망을 넘게 한다.
 * 기존 rememberSaveable 단독은 콜드스타트마다 온보딩을 다시 밟는 버그였음.
 * 초기 레벨 연출도 여기서 1회 관리(최초 완료 직후에만).
 */
class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_COMPLETED, value).apply()
        }

    var connected: Boolean
        get() = prefs.getBoolean(KEY_CONNECTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONNECTED, value).apply()
        }

    /** 초기 레벨 연출(#44)을 이미 보여줬는가 — 최초 연결 직후 1회. */
    var initialLevelShown: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_LEVEL_SHOWN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INITIAL_LEVEL_SHOWN, value).apply()
        }

    private companion object {
        const val PREFS = "nexus_onboarding"
        const val KEY_COMPLETED = "completed"
        const val KEY_CONNECTED = "connected"
        const val KEY_INITIAL_LEVEL_SHOWN = "initial_level_shown"
    }
}
