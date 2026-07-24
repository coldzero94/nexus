package com.nexus.core

/**
 * 오늘 XP 게이지 스케일 (#259, E16-9) — 순수 함수. 캡 적용된 오늘 XP를 게이지 채움 비율로 매핑하고,
 * 소프트 니([XpEngine.DAILY_KNEE]) 위치·초과 여부를 계산한다.
 *
 * XP 곡선은 니(200)까지 선형, 이후 [XpEngine.DAILY_SOFT_RATE] 감쇠로 하드캡(300)에 접근한다(벽 아님).
 * 게이지는 니를 '벽/한도'가 아니라 '여기부터 천천히 쌓여요' 지점으로 보여, 무처벌 곡선을 그대로 시각화한다.
 */
object XpGaugeScale {
    /** 게이지 전체(0~하드캡) 대비 니 마커 위치 비율([0,1]). 캡이 비정상(<=0)이면 0. */
    fun kneeFraction(knee: Double = XpEngine.DAILY_KNEE, cap: Double = XpEngine.DAILY_HARD_CAP): Float =
        if (cap <= 0.0) 0f else (knee / cap).toFloat().coerceIn(0f, 1f)

    /** 오늘 XP의 전체 채움 비율([0,1]) — todayXp/cap. 초과·음수는 클램프. */
    fun fillFraction(todayXp: Int, cap: Double = XpEngine.DAILY_HARD_CAP): Float =
        if (cap <= 0.0) 0f else (todayXp / cap).toFloat().coerceIn(0f, 1f)

    /** 니 도달 여부 — 게이지 캡션을 '목표까지'에서 '천천히 쌓여요'로 전환하는 분기. */
    fun reachedKnee(todayXp: Int, knee: Double = XpEngine.DAILY_KNEE): Boolean = todayXp >= knee
}
