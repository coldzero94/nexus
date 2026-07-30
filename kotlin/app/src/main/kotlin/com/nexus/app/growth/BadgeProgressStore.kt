package com.nexus.app.growth

import android.content.Context

/**
 * 획득한 배지 id 영속화 (#175, E5-11) — 배지는 캐릭터에 영구히 남는 수집 자산이라(BENCHMARK.md)
 * 한 번 획득하면 조건이 다시 거짓이 되어도 유지된다. [GrowthStateStore]와 같은 prefs 패턴.
 * **축하 신호는 여기가 아니다** (#218): 저장 전 [earned]와의 차집합은 `loadBadges` 안에서만 살고,
 * `addEarned`가 도는 순간 사라진다. 사용자에게 보여줄 "아직 축하하지 않은 배지"는
 * [BadgeCelebrationStore]가 영속으로 들고 있다 — #28 위젯 `newBadge` 점도 그쪽을 봐야 한다
 * (차집합을 보면 표시 직후 사라지고, 축하 대기 집합을 보면 '확인'까지 켜져 있다).
 *
 * 상시 배지와 월 한정 배지(#206)는 [prefsName]으로 저장소를 나눈다 — id 공간이 겹치지 않더라도
 * 한 파일에 섞으면 상시 배지 조회에 지난 달 id가 딸려 들어와 의미가 흐려진다.
 */
class BadgeProgressStore(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** 지금까지 획득한 배지 id. getStringSet 반환본은 변형 금지라 복사본을 돌려준다. */
    val earned: Set<String>
        get() = prefs.getStringSet(KEY_EARNED, emptySet())?.toSet() ?: emptySet()

    /** 획득 집합에 합류(합집합) — 회수는 없다. 빈 입력은 무시(불필요한 쓰기 방지). */
    fun addEarned(ids: Set<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY_EARNED, earned + ids).apply()
    }

    companion object {
        /** 월 한정 배지 전용 저장소 (#206) — 상시 배지와 분리. */
        const val MONTHLY_PREFS = "nexus_monthly_badge_progress"

        private const val PREFS = "nexus_badge_progress"
        private const val KEY_EARNED = "earned_badge_ids"
    }
}
