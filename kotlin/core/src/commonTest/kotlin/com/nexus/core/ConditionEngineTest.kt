package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
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
            assertTrue(next <= condition, "monotonic decay")
            assertTrue(next >= ConditionEngine.SOFT_FLOOR, "never below floor: $next")
            condition = next
        }
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
    fun outOfRangeInput_isClamped() {
        // 방어: 손상된 저장값(범위 밖)도 0~100으로 클램프 후 계산
        assertEquals(92.0, ConditionEngine.nextDay(150.0, dayBasePoints = 0.0), 1e-9)
        assertEquals(15.0, ConditionEngine.nextDay(-10.0, dayBasePoints = 30.0), 1e-9)
    }
}
