package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConditionSleepTest {
    private val delta = 1e-9

    @Test
    fun nullSleep_leaves_condition_unchanged() {
        // 워치 미착용·수면 없음(sync 희소) → 무효과, 활동 기반 유지.
        assertEquals(70.0, ConditionEngine.applySleep(70.0, null), delta)
    }

    @Test
    fun full_sleep_adds_max_bonus() {
        assertEquals(70.0 + ConditionEngine.SLEEP_BONUS, ConditionEngine.applySleep(70.0, 7.0), delta)
        // 목표 초과도 상한(+BONUS)에서 포화
        assertEquals(70.0 + ConditionEngine.SLEEP_BONUS, ConditionEngine.applySleep(70.0, 9.0), delta)
    }

    @Test
    fun bonus_scales_with_sleep_duration() {
        // 목표의 절반(3.5h) → +BONUS/2
        assertEquals(70.0 + ConditionEngine.SLEEP_BONUS / 2.0, ConditionEngine.applySleep(70.0, 3.5), delta)
    }

    @Test
    fun short_sleep_never_penalizes() {
        // 보상 전용: 짧게 자도(2h) 컨디션이 내려가지 않는다(무처벌).
        assertTrue(ConditionEngine.applySleep(70.0, 2.0) >= 70.0)
        assertEquals(70.0, ConditionEngine.applySleep(70.0, 0.0), delta)
    }

    @Test
    fun never_above_max() {
        assertEquals(ConditionEngine.MAX, ConditionEngine.applySleep(98.0, 8.0), delta)
    }
}
