package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConditionSleepTest {
    private val delta = 1e-9

    @Test
    fun nullSleep_leaves_condition_unchanged() {
        // 수면 데이터 없음(sync 희소) → 무효과, 현행 활동 기반 유지.
        assertEquals(70.0, ConditionEngine.applySleep(70.0, null), delta)
    }

    @Test
    fun sufficient_sleep_adds_bonus() {
        assertEquals(70.0 + ConditionEngine.SLEEP_BONUS, ConditionEngine.applySleep(70.0, 7.5), delta)
        // 목표 경계 포함(>=)
        assertEquals(70.0 + ConditionEngine.SLEEP_BONUS, ConditionEngine.applySleep(70.0, 7.0), delta)
    }

    @Test
    fun poor_sleep_subtracts_penalty() {
        assertEquals(70.0 - ConditionEngine.SLEEP_PENALTY, ConditionEngine.applySleep(70.0, 4.0), delta)
        // 부족 경계 포함(<=)
        assertEquals(70.0 - ConditionEngine.SLEEP_PENALTY, ConditionEngine.applySleep(70.0, 5.0), delta)
    }

    @Test
    fun between_interpolates_linearly() {
        // 6h = 5~7h 정중앙 → 보정 = (-PENALTY + BONUS)/2
        val mid = (-ConditionEngine.SLEEP_PENALTY + ConditionEngine.SLEEP_BONUS) / 2.0
        assertEquals(70.0 + mid, ConditionEngine.applySleep(70.0, 6.0), delta)
    }

    @Test
    fun never_below_floor_even_with_poor_sleep() {
        // 무처벌 불변식: 수면 부족도 바닥을 뚫지 않는다.
        assertEquals(ConditionEngine.SOFT_FLOOR, ConditionEngine.applySleep(ConditionEngine.SOFT_FLOOR, 3.0), delta)
        assertTrue(ConditionEngine.applySleep(22.0, 3.0) >= ConditionEngine.SOFT_FLOOR)
    }

    @Test
    fun never_above_max_with_bonus() {
        assertEquals(ConditionEngine.MAX, ConditionEngine.applySleep(98.0, 8.0), delta)
    }
}
