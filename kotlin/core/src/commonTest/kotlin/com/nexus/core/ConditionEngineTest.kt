package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConditionEngineTest {

    @Test
    fun idleDay_decaysSoftly_fromMax() {
        // 100에서 무활동: 하락폭 최대 8 → 92
        assertEquals(92.0, ConditionEngine.nextDay(100.0, dayBasePoints = 0.0), 1e-9)
    }

    @Test
    fun idleDecay_shrinksNearFloor() {
        // 30에서 무활동: 8 × (30-20)/(100-20) = 1 → 29 (완만)
        assertEquals(29.0, ConditionEngine.nextDay(30.0, dayBasePoints = 0.0), 1e-9)
    }

    @Test
    fun atSoftFloor_neverDropsFurther() {
        // 소멸 없음: 바닥(20)에서는 무활동이어도 그대로
        assertEquals(20.0, ConditionEngine.nextDay(20.0, dayBasePoints = 0.0), 1e-9)
    }

    @Test
    fun longIdleStreak_asymptotesAboveFloor_neverBelow() {
        // 무활동 100일: 단조 하락하되 바닥을 절대 뚫지 않는다 (소프트 손실 불변식)
        var condition = 100.0
        repeat(100) {
            val next = ConditionEngine.nextDay(condition, dayBasePoints = 0.0)
            assertTrue(next < condition, "strictly decreasing decay") // 하락 0 회귀 방지
            assertTrue(next >= ConditionEngine.SOFT_FLOOR, "never below floor: $next")
            condition = next
        }
        // 점근 수렴: 20 + 80×0.9^100 ≈ 20.002 — 바닥에 붙어야 한다 (심층 하락 무력화 회귀 방지)
        assertEquals(ConditionEngine.SOFT_FLOOR, condition, 0.01)
    }

    @Test
    fun activeDay_recovers_fasterThanDecay() {
        // 활동일 회복(+15) > 최대 하락(8) — 하루 활동이면 이틀 공백 만회 (용서 장치)
        assertEquals(65.0, ConditionEngine.nextDay(50.0, dayBasePoints = 30.0), 1e-9)
        assertTrue(ConditionEngine.RECOVERY_PER_ACTIVE_DAY > ConditionEngine.IDLE_DECAY_AT_MAX)
    }

    @Test
    fun recovery_capsAtMax() {
        assertEquals(100.0, ConditionEngine.nextDay(95.0, dayBasePoints = 30.0), 1e-9)
    }

    @Test
    fun restMode_blocksDecay_butNotRecovery() {
        // 휴식 모드(E4-7): 무활동이어도 하락 없음, 활동하면 회복은 그대로
        assertEquals(80.0, ConditionEngine.nextDay(80.0, dayBasePoints = 0.0, restMode = true), 1e-9)
        assertEquals(95.0, ConditionEngine.nextDay(80.0, dayBasePoints = 30.0, restMode = true), 1e-9)
    }

    @Test
    fun belowThreshold_countsAsIdle() {
        // 걷기 5분(5pt) < 활동 문턱(10pt) → 무활동 취급
        assertEquals(92.0, ConditionEngine.nextDay(100.0, dayBasePoints = 5.0), 1e-9)
    }

    @Test
    fun activeDay_recoversFromFloor() {
        // 바닥에서도 활동하면 즉시 회복 — 무처벌 루프의 핵심 (분기 순서 회귀 방지)
        assertEquals(35.0, ConditionEngine.nextDay(20.0, dayBasePoints = 30.0), 1e-9)
        assertEquals(25.0, ConditionEngine.nextDay(10.0, dayBasePoints = 30.0), 1e-9) // 바닥 아래 손상값에서도
    }

    @Test
    fun exactlyAtThreshold_countsAsActive() {
        // 걷기 딱 10분(10pt) = 활동일 (경계 포함 ≥)
        assertEquals(65.0, ConditionEngine.nextDay(50.0, dayBasePoints = 10.0), 1e-9)
    }

    @Test
    fun belowFloor_idleDay_staysPut() {
        // 바닥 아래 손상값: 무활동이어도 그대로 — 공짜 회복도, 위로 스냅도 없음
        // (가드 제거 시 하락식이 음수가 되어 +1 "회복"하는 회귀를 잡는다)
        assertEquals(10.0, ConditionEngine.nextDay(10.0, dayBasePoints = 0.0), 1e-9)
    }

    @Test
    fun nearFloor_singleStepDecay() {
        // 21 → 20.9: 하락 8×(21-20)/80 = 0.1 — 선형 계수의 소단 끝 고정
        assertEquals(20.9, ConditionEngine.nextDay(21.0, dayBasePoints = 0.0), 1e-9)
    }

    @Test
    fun nanInput_recoversToDefault() {
        // NaN은 coerceIn을 통과해 게이지를 얼린다 — 기본값 복구 후 정상 계산 (손상 저장값 방어)
        assertEquals(ConditionEngine.DEFAULT + 15.0, ConditionEngine.nextDay(Double.NaN, dayBasePoints = 30.0), 1e-9)
    }

    @Test
    fun negativePoints_rejected() {
        assertFailsWith<IllegalArgumentException> {
            ConditionEngine.nextDay(70.0, dayBasePoints = -1.0)
        }
    }

    @Test
    fun outOfRangeInput_isClamped() {
        // 방어: 손상된 저장값(범위 밖)도 0~100으로 클램프 후 계산
        assertEquals(92.0, ConditionEngine.nextDay(150.0, dayBasePoints = 0.0), 1e-9)
        assertEquals(15.0, ConditionEngine.nextDay(-10.0, dayBasePoints = 30.0), 1e-9)
    }

    @Test
    fun fromDailyPoints_foldsFromDefault() {
        // 기본 70 → 활동일 +15(85) → 무활동 -8×(85-20)/80=6.5(78.5)
        assertEquals(78.5, ConditionEngine.fromDailyPoints(listOf(30.0, 0.0)), 1e-9)
        assertEquals(ConditionEngine.DEFAULT, ConditionEngine.fromDailyPoints(emptyList()), 1e-9)
    }

    @Test
    fun restDayBuff_boostsRecovery_afterIdleDay() {
        // 어제 휴식 → 오늘 활동: 회복 15 + 버프 7 = 22 (#63) — 버프는 고정 보너스(자체 상한)
        assertEquals(72.0, ConditionEngine.nextDay(50.0, dayBasePoints = 30.0, restedYesterday = true), 1e-9)
        // 연속 활동일엔 버프 없음
        assertEquals(65.0, ConditionEngine.nextDay(50.0, dayBasePoints = 30.0, restedYesterday = false), 1e-9)
        // 버프가 있어도 상한 100을 넘지 않는다
        assertEquals(100.0, ConditionEngine.nextDay(95.0, dayBasePoints = 30.0, restedYesterday = true), 1e-9)
    }

    @Test
    fun restDayBuff_notAppliedOnIdleDay() {
        // 어제 쉬고 오늘도 쉼 — 버프는 활동일에만, 무활동 하락은 그대로
        assertEquals(92.0, ConditionEngine.nextDay(100.0, dayBasePoints = 0.0, restedYesterday = true), 1e-9)
    }
}
