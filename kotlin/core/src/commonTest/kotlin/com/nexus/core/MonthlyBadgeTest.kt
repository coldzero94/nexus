package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MonthlyBadgeTest {

    // 런타임 자산(app/src/main/assets/character/monthly_badges.json)과 동일한 v1 표.
    private val v1Table = """
        {
          "version": "v1",
          "badges": [
            { "id": "jul_2026_passion", "name": "7월의 열정", "description": "d",
              "period": "2026-07", "when": "monthActiveDays >= 15" },
            { "id": "aug_2026_steps", "name": "8월의 발걸음", "description": "d",
              "period": "2026-08", "when": "monthSteps >= 200000" }
          ]
        }
    """.trimIndent()

    private val table = MonthlyBadgeTableReader.parse(v1Table)

    @Test
    fun activeBadges_filters_by_period() {
        assertEquals(listOf("jul_2026_passion"), MonthlyBadgeCalendar.activeBadges(table, "2026-07").map { it.id })
        assertEquals(listOf("aug_2026_steps"), MonthlyBadgeCalendar.activeBadges(table, "2026-08").map { it.id })
        assertTrue(MonthlyBadgeCalendar.activeBadges(table, "2026-09").isEmpty())
    }

    @Test
    fun unlocks_only_within_its_month() {
        // 7월 조건을 충족해도 8월엔 7월 배지가 활성이 아니라 미해금(캘린더).
        val julyMet = MonthlyBadgeContext(monthActiveDays = 20)
        assertEquals(setOf("jul_2026_passion"), MonthlyBadgeCalendar.unlocked(table, "2026-07", julyMet))
        assertEquals(emptySet(), MonthlyBadgeCalendar.unlocked(table, "2026-08", julyMet))
    }

    @Test
    fun threshold_boundary_is_inclusive() {
        val met = MonthlyBadgeCalendar.unlocked(table, "2026-07", MonthlyBadgeContext(monthActiveDays = 15))
        assertEquals(setOf("jul_2026_passion"), met)
        val below = MonthlyBadgeCalendar.unlocked(table, "2026-07", MonthlyBadgeContext(monthActiveDays = 14))
        assertEquals(emptySet(), below)
    }

    @Test
    fun newlyUnlocked_excludes_already_earned() {
        val ctx = MonthlyBadgeContext(monthSteps = 250_000)
        val first = MonthlyBadgeCalendar.newlyUnlocked(table, "2026-08", ctx, alreadyEarned = emptySet())
        assertEquals(setOf("aug_2026_steps"), first)
        val second = MonthlyBadgeCalendar.newlyUnlocked(table, "2026-08", ctx, alreadyEarned = setOf("aug_2026_steps"))
        assertEquals(emptySet(), second)
    }

    @Test
    fun parse_rejects_bad_period_format() {
        assertFailsWith<IllegalArgumentException> {
            MonthlyBadgeTableReader.parse(
                """{ "version": "t", "badges": [
                     { "id": "a", "name": "n", "description": "d", "period": "2026-13", "when": "monthXp >= 1" } ] }""",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MonthlyBadgeTableReader.parse(
                """{ "version": "t", "badges": [
                     { "id": "a", "name": "n", "description": "d", "period": "2026-7", "when": "monthXp >= 1" } ] }""",
            )
        }
    }

    @Test
    fun parse_rejects_unknown_variable() {
        val ex = assertFailsWith<IllegalArgumentException> {
            MonthlyBadgeTableReader.parse(
                """{ "version": "t", "badges": [
                     { "id": "a", "name": "n", "description": "d",
                       "period": "2026-07", "when": "monthStep >= 1" } ] }""",
            )
        }
        assertTrue(ex.message!!.contains("unknown vars"))
    }

    @Test
    fun parse_rejects_duplicate_id() {
        assertFailsWith<IllegalArgumentException> {
            MonthlyBadgeTableReader.parse(
                """{ "version": "t", "badges": [
                     { "id": "a", "name": "n", "description": "d", "period": "2026-07", "when": "monthXp >= 1" },
                     { "id": "a", "name": "m", "description": "d", "period": "2026-08", "when": "monthXp >= 2" } ] }""",
            )
        }
    }
}
