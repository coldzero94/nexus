package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrengthAxisTest {

    private val freqCases = listOf(
        0 to 0.0,
        1 to 1.0,
        2 to 1.1,
        3 to 1.2,
        4 to 1.3,
        10 to 1.3,
    )

    @Test
    fun weeklyFrequencyBonus_matchesCaseTable() {
        freqCases.forEach { (n, bonus) ->
            assertEquals(bonus, StrengthAxis.weeklyFrequencyBonus(n), 1e-9, "freq($n)")
        }
    }

    // (분, 주당세션, 심박보정) → 기대 힘 XP. STRENGTH base = 1.5×분.
    private data class Case(val min: Int, val sessions: Int, val hr: Double, val expected: Int)

    private val cases = listOf(
        Case(40, 1, 1.0, 60), // 1.5×40=60 ×1.0×1.0
        Case(40, 3, 1.0, 72), // 60 ×1.2
        Case(40, 3, 1.2, 86), // 60 ×1.2×1.2 = 86.4 → 86
        Case(40, 4, 1.0, 78), // 60 ×1.3
        Case(0, 3, 1.0, 0),
    )

    @Test
    fun strengthXp_matchesCaseTable() {
        cases.forEach { c ->
            assertEquals(c.expected, StrengthAxis.strengthXp(c.min, c.sessions, c.hr), "case: $c")
        }
    }

    @Test
    fun strengthXp_rejectsNegatives() {
        assertFailsWith<IllegalArgumentException> { StrengthAxis.strengthXp(-1, 1) }
        assertFailsWith<IllegalArgumentException> { StrengthAxis.strengthXp(10, 1, hrCorrection = -0.1) }
    }
}
