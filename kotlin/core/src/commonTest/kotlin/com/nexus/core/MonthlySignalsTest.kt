package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #206 — 월 신호 집계. 범위 경계와 "상쇄된 날은 활동일 아님"이 핵심. */
class MonthlySignalsTest {
    private val start = 20_000L
    private val end = 20_030L // 31일 달

    @Test
    fun `활동일은 XP가 양수인 날만 센다`() {
        val daily = mapOf(start to 30.0, start + 1 to 0.0, start + 2 to 12.5)
        assertEquals(2, MonthlySignals.activeDays(daily, start, end))
    }

    @Test
    fun `취소로 상쇄돼 0이 된 날은 활동일이 아니다`() {
        // 지급 후 세션이 삭제돼 상쇄된 날 — 배지가 거저 열리면 안 된다
        val daily = mapOf(start to 0.0, start + 1 to 50.0)
        assertEquals(1, MonthlySignals.activeDays(daily, start, end))
    }

    @Test
    fun `범위 밖은 세지 않는다 - 달 경계`() {
        val daily = mapOf(start - 1 to 99.0, start to 10.0, end to 10.0, end + 1 to 99.0)
        assertEquals(2, MonthlySignals.activeDays(daily, start, end))
    }

    @Test
    fun `XP 합은 범위 안만 — 음수 날은 날 단위로 0 처리`() {
        // 화면 XP와 같은 규칙(LedgerMath.cappedTotalXp): 음수 날은 다른 날에서 빼지 않는다
        val daily = mapOf(start - 1 to 100.0, start to 60.0, start + 1 to -20.0, end + 1 to 100.0)
        assertEquals(60, MonthlySignals.totalXp(daily, start, end))
    }

    @Test
    fun `XP 합이 음수면 0으로 클램프`() {
        assertEquals(0, MonthlySignals.totalXp(mapOf(start to -50.0), start, end))
    }

    @Test
    fun `일일 상한을 적용한다 — 원시 합이 아니라 화면 XP와 일치`() {
        // 원장은 세션 단위 무상한 지급을 박제한다. 상한 없이 더하면 배지가 의도의 1/3 노력에 열린다.
        val daily = mapOf(start to 900.0, start + 1 to 900.0, start + 2 to 900.0)
        val expected = daily.values.sumOf { XpEngine.applyDailyCap(it) }
        assertEquals(expected, MonthlySignals.totalXp(daily, start, end))
        assertTrue(MonthlySignals.totalXp(daily, start, end) < 2700, "원시 합(2700)이 그대로 새면 안 된다")
    }

    @Test
    fun `걸음 합은 범위 안만`() {
        val steps = mapOf(start - 1 to 5000L, start to 8000L, start + 1 to 12000L, end + 1 to 9000L)
        assertEquals(20_000, MonthlySignals.totalSteps(steps, start, end))
    }

    @Test
    fun `빈 입력·뒤집힌 경계는 0`() {
        assertEquals(0, MonthlySignals.activeDays(emptyMap(), start, end))
        assertEquals(0, MonthlySignals.totalXp(mapOf(start to 10.0), end, start))
        assertEquals(0, MonthlySignals.totalSteps(mapOf(start to 100L), end, start))
    }
}
