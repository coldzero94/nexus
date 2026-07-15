package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrowthCalculatorTest {

    @Test
    fun emptySessions_yieldLevelOneBalanced() {
        val g = GrowthCalculator.compute(emptyList())
        assertEquals(0, g.totalXp)
        assertEquals(1, g.level)
        assertEquals(ClassAffinity.BALANCED, g.affinity)
        assertTrue(g.axisShares.isEmpty())
        assertTrue(g.stats.isEmpty())
    }

    @Test
    fun runningDominant_yieldsAgilityAffinity_andSplitsStats() {
        // 러닝 60분(Tier A) = base 120 → 지구력/민첩 50:50
        val g = GrowthCalculator.compute(
            listOf(SessionInput(ActivityType.RUNNING, minutes = 60, tier = TrustTier.A)),
        )
        assertEquals(120, g.totalXp)
        assertEquals(ClassAffinity.AGILITY, g.affinity)
        assertEquals(60, g.stats[Stat.ENDURANCE])
        assertEquals(60, g.stats[Stat.AGILITY])
    }

    @Test
    fun tierC_countsForAffinity_butNotXp() {
        val g = GrowthCalculator.compute(
            listOf(
                SessionInput(ActivityType.STRENGTH, minutes = 40, tier = TrustTier.C),
                SessionInput(ActivityType.WALKING, minutes = 10, tier = TrustTier.A),
            ),
        )
        // XP는 걷기 10분(Tier A)만
        assertEquals(10, g.totalXp)
        // 성향 비중에는 근력 60pt vs 걷기 10pt → 힘형
        assertEquals(ClassAffinity.STRENGTH, g.affinity)
    }

    @Test
    fun unknownType_ignoredEntirely() {
        val g = GrowthCalculator.compute(
            listOf(SessionInput(type = null, minutes = 60, tier = TrustTier.A)),
        )
        assertEquals(0, g.totalXp)
        assertTrue(g.axisShares.isEmpty())
    }
}
