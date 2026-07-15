package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WeeklySettlementTest {
    // (주간XP, 이번주 활동일, 직전주 활동일) → 기대 정산 XP
    private data class Case(val base: Int, val active: Int, val prev: Int, val expected: Int)

    private val cases =
        listOf(
            // 균형(활동5·휴식2) 1.15 × 연속성(5≥5) 1.05 = 1.2075
            Case(1000, 5, 5, 1207),
            // 균형 미달(활동3) 1.0 × 연속성(3≥3) 1.05
            Case(1000, 3, 3, 1050),
            // 균형(활동4·휴식3) 1.15 × 연속성 미달(4<5) 1.0
            Case(1000, 4, 5, 1150),
            // 활동7·휴식0 → 균형 없음(휴식 부족) × 연속성(7≥0) 1.05
            Case(1000, 7, 0, 1050),
            // 무활동
            Case(0, 0, 0, 0),
        )

    @Test
    fun settle_matchesCaseTable() {
        cases.forEach { c ->
            assertEquals(c.expected, WeeklySettlement.settle(c.base, c.active, c.prev), "case: $c")
        }
    }

    @Test
    fun rejectsNegativeBase() {
        assertFailsWith<IllegalArgumentException> { WeeklySettlement.settle(-1, 3, 3) }
    }
}
