package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReturnWelcomeTest {

    @Test
    fun gapBoundaries_threeDaysIsWelcome() {
        // 공백 2일 = 일상, 정확히 3일부터 복귀 환영 (경계 포함)
        assertFalse(ReturnWelcomePolicy.shouldWelcome(lastOpenEpochDay = 100, todayEpochDay = 102))
        assertTrue(ReturnWelcomePolicy.shouldWelcome(lastOpenEpochDay = 100, todayEpochDay = 103))
        assertTrue(ReturnWelcomePolicy.shouldWelcome(lastOpenEpochDay = 100, todayEpochDay = 130))
    }

    @Test
    fun firstLaunch_isNotAReturn() {
        assertFalse(ReturnWelcomePolicy.shouldWelcome(lastOpenEpochDay = 0, todayEpochDay = 100))
        assertEquals(0, ReturnWelcomePolicy.gapDays(0, 100))
    }

    @Test
    fun clockRollback_isNotAReturn() {
        assertFalse(ReturnWelcomePolicy.shouldWelcome(lastOpenEpochDay = 100, todayEpochDay = 98))
        assertEquals(0, ReturnWelcomePolicy.gapDays(100, 98))
    }

    @Test
    fun gapDays_forDisplay() {
        assertEquals(5, ReturnWelcomePolicy.gapDays(100, 105))
    }
}
