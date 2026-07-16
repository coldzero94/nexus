package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {
    private fun days(vararg active: Boolean): List<Boolean> = active.toList()

    @Test
    fun currentStreak_counts_trailing_active_days() {
        assertEquals(3, StreakCalculator.currentStreak(days(false, true, true, true)))
        assertEquals(4, StreakCalculator.currentStreak(days(true, true, true, true)))
    }

    @Test
    fun currentStreak_is_zero_when_last_day_inactive() {
        assertEquals(0, StreakCalculator.currentStreak(days(true, true, false)))
        assertEquals(0, StreakCalculator.currentStreak(days(false)))
    }

    @Test
    fun currentStreak_of_empty_is_zero() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList()))
    }

    @Test
    fun longestStreak_finds_max_run() {
        assertEquals(3, StreakCalculator.longestStreak(days(true, false, true, true, true, false, true)))
        assertEquals(0, StreakCalculator.longestStreak(days(false, false)))
        assertEquals(2, StreakCalculator.longestStreak(days(true, true)))
    }
}

class BadgeSignalsTest {

    @Test
    fun build_derives_level_from_ledger_xp() {
        // 레벨은 LevelCurve.displayLevel로 — 성장 탭과 같은 값.
        val xp = 500
        val ctx = BadgeSignals.build(
            cumulativeXp = xp,
            dailyActive = emptyList(),
            bestDaySteps = 0,
            expeditionsCompleted = 0,
        )
        assertEquals(LevelCurve.displayLevel(xp), ctx.level)
        assertEquals(xp, ctx.cumulativeXp)
    }

    @Test
    fun build_counts_active_days_and_streak() {
        val active = listOf(true, false, true, true, true) // 활동 4일, 끝 3일 연속
        val ctx = BadgeSignals.build(
            cumulativeXp = 0,
            dailyActive = active,
            bestDaySteps = 8_000,
            expeditionsCompleted = 2,
        )
        assertEquals(4, ctx.activeDaysTotal)
        assertEquals(3, ctx.streakDays)
        assertEquals(8_000, ctx.bestDaySteps)
        assertEquals(2, ctx.expeditionsCompleted)
    }

    @Test
    fun build_feeds_badge_evaluator_end_to_end() {
        // 신호 조립 → 평가기까지: 7일 연속이면 week_streak 해금.
        val table = BadgeTableReader.parse(
            """{ "version": "t", "badges": [
                 { "id": "week_streak", "name": "n", "description": "d", "when": "streakDays >= 7" } ] }""",
        )
        val sevenActive = List(7) { true }
        val ctx = BadgeSignals.build(
            cumulativeXp = 0,
            dailyActive = sevenActive,
            bestDaySteps = 0,
            expeditionsCompleted = 0,
        )
        assertEquals(setOf("week_streak"), BadgeEvaluator.unlocked(table, ctx))
    }
}
