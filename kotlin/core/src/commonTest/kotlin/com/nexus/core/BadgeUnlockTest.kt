package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BadgeUnlockTest {

    // 런타임 자산(app/src/main/assets/character/badges.json)과 동일한 v1 표.
    private val v1Table = """
        {
          "version": "v1",
          "badges": [
            { "id": "first_step", "name": "첫걸음", "description": "d", "when": "activeDaysTotal >= 1" },
            { "id": "week_streak", "name": "꾸준함의 증표", "description": "d", "when": "streakDays >= 7" },
            { "id": "level_10", "name": "성장의 궤도", "description": "d", "when": "level >= 10" },
            { "id": "first_expedition", "name": "탐험가", "description": "d", "when": "expeditionsCompleted >= 1" },
            { "id": "ten_thousand", "name": "만보 주파", "description": "d", "when": "bestDaySteps >= 10000" }
          ]
        }
    """.trimIndent()

    private val table = BadgeTableReader.parse(v1Table)

    @Test
    fun fresh_player_unlocks_nothing() {
        // 기본값(level 1, 모든 카운트 0) — 어떤 조건도 충족 안 함.
        assertEquals(emptySet(), BadgeEvaluator.unlocked(table, BadgeContext()))
    }

    @Test
    fun first_activity_unlocks_first_step() {
        assertEquals(setOf("first_step"), BadgeEvaluator.unlocked(table, BadgeContext(activeDaysTotal = 1)))
    }

    @Test
    fun thresholds_are_inclusive() {
        // >= 경계값에서 정확히 해금.
        assertTrue("week_streak" in BadgeEvaluator.unlocked(table, BadgeContext(streakDays = 7)))
        assertTrue("level_10" in BadgeEvaluator.unlocked(table, BadgeContext(level = 10)))
        assertTrue("ten_thousand" in BadgeEvaluator.unlocked(table, BadgeContext(bestDaySteps = 10_000)))
        // 경계 바로 아래는 미해금.
        assertTrue("week_streak" !in BadgeEvaluator.unlocked(table, BadgeContext(streakDays = 6)))
    }

    @Test
    fun multiple_conditions_unlock_together() {
        val ctx = BadgeContext(
            level = 12,
            activeDaysTotal = 30,
            streakDays = 8,
            expeditionsCompleted = 2,
            bestDaySteps = 12_000,
        )
        assertEquals(
            setOf("first_step", "week_streak", "level_10", "first_expedition", "ten_thousand"),
            BadgeEvaluator.unlocked(table, ctx),
        )
    }

    @Test
    fun newlyUnlocked_excludes_already_earned() {
        val ctx = BadgeContext(activeDaysTotal = 30, streakDays = 8)
        // first_step은 이미 보유 → week_streak만 새로.
        val fresh = BadgeEvaluator.newlyUnlocked(table, ctx, alreadyUnlocked = setOf("first_step"))
        assertEquals(setOf("week_streak"), fresh)
    }

    @Test
    fun newlyUnlocked_is_empty_when_no_progress() {
        val ctx = BadgeContext(activeDaysTotal = 1)
        assertEquals(emptySet(), BadgeEvaluator.newlyUnlocked(table, ctx, alreadyUnlocked = setOf("first_step")))
    }

    @Test
    fun parse_rejects_duplicate_id() {
        assertFailsWith<IllegalArgumentException> {
            BadgeTableReader.parse(
                """{ "version": "t", "badges": [
                     { "id": "a", "name": "n", "description": "d", "when": "level >= 1" },
                     { "id": "a", "name": "m", "description": "d", "when": "streakDays >= 1" } ] }""",
            )
        }
    }

    @Test
    fun parse_rejects_unknown_variable() {
        val ex = assertFailsWith<IllegalArgumentException> {
            BadgeTableReader.parse(
                """{ "version": "t", "badges": [
                     { "id": "a", "name": "n", "description": "d", "when": "levl >= 10" } ] }""",
            )
        }
        assertTrue(ex.message!!.contains("unknown vars"))
    }

    @Test
    fun parse_rejects_empty_badges() {
        assertFailsWith<IllegalArgumentException> {
            BadgeTableReader.parse("""{ "version": "t", "badges": [] }""")
        }
    }
}
