package com.nexus.core

/**
 * 개인 계수 베이스라인 (#14, MVP §5 · #2 ① 반영).
 * '자기 기준 성장' — 오늘 활동을 최근 **활동일** 평균과 비교한다.
 * 휴식일을 평균에서 제외하는 게 핵심: 휴식 포함 일평균으로 나누면 활동일마다 상한(1.5)에 포화됨(#2 발견).
 */
object Baseline {
    const val WINDOW_DAYS = 14
    const val NEUTRAL = 1.0 // 콜드스타트·비활동일 기본값

    /**
     * 개인 계수 = 오늘 기본점수 ÷ 최근 [WINDOW_DAYS]일 **활동일** 평균, [XpEngine.PERSONAL_COEF_MIN]~[MAX] 클램프.
     * @param todayBase 오늘 기본점수(0=휴식 → NEUTRAL, 어차피 base 0이면 XP 0)
     * @param priorDailyBases 이전 일별 기본점수(휴식일 0 포함, 오늘 제외). 최근 [WINDOW_DAYS]일만 사용.
     */
    fun personalCoefficient(
        todayBase: Double,
        priorDailyBases: List<Double>,
    ): Double {
        if (todayBase <= 0.0) return NEUTRAL
        val activeDays = priorDailyBases.takeLast(WINDOW_DAYS).filter { it > 0.0 }
        if (activeDays.isEmpty()) return NEUTRAL // 콜드스타트: 비교 대상 없음 → 중립
        val avg = activeDays.average()
        if (avg <= 0.0) return NEUTRAL
        return (todayBase / avg).coerceIn(XpEngine.PERSONAL_COEF_MIN, XpEngine.PERSONAL_COEF_MAX)
    }
}
