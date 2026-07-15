package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XpEngineTest {
    // E3-13 케이스 테이블 하네스의 씨앗 — 치완 스프레드시트와 같은 (입력 → 기대 XP) 형태를 유지한다.
    private data class Case(val type: ActivityType, val minutes: Int, val expected: Int)

    private val cases =
        listOf(
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

    // ── #13 일일 상한(200 knee + 300 cap) 케이스 테이블 (#2 스프레드시트 대조) ──
    private data class CapCase(val raw: Double, val expected: Int)

    private val capCases =
        listOf(
            CapCase(0.0, 0),
            CapCase(100.0, 100),
            CapCase(200.0, 200),
            CapCase(300.0, 250), // 200 + 100×0.5
            CapCase(400.0, 300), // 200 + 200×0.5 = 300 (하드캡 경계)
            CapCase(500.0, 300), // 200 + 300×0.5 = 350 → 300 캡
        )

    @Test
    fun applyDailyCap_matchesCaseTable() {
        capCases.forEach { c ->
            assertEquals(c.expected, XpEngine.applyDailyCap(c.raw), "cap case: $c")
        }
    }

    // (base, 개인, 신뢰, 균형) → 기대 일일 XP
    private data class DailyCase(
        val base: Double,
        val coef: Double,
        val trust: Double,
        val bonus: Double,
        val expected: Int,
    )

    private val dailyCases =
        listOf(
            DailyCase(100.0, 1.0, 1.0, 1.0, 100),
            DailyCase(100.0, 1.5, 1.0, 1.0, 150),
            DailyCase(200.0, 1.0, 1.0, 1.0, 200),
            DailyCase(100.0, 1.0, 0.0, 1.0, 0), // Tier C(신뢰 0) → XP 제외
            DailyCase(100.0, 2.0, 1.0, 1.0, 150), // 개인계수 상한 1.5로 클램프
            DailyCase(100.0, 0.5, 1.0, 1.0, 80), // 개인계수 하한 0.8로 클램프
            DailyCase(200.0, 1.0, 1.0, 1.15, 215), // 균형 보너스 → raw 230 → 200+30×0.5
            DailyCase(300.0, 1.0, 1.0, 1.0, 250), // 상한 체감
        )

    @Test
    fun dailyXp_matchesCaseTable() {
        dailyCases.forEach { c ->
            assertEquals(c.expected, XpEngine.dailyXp(c.base, c.coef, c.trust, c.bonus), "daily case: $c")
        }
    }

    @Test
    fun dailyXp_rejectsNegativeBase() {
        assertFailsWith<IllegalArgumentException> { XpEngine.dailyXp(basePoints = -1.0) }
    }
}
