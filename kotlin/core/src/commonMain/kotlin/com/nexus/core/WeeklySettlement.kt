package com.nexus.core

/**
 * 주간 정산 (#21, E3-9, MVP §5): 주간 기본 XP에 균형 보너스 + 연속성 보너스 적용.
 * 실제 정산 시점(월요일 KST)·트리거는 app/워커가 담당 — core는 정산 산식만.
 */
object WeeklySettlement {
    const val BALANCE_ACTIVE_THRESHOLD = 4
    const val BALANCE_REST_THRESHOLD = 1
    const val BALANCE_BONUS = 0.15
    private const val DAYS_PER_WEEK = 7
    private const val ROUNDING_EPS = 1e-9

    /**
     * @param weeklyBaseXp 그 주 일일 XP 합(정산 전)
     * @param activeDaysThisWeek 이번 주 활동일 수
     * @param activeDaysPrevWeek 직전 주 활동일 수(연속성 판정)
     * @return 균형(활동 ≥4 & 휴식 ≥1 → +15%) × 연속성(#62 직전 주 대비 유지 → +5%) 적용 XP
     */
    fun settle(weeklyBaseXp: Int, activeDaysThisWeek: Int, activeDaysPrevWeek: Int): Int {
        require(weeklyBaseXp >= 0) { "weeklyBaseXp must be >= 0" }
        val restDays = DAYS_PER_WEEK - activeDaysThisWeek
        val balance =
            if (activeDaysThisWeek >= BALANCE_ACTIVE_THRESHOLD && restDays >= BALANCE_REST_THRESHOLD) {
                1.0 + BALANCE_BONUS
            } else {
                1.0
            }
        val consistency = ConsistencyBonus.weeklyMultiplier(activeDaysThisWeek, activeDaysPrevWeek)
        return (weeklyBaseXp * balance * consistency + ROUNDING_EPS).toInt()
    }
}
