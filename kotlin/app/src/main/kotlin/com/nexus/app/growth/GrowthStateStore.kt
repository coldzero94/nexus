package com.nexus.app.growth

import android.content.Context
import com.nexus.core.ClassAffinity

/**
 * 성장 탭이 마지막으로 보여준 레벨·성향 (#61, E3-15) — 재방문 시 변화 감지의 기준점.
 * 레벨 마커는 **단조 증가**로만 갱신한다: 표시 레벨은 최근 28일 창 계산이라 세션이 창을
 * 빠져나가면 내려갈 수 있지만(원장 배선 전 v1 한계), 캐릭터 불퇴행 원칙에 따라 하락을
 * 축하 기준점에 반영하지 않는다 — 같은 레벨을 두 번 축하하지 않기 위함.
 */
class GrowthStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 마지막으로 본 레벨, 0 = 최초(축하 없이 기준점만 설정). */
    val lastSeenLevel: Int
        get() = prefs.getInt(KEY_LEVEL, 0)

    /** 마지막으로 본 클래스 성향, null = 최초. */
    val lastSeenAffinity: ClassAffinity?
        get() = prefs.getString(KEY_AFFINITY, null)?.let { name ->
            ClassAffinity.entries.firstOrNull { it.name == name }
        }

    /** 기준점 갱신 — 레벨은 하락 무시(단조), 성향은 현재값으로. 단일 edit(반쪽 갱신 방지). */
    fun recordSeen(level: Int, affinity: ClassAffinity) {
        prefs.edit()
            .putInt(KEY_LEVEL, maxOf(lastSeenLevel, level))
            .putString(KEY_AFFINITY, affinity.name)
            .apply()
    }

    private companion object {
        const val PREFS = "nexus_growth_state"
        const val KEY_LEVEL = "last_seen_level"
        const val KEY_AFFINITY = "last_seen_affinity"
    }
}
