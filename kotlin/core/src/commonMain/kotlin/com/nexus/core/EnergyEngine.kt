package com.nexus.core

/**
 * 에너지 재화 (#67, E5-6) — 운동으로 얻고 원정(#34)에 쓰는 메타게임 재화.
 * 환금성 없음(제품 불변식) — 코스메틱 루프 전용.
 *
 * 획득은 **원장 파생**: 누적 에너지 = 상한 적용 XP ÷ [XP_PER_ENERGY]. 별도 획득 추적이
 * 없어 멱등이 공짜고(같은 세션 중복 지급 불가 = 에너지도 불가), 소모만 단조 카운터로 기록한다.
 * 잔액 = 획득 − 소모 (0 미만 없음). 밸런스: 보통 하루 활동 60~90pt → 6~9 에너지 →
 * 원정([EXPEDITION_COST]=3) 2~3회 = MVP §3 "하루 2~3회 자연 재방문".
 */
object EnergyEngine {
    /** XP → 에너지 환산 (10pt = 1 에너지). */
    const val XP_PER_ENERGY = 10

    /** 원정 1회 비용 — 원정 코어(#34)가 소비. */
    const val EXPEDITION_COST = 3

    /** 누적 획득 에너지 — 입력은 일 상한 적용 누적 XP([LedgerMath.cappedTotalXp]). */
    fun earnedTotal(cappedTotalXp: Int): Int {
        require(cappedTotalXp >= 0) { "cappedTotalXp must be >= 0" }
        return cappedTotalXp / XP_PER_ENERGY
    }

    /** 현재 잔액 — 과소모(원장 취소로 획득이 줄어든 경우)는 0으로 클램프. */
    fun balance(cappedTotalXp: Int, totalSpent: Int): Int {
        require(totalSpent >= 0) { "totalSpent must be >= 0" }
        return (earnedTotal(cappedTotalXp) - totalSpent).coerceAtLeast(0)
    }

    /** [cost]만큼 소모 가능한가. */
    fun canSpend(cappedTotalXp: Int, totalSpent: Int, cost: Int): Boolean {
        require(cost > 0) { "cost must be > 0" }
        return balance(cappedTotalXp, totalSpent) >= cost
    }
}
