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

    /**
     * 휴식일 버프 (#63, E4-10): 어제 쉬고 오늘 움직이면 회복이 이만큼 추가된다 — 고정 보너스라
     * 자체 상한(완료 기준 "상한 있음"). 휴식을 손해가 아니라 리듬으로 만드는 장치.
     * 수면·HRV 기반 정밀 감지는 후속(권한 추가 = 재심사).
     */
    const val REST_DAY_RECOVERY_BONUS = 7.0

    /** 컨디션 최대 기준 무활동 하락폭 — 바닥에 가까울수록 비례 감소(점근). */
    const val IDLE_DECAY_AT_MAX = 8.0

    /** 이 이상의 하루 기본점수(분 환산 ≈ 걷기 10분)면 "활동한 날"로 본다. */
    const val ACTIVE_DAY_THRESHOLD_POINTS = 10.0

    /**
     * 수면 반영 상수 (#180, E4-12) — 표시 게이지라 자유 튜닝. 목표 이상=가산, 부족 이하=감산.
     * 무처벌 원칙상 감산 폭은 소폭으로 두고, 결과는 항상 [SOFT_FLOOR] 위로 클램프한다.
     */
    const val SLEEP_TARGET_HOURS = 7.0
    const val SLEEP_POOR_HOURS = 5.0
    const val SLEEP_BONUS = 8.0
    const val SLEEP_PENALTY = 6.0

    /**
     * 하루 경과 반영. [current]는 어제의 컨디션, [dayBasePoints]는 그날의 신뢰 반영 전
     * 기본점수 합(§5 baseScore — Tier C 포함: 컨디션은 코스메틱이라 수기 기록도 몸을 움직인 날).
     * [restMode]는 사용자가 켠 휴식 모드(E4-7) — 하락만 막고 회복은 그대로.
     * 경계는 포함(≥ 10pt = 활동일) — 분 합산이 부동소수라면 호출자가 정량화 후 넘길 것.
     *
     * 분기 순서가 계약이다: 활동 회복이 **바닥 가드보다 먼저** — 바닥에서도 활동하면
     * 즉시 회복해야 한다(무처벌 루프의 핵심, 테스트로 고정).
     * [restedYesterday]가 참인 활동일엔 [REST_DAY_RECOVERY_BONUS]가 추가된다(#63).
     */
    fun nextDay(
        current: Double,
        dayBasePoints: Double,
        restMode: Boolean = false,
        restedYesterday: Boolean = false,
    ): Double {
        require(dayBasePoints >= 0) { "dayBasePoints must be >= 0" }
        // 손상 저장값 방어: NaN은 coerceIn을 통과하므로 기본값으로 복구, 범위 밖은 클램프.
        // 게이지는 연속값(Double) — 반올림은 표시·영속화 계층에서, 엔진은 하지 않는다.
        val clamped = if (current.isNaN()) DEFAULT else current.coerceIn(0.0, MAX)
        val recovery = RECOVERY_PER_ACTIVE_DAY + if (restedYesterday) REST_DAY_RECOVERY_BONUS else 0.0
        return when {
            dayBasePoints >= ACTIVE_DAY_THRESHOLD_POINTS ->
                (clamped + recovery).coerceAtMost(MAX)

            restMode -> clamped

            // 바닥 이하(손상값 포함)에선 더 깎지도, 공짜로 올리지도 않는다
            clamped <= SOFT_FLOOR -> clamped

            else -> clamped - IDLE_DECAY_AT_MAX * (clamped - SOFT_FLOOR) / (MAX - SOFT_FLOOR)
        }
    }

    /**
     * 최근 일자별 기본점수(과거→오늘 순)를 [DEFAULT]에서 폴드해 오늘의 컨디션을 파생 (#32).
     * 원장(Room) 영속화 전의 표시 전용 계산 — 원장 배선 후 저장값 갱신 방식으로 대체.
     */
    fun fromDailyPoints(dayPoints: List<Double>, restMode: Boolean = false): Double =
        dayPoints.fold(DEFAULT) { acc, points -> nextDay(acc, points, restMode) }

    /**
     * 지난밤 수면 반영 (#180, E4-12) — 활동 기반 컨디션에 소프트 보정. [sleepHours]가 null(데이터
     * 없음·sync 희소)이면 무효과로 현행 유지. 목표([SLEEP_TARGET_HOURS]) 이상=+[SLEEP_BONUS],
     * 부족([SLEEP_POOR_HOURS]) 이하=−[SLEEP_PENALTY], 사이는 선형 보간. 결과는 항상
     * [SOFT_FLOOR]~[MAX] — 수면 부족도 바닥을 뚫지 않는다(불변식 유지).
     */
    fun applySleep(condition: Double, sleepHours: Double?): Double {
        if (sleepHours == null) return condition
        val adjust = when {
            sleepHours >= SLEEP_TARGET_HOURS -> SLEEP_BONUS

            sleepHours <= SLEEP_POOR_HOURS -> -SLEEP_PENALTY

            else -> {
                val t = (sleepHours - SLEEP_POOR_HOURS) / (SLEEP_TARGET_HOURS - SLEEP_POOR_HOURS)
                -SLEEP_PENALTY + t * (SLEEP_BONUS + SLEEP_PENALTY)
            }
        }
        return (condition + adjust).coerceIn(SOFT_FLOOR, MAX)
    }
}
