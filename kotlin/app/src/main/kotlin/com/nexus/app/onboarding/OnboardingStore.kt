package com.nexus.app.onboarding

import android.content.Context
import com.nexus.core.FirstSession

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

    /**
     * 첫 세션 루프의 기준선 (#211) — 연결 시점의 누적 XP. [com.nexus.core.FirstSession.NO_BASELINE] = 미설정.
     *
     * 소급 지급분(#44)을 여기 흡수해 두고 **이 값을 넘어선 증가분**만 사용자 활동으로 본다.
     * 한 번 박히면 덮어쓰지 않는다 — 다시 잡으면 그 사이의 첫 활동이 통째로 소급분으로 오인된다.
     */
    var firstXpBaselineXp: Int
        get() = prefs.getInt(KEY_FIRST_XP_BASELINE, FirstSession.NO_BASELINE)
        set(value) {
            prefs.edit().putInt(KEY_FIRST_XP_BASELINE, value).apply()
        }

    /**
     * 첫 세션 루프(#211) 대상인가 — **이번 릴리스 이후 온보딩을 마친 사용자만** true.
     *
     * 이 앱을 이미 몇 주 쓰던 알파 테스터에게 "첫 성장까지, 10분"이 뜨면 명백한 거짓말이다. 기존
     * 설치는 이 플래그가 없으므로(온보딩을 다시 밟지 않는다) 루프 전체가 조용히 비켜간다.
     */
    var firstSessionEligible: Boolean
        get() = prefs.getBoolean(KEY_FIRST_SESSION_ELIGIBLE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_SESSION_ELIGIBLE, value).apply()
        }

    /** 첫 행동 코치(#211)를 이미 보여줬는가 — 1회. */
    var firstCoachShown: Boolean
        get() = prefs.getBoolean(KEY_FIRST_COACH_SHOWN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_COACH_SHOWN, value).apply()
        }

    /** 첫 활동 XP 축하(#211)를 이미 보여줬는가 — 1회. */
    var firstXpCelebrated: Boolean
        get() = prefs.getBoolean(KEY_FIRST_XP_CELEBRATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_XP_CELEBRATED, value).apply()
        }

    private companion object {
        const val PREFS = "nexus_onboarding"
        const val KEY_COMPLETED = "completed"
        const val KEY_CONNECTED = "connected"
        const val KEY_INITIAL_LEVEL_SHOWN = "initial_level_shown"
        const val KEY_FIRST_SESSION_ELIGIBLE = "first_session_eligible"
        const val KEY_FIRST_XP_BASELINE = "first_xp_baseline"
        const val KEY_FIRST_COACH_SHOWN = "first_coach_shown"
        const val KEY_FIRST_XP_CELEBRATED = "first_xp_celebrated"
    }
}
