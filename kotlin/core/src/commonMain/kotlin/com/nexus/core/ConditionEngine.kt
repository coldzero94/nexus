package com.nexus.core

/**
 * 컨디션 산식 (#27, E4-3): 활동/휴식 리듬 → 0~100 게이지, **소프트 손실**.
 *
 * 제품 불변식(MVP §1 "무처벌 + 소프트 손실"): 캐릭터는 죽거나 퇴화하지 않는다 —
 * 유일한 손실 기제가 이 컨디션이며, 그마저도:
 * - 무활동 하락은 [SOFT_FLOOR]에 **점근**한다(바닥 근처일수록 하락폭이 줄어 절대 뚫지 않음) → "소멸 없음"
 * - 휴식 모드(E4-7) 중에는 하락하지 않는다
 * - 활동 하루면 즉시 회복이 시작된다(하락보다 회복이 빠름 — 용서 장치)
 *
 * XP 산식과 독립(원장·산식 버전 태그와 무관) — 밸런스 상수는 표시 전용 게이지라 자유 튜닝.
 */
object ConditionEngine {
    const val MAX = 100.0
    const val DEFAULT = 70.0

    /** 소프트 손실 바닥 — 아무리 오래 쉬어도 이 아래로 내려가지 않는다(소멸 없음). */
    const val SOFT_FLOOR = 20.0

    /** 활동일 회복량(하락 최대치보다 크게 — 하루 활동이면 이틀 공백을 거의 만회). */
    const val RECOVERY_PER_ACTIVE_DAY = 15.0

    /** 컨디션 최대 기준 무활동 하락폭 — 바닥에 가까울수록 비례 감소(점근). */
    const val IDLE_DECAY_AT_MAX = 8.0

    /** 이 이상의 하루 기본점수(분 환산 ≈ 걷기 10분)면 "활동한 날"로 본다. */
    const val ACTIVE_DAY_THRESHOLD_POINTS = 10.0

    /**
     * 하루 경과 반영. [current]는 어제의 컨디션, [dayBasePoints]는 그날의 신뢰 반영 전
     * 기본점수 합(§5 baseScore — Tier C 포함: 컨디션은 코스메틱이라 수기 기록도 몸을 움직인 날).
     * [restMode]는 사용자가 켠 휴식 모드(E4-7) — 하락만 막고 회복은 그대로.
     * 경계는 포함(≥ 10pt = 활동일) — 분 합산이 부동소수라면 호출자가 정량화 후 넘길 것.
     *
     * 분기 순서가 계약이다: 활동 회복이 **바닥 가드보다 먼저** — 바닥에서도 활동하면
     * 즉시 회복해야 한다(무처벌 루프의 핵심, 테스트로 고정).
     */
    fun nextDay(current: Double, dayBasePoints: Double, restMode: Boolean = false): Double {
        require(dayBasePoints >= 0) { "dayBasePoints must be >= 0" }
        val clamped = current.coerceIn(0.0, MAX)
        return when {
            dayBasePoints >= ACTIVE_DAY_THRESHOLD_POINTS ->
                (clamped + RECOVERY_PER_ACTIVE_DAY).coerceAtMost(MAX)

            restMode -> clamped

            // 바닥 이하(손상값 포함)에선 더 깎지도, 공짜로 올리지도 않는다
            clamped <= SOFT_FLOOR -> clamped

            else -> clamped - IDLE_DECAY_AT_MAX * (clamped - SOFT_FLOOR) / (MAX - SOFT_FLOOR)
        }
    }
}
