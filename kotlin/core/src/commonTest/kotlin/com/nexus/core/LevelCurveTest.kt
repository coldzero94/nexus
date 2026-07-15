package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LevelCurveTest {

    // 레벨 → 도달 필요 누적 XP (100 × N^1.5)
    private val xpForLevelCases = listOf(
        0 to 0,
        1 to 100,
        2 to 283, // 100×2^1.5 = 282.84
        4 to 800, // 100×8
        5 to 1118, // 100×5^1.5 = 1118.03
        9 to 2700, // 100×27
    )

    @Test
    fun xpForLevel_matchesCaseTable() {
        xpForLevelCases.forEach { (level, xp) ->
            assertEquals(xp, LevelCurve.xpForLevel(level), "xpForLevel($level)")
        }
    }

    // 누적 XP → 도달 레벨
    private val levelForXpCases = listOf(
        0 to 0,
        99 to 0,
        100 to 1,
        300 to 2,
        800 to 4,
        2700 to 9,
    )

    @Test
    fun levelForXp_matchesCaseTable() {
        levelForXpCases.forEach { (xp, level) ->
            assertEquals(level, LevelCurve.levelForXp(xp), "levelForXp($xp)")
        }
    }

    @Test
    fun levelUp_detectsTransition() {
        assertEquals(LevelUp(0, 1), LevelCurve.levelUp(90, 110))
        assertEquals(LevelUp(3, 4), LevelCurve.levelUp(700, 900))
    }

    @Test
    fun levelUp_nullWhenNoLevelGain() {
        assertNull(LevelCurve.levelUp(100, 200)) // 둘 다 레벨 1
        assertNull(LevelCurve.levelUp(2700, 2700))
    }

    @Test
    fun progressToNextLevel_boundariesAndMidpoint() {
        assertEquals(0.0, LevelCurve.progressToNextLevel(800), 1e-6) // 레벨 4 진입점
        // 레벨 4(800)~레벨 5(1118) 중간 ≈ 959 → 0.5
        assertEquals(0.5, LevelCurve.progressToNextLevel(959), 0.02)
    }

    @Test
    fun rejectsNegatives() {
        assertFailsWith<IllegalArgumentException> { LevelCurve.levelForXp(-1) }
        assertFailsWith<IllegalArgumentException> { LevelCurve.xpForLevel(-1) }
    }
}
