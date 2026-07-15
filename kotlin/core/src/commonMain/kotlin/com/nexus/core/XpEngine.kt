package com.nexus.core

enum class ActivityType(val pointsPerMinute: Double) {
    WALKING(1.0),
    RUNNING(2.0),
    STRENGTH(1.5),
}

object XpEngine {
    /** 산식 버전 태그 — RewardEvent 원장과 케이스 테이블이 이 값으로 버전링된다 (BACKEND.md §1). */
    const val FORMULA_VERSION = 1

    /** 일일 상한 (MVP §5, #2 확정): 200 초과분 절반 체감 + 300 하드캡. */
    const val DAILY_KNEE = 200.0
    const val DAILY_SOFT_RATE = 0.5
    const val DAILY_HARD_CAP = 300.0

    /** 개인 계수 캡 (자기 기준 성장). 산출은 E3-2(#14 베이스라인), 여기선 클램프만. */
    const val PERSONAL_COEF_MIN = 0.8
    const val PERSONAL_COEF_MAX = 1.5

    /** MVP.md §5 기본 점수: 활동 유형별 분당 포인트 × 시간. */
    fun baseScore(type: ActivityType, minutes: Int): Int {
        require(minutes >= 0) { "minutes must be >= 0" }
        return (type.pointsPerMinute * minutes).toInt()
    }

    /**
     * 하루 획득 XP (#13, MVP §5): `기본 × 개인 × 신뢰 × 균형` → 일일 상한.
     * - [personalCoef]: 개인 계수. E3-2(#14)가 14일 이동평균 대비로 산출, 여기선 [PERSONAL_COEF_MIN]~[MAX] 클램프.
     * - [trustMultiplier]: 신뢰 계수(#9 TrustTier.xpMultiplier — Tier C=0이면 XP 제외).
     * - [balanceBonus]: 균형 보너스(E3-9/S3 주간 정산, 평일 1.0).
     */
    fun dailyXp(
        basePoints: Double,
        personalCoef: Double = 1.0,
        trustMultiplier: Double = 1.0,
        balanceBonus: Double = 1.0,
    ): Int {
        require(basePoints >= 0) { "basePoints must be >= 0" }
        require(trustMultiplier >= 0) { "trustMultiplier must be >= 0" }
        require(balanceBonus >= 0) { "balanceBonus must be >= 0" }
        val coef = personalCoef.coerceIn(PERSONAL_COEF_MIN, PERSONAL_COEF_MAX)
        val raw = basePoints * coef * trustMultiplier * balanceBonus
        return applyDailyCap(raw)
    }

    /** 일일 상한: 200 초과분 절반 체감 후 300 하드캡. */
    fun applyDailyCap(raw: Double): Int {
        val softened = if (raw <= DAILY_KNEE) raw else DAILY_KNEE + (raw - DAILY_KNEE) * DAILY_SOFT_RATE
        return softened.coerceAtMost(DAILY_HARD_CAP).toInt()
    }
}
