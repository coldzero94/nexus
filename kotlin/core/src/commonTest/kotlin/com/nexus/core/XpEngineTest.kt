package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XpEngineTest {
    // E3-13 케이스 테이블 하네스의 씨앗 — 치완 스프레드시트와 같은 (입력 → 기대 XP) 형태를 유지한다.
    private data class Case(val type: ActivityType, val minutes: Int, val expected: Int)

    private val cases = listOf(
        Case(ActivityType.WALKING, 30, 30),
        Case(ActivityType.RUNNING, 30, 60),
        Case(ActivityType.STRENGTH, 40, 60),
        Case(ActivityType.WALKING, 0, 0),
    )

    @Test
    fun baseScore_matchesCaseTable() {
        cases.forEach { c ->
            assertEquals(c.expected, XpEngine.baseScore(c.type, c.minutes), "case: $c")
        }
    }

    @Test
    fun baseScore_rejectsNegativeMinutes() {
        assertFailsWith<IllegalArgumentException> { XpEngine.baseScore(ActivityType.WALKING, -1) }
    }
}
