package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StatMappingTest {

    @Test
    fun walking_allEndurance() {
        assertEquals(mapOf(Stat.ENDURANCE to 50), StatMapping.distribute(ActivityType.WALKING, 50))
    }

    @Test
    fun running_splitEnduranceAgility() {
        assertEquals(
            mapOf(Stat.ENDURANCE to 30, Stat.AGILITY to 30),
            StatMapping.distribute(ActivityType.RUNNING, 60),
        )
    }

    @Test
    fun running_remainderToTopWeight_conservesSum() {
        val d = StatMapping.distribute(ActivityType.RUNNING, 61)
        assertEquals(31, d[Stat.ENDURANCE]) // 잔여 1이 최대 가중치(첫 END)로
        assertEquals(30, d[Stat.AGILITY])
        assertEquals(61, d.values.sum()) // 합 보존
    }

    @Test
    fun strength_allStrength() {
        assertEquals(mapOf(Stat.STRENGTH to 40), StatMapping.distribute(ActivityType.STRENGTH, 40))
    }

    @Test
    fun zeroXp_emptyMap() {
        assertEquals(emptyMap(), StatMapping.distribute(ActivityType.WALKING, 0))
    }

    @Test
    fun lockedStats_areFocusAndAffinity() {
        assertEquals(listOf(Stat.FOCUS, Stat.AFFINITY), StatMapping.lockedStats)
        assertTrue(StatMapping.unlockedStats.contains(Stat.RECOVERY))
        assertTrue(StatMapping.unlockedStats.none { it.locked })
    }

    @Test
    fun rejectsNegativeXp() {
        assertFailsWith<IllegalArgumentException> { StatMapping.distribute(ActivityType.WALKING, -1) }
    }
}
