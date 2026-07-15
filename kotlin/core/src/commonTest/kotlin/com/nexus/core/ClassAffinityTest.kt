package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassAffinityTest {
    // (walk, run, strength) → 기대 성향. 40% 규칙 경계 검증.
    private data class Case(val w: Double, val r: Double, val s: Double, val expected: ClassAffinity)

    private val cases =
        listOf(
            Case(100.0, 0.0, 0.0, ClassAffinity.ENDURANCE),
            Case(0.0, 100.0, 0.0, ClassAffinity.AGILITY),
            Case(0.0, 0.0, 100.0, ClassAffinity.STRENGTH),
            Case(60.0, 20.0, 20.0, ClassAffinity.ENDURANCE), // 60% ≥ 40%
            Case(20.0, 60.0, 20.0, ClassAffinity.AGILITY),
            Case(20.0, 20.0, 60.0, ClassAffinity.STRENGTH),
            Case(39.0, 31.0, 30.0, ClassAffinity.BALANCED), // 최대 39% < 40% → 균형
            Case(34.0, 33.0, 33.0, ClassAffinity.BALANCED),
            Case(0.0, 0.0, 0.0, ClassAffinity.BALANCED), // 무활동
        )

    @Test
    fun affinity_matchesCaseTable() {
        cases.forEach { c ->
            assertEquals(c.expected, ClassAffinityCalculator.affinity(c.w, c.r, c.s), "case: $c")
        }
    }

    @Test
    fun rejectsNegative() {
        assertFailsWith<IllegalArgumentException> {
            ClassAffinityCalculator.affinity(-1.0, 0.0, 0.0)
        }
    }
}
