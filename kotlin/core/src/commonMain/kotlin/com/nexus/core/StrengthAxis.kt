package com.nexus.core

/**
 * 근력 축 XP (#16, MVP §5): `세션 시간 × 주당 빈도 보너스 × 심박존 보정`.
 * **세트·렙 기록 강요 제로** — 볼륨(중량×렙) 기반 정밀 힘 스탯은 후속(로거 앱 API 제휴 시).
 * 인정 경로(Tier A/B/C)는 신뢰 필터(#9)가 담당 — 여기선 근력 XP 산식만.
 */
object StrengthAxis {

    /** 주당 근력 세션 빈도 보너스(꾸준함 보상). n=이번 주 근력 세션 수(이번 세션 포함). */
    fun weeklyFrequencyBonus(sessionsThisWeek: Int): Double = when {
        sessionsThisWeek <= 0 -> 0.0
        sessionsThisWeek == 1 -> 1.0
        sessionsThisWeek == 2 -> 1.1
        sessionsThisWeek == 3 -> 1.2
        else -> 1.3 // 4회+
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
        // + EPS: 60×1.2 같은 곱이 double에서 71.9999…로 나와 floor가 어긋나는 것 방지
        return (base * weeklyFrequencyBonus(sessionsThisWeek) * hrCorrection + 1e-9).toInt()
    }
}
