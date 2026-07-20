package com.nexus.core

/**
 * 근력 축 XP (#16, MVP §5): `세션 시간 × 주당 빈도 보너스 × 심박존 보정`.
 * **세트·렙 기록 강요 제로** — 볼륨(중량×렙) 기반 정밀 힘 스탯은 후속(로거 앱 API 제휴 시).
 * 인정 경로(Tier A/B/C)는 신뢰 필터(#9)가 담당 — 여기선 근력 XP 산식만.
 */
object StrengthAxis {
    // 주당 근력 세션 빈도 보너스 — 꾸준함 보상(2회·3회·4회+ 구간). 밸런스는 steps/energy CSV와 별도 #77 튜닝.
    private const val BONUS_TWICE = 1.1
    private const val BONUS_THRICE = 1.2
    private const val BONUS_FREQUENT = 1.3
    private const val FREQUENT_THRESHOLD = 3

    /** 부동소수 곱(예: 60×1.2=71.9999…)에서 floor가 어긋나지 않도록 더하는 극소값. */
    private const val FLOOR_EPSILON = 1e-9

    /** 주당 근력 세션 빈도 보너스(꾸준함 보상). n=이번 주 근력 세션 수(이번 세션 포함). */
    fun weeklyFrequencyBonus(sessionsThisWeek: Int): Double = when {
        sessionsThisWeek <= 0 -> 0.0
        sessionsThisWeek == 1 -> 1.0
        sessionsThisWeek == 2 -> BONUS_TWICE
        sessionsThisWeek == FREQUENT_THRESHOLD -> BONUS_THRICE
        else -> BONUS_FREQUENT // 4회+
    }

    /**
     * 힘 XP = 근력 기본점수(시간) × 주당 빈도 보너스 × 심박존 보정.
     * @param minutes 세션 시간(분)
     * @param sessionsThisWeek 이번 주 근력 세션 수(이번 세션 포함)
     * @param hrCorrection 심박존 보정(#15, 없으면 1.0)
     */
    fun strengthXp(minutes: Int, sessionsThisWeek: Int, hrCorrection: Double = 1.0): Int {
        require(minutes >= 0) { "minutes must be >= 0" }
        require(hrCorrection >= 0) { "hrCorrection must be >= 0" }
        val base = XpEngine.baseScore(ActivityType.STRENGTH, minutes).toDouble()
        return (base * weeklyFrequencyBonus(sessionsThisWeek) * hrCorrection + FLOOR_EPSILON).toInt()
    }
}
