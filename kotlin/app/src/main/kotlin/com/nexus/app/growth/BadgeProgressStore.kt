package com.nexus.app.growth

import android.content.Context

/**
 * 획득한 배지 id 영속화 (#175, E5-11) — 배지는 캐릭터에 영구히 남는 수집 자산이라(BENCHMARK.md)
 * 한 번 획득하면 조건이 다시 거짓이 되어도 유지된다. [GrowthStateStore]와 같은 prefs 패턴.
 * `newlyUnlocked`(이번에 새로 열린 배지 = #28 `newBadge` 신호원)는 저장 전 [earned]와의 차집합.
 */
class BadgeProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 지금까지 획득한 배지 id. getStringSet 반환본은 변형 금지라 복사본을 돌려준다. */
    val earned: Set<String>
        get() = prefs.getStringSet(KEY_EARNED, emptySet())?.toSet() ?: emptySet()

    /** 획득 집합에 합류(합집합) — 회수는 없다. 빈 입력은 무시(불필요한 쓰기 방지). */
    fun addEarned(ids: Set<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY_EARNED, earned + ids).apply()
    }

    private companion object {
        const val PREFS = "nexus_badge_progress"
        const val KEY_EARNED = "earned_badge_ids"
    }
}
