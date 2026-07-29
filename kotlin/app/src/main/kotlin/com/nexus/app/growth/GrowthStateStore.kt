package com.nexus.app.growth

import android.content.Context
import com.nexus.core.ClassAffinity
import com.nexus.core.Stat

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

    /**
     * 마지막으로 본 능력치 벡터 (#219) — 레벨업 축하의 "+N" 기준. 비어 있으면 최초(상승 없음으로 본다).
     *
     * `Stat이름=값` 쌍을 `;`로 이어 한 문자열에 담는다 — 스탯이 대여섯 개뿐이라 별도 직렬화는 과하다.
     * 모르는 이름(스탯 추가·개명)은 조용히 버린다: 축하 한 번을 놓칠 뿐 크래시보다 낫다.
     */
    val lastSeenStats: Map<Stat, Int>
        get() = prefs.getString(KEY_STATS, null)
            ?.split(';')
            ?.mapNotNull { pair ->
                val (name, value) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val stat = Stat.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                value.toIntOrNull()?.let { stat to it }
            }
            ?.toMap()
            .orEmpty()

    /**
     * 기준점 갱신 — 레벨은 하락 무시(단조), 성향·능력치는 현재값으로. 단일 edit(반쪽 갱신 방지).
     *
     * 능력치는 단조로 두지 않는다: 상승분 계산은 "직전에 보여준 값"과의 차이여야 하는데, 최댓값을
     * 남기면 창 이탈로 내려갔다 회복할 때 그 회복분이 통째로 '상승'으로 잡혀 없는 성장을 축하한다.
     */
    fun recordSeen(level: Int, affinity: ClassAffinity, stats: Map<Stat, Int> = emptyMap()) {
        prefs.edit()
            .putInt(KEY_LEVEL, maxOf(lastSeenLevel, level))
            .putString(KEY_AFFINITY, affinity.name)
            .apply {
                if (stats.isNotEmpty()) {
                    putString(KEY_STATS, stats.entries.joinToString(";") { "${it.key.name}=${it.value}" })
                }
            }
            .apply()
    }

    private companion object {
        const val PREFS = "nexus_growth_state"
        const val KEY_LEVEL = "last_seen_level"
        const val KEY_AFFINITY = "last_seen_affinity"
        const val KEY_STATS = "last_seen_stats"
    }
}
