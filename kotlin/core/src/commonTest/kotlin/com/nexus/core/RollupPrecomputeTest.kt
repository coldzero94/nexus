package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RollupPrecomputeTest {
    @Test
    fun empty_isLevelOneFloor() {
        val r = RollupPrecompute.compute(emptyList())
        assertEquals(0, r.totalXp)
        assertEquals(1, r.level) // 바닥값
        assertTrue(r.daily.isEmpty())
        assertTrue(r.weekly.isEmpty())
    }

    @Test
    fun oneWeek_appliesSettlement() {
        val week = listOf(30, 0, 30, 0, 30, 0, 0) // 활동 3일
        val r = RollupPrecompute.compute(week)
        assertEquals(7, r.daily.size)
        assertEquals(1, r.weekly.size)
        assertEquals(3, r.weekly[0].activeDays)
        // 정산: 균형 미달(3<4) × 연속성(직전 없음, 3>=0) → 90×1.05
        assertEquals(WeeklySettlement.settle(90, 3, 0), r.weekly[0].settledXp)
        assertEquals(r.weekly[0].settledXp, r.totalXp)
    }

    @Test
    fun twoWeeks_consistencyUsesPrevWeek() {
        val days = listOf(30, 0, 30, 0, 30, 0, 0) + listOf(30, 30, 30, 30, 0, 0, 0)
        val r = RollupPrecompute.compute(days)
        assertEquals(14, r.daily.size)
        assertEquals(2, r.weekly.size)
        assertEquals(4, r.weekly[1].activeDays)
        // 2주차: 직전 주 활동일 3 대비 4(유지+) → 연속성 적용, 균형(4활동·3휴식) 적용
        assertEquals(WeeklySettlement.settle(120, 4, 3), r.weekly[1].settledXp)
        assertEquals(r.weekly[0].settledXp + r.weekly[1].settledXp, r.totalXp)
    }

    @Test
    fun dailyRollup_marksActive() {
        val r = RollupPrecompute.compute(listOf(100, 0, 50))
        assertEquals(listOf(true, false, true), r.daily.map { it.active })
    }
}
