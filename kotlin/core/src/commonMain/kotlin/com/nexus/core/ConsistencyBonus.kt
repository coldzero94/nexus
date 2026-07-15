package com.nexus.core

/**
 * 연속성 보너스 (#62 라운드 1 · #2③): 직전 주 대비 활동일을 **유지/증가**하면 +5%.
 * 폭주형보다 꾸준형을 우대(들쑥날쑥이 주3걷기를 앞서는 문제 완화).
 * 적용은 주간 정산(E3-9/S3 #21)에서 — core는 배수 산출만.
 */
object ConsistencyBonus {
    const val BONUS = 0.05

    /**
     * 주간 연속성 배수. 이번 주 활동일이 1일 이상이고 직전 주 이상이면 1.05, 아니면 1.0.
     * @param activeDaysThisWeek 이번 주 활동일 수
     * @param activeDaysPrevWeek 직전 주 활동일 수
     */
    fun weeklyMultiplier(activeDaysThisWeek: Int, activeDaysPrevWeek: Int): Double =
        if (activeDaysThisWeek >= 1 && activeDaysThisWeek >= activeDaysPrevWeek) 1.0 + BONUS else 1.0
}
