package com.nexus.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 잔여 표기 순수 함수 고정 (#72 리뷰) — 마지막 1시간 "약 0시간 남음" 회귀 방지 + 반올림 계약. */
class WidgetStatusTest {

    @Test
    fun lastHourIsSoonBranch() {
        assertNull(remainingDisplayHours(0L))
        assertNull(remainingDisplayHours(59 * 60_000L))
        assertNull(remainingDisplayHours(3_599_999L))
    }

    @Test
    fun roundsToNearestHour() {
        assertEquals(1L, remainingDisplayHours(3_600_000L)) // 정확히 1시간
        assertEquals(1L, remainingDisplayHours(89 * 60_000L)) // 1시간 29분 → 1
        assertEquals(2L, remainingDisplayHours(90 * 60_000L)) // 1시간 30분 → 2
        assertEquals(7L, remainingDisplayHours(6 * 3_600_000L + 59 * 60_000L)) // 6시간 59분 → 7
        assertEquals(8L, remainingDisplayHours(8 * 3_600_000L)) // 출발 직후 → 8
    }
}
