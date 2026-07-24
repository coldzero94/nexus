package com.nexus.core

/**
 * 컨디션 게이지 표시 수학 (#257, E16-7) — 순수 함수. 소프트 바닥([ConditionEngine.SOFT_FLOOR])~
 * [ConditionEngine.MAX] 구간을 채움 비율([0,1])로 매핑하고 3존으로 분류한다.
 *
 * "불퇴행"의 시각 증거: 게이지의 좌측 끝은 0이 아니라 **바닥값**이라, 아무리 쉬어도 채움이 0으로
 * 떨어지지 않는다(소멸 없음, MVP §1). 색만으로 상태를 전달하지 않도록 존을 이산 라벨로도 제공한다.
 */
object ConditionGauge {
    /** 컨디션 존 — 무처벌 원칙상 최저도 '회복중'(비난 없음). 색+라벨 병기용 이산 분류. */
    enum class Zone { Recovering, Stable, Good }

    /** 안정 존 시작 — 이 아래는 회복중. */
    const val STABLE_MIN = 40.0

    /** 좋음 존 시작 — 경계값은 상위 존에 포함. */
    const val GOOD_MIN = 70.0

    /**
     * 게이지 채움 비율 — 바닥~[max] 구간 내 상대 위치([0,1]). 바닥 이하 입력도 0 밑으로 클램프되지
     * 않고(불퇴행), [max] 초과도 1로 클램프. [floor] >= [max]인 비정상 입력은 0.
     */
    fun fillRatio(
        condition: Double,
        floor: Double = ConditionEngine.SOFT_FLOOR,
        max: Double = ConditionEngine.MAX,
    ): Double {
        val span = max - floor
        if (span <= 0.0) return 0.0
        return ((condition - floor) / span).coerceIn(0.0, 1.0)
    }

    /** 존 분류 — [STABLE_MIN)·[GOOD_MIN) 경계. 경계값은 상위 존. */
    fun zoneOf(condition: Double): Zone = when {
        condition >= GOOD_MIN -> Zone.Good
        condition >= STABLE_MIN -> Zone.Stable
        else -> Zone.Recovering
    }
}
