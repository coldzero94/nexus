package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #286 — 오픈 날짜 집계. 창 경계·중복·보관 정리가 핵심(게이트 판정값이라 정확해야 한다). */
class OpenDaysTest {
    private val today = 20_000L

    @Test
    fun `빈 상태는 0`() {
        assertEquals(0, OpenDays.countInWindow(emptySet(), today))
    }

    @Test
    fun `같은 날 여러 번은 1일 - 집합이라 중복 없음`() {
        val days = OpenDays.record(OpenDays.record(setOf(), today), today)
        assertEquals(1, OpenDays.countInWindow(days, today))
    }

    @Test
    fun `창 경계 - 6일 전은 포함, 7일 전은 제외`() {
        val days = setOf(today, today - 6, today - 7)
        assertEquals(2, OpenDays.countInWindow(days, today))
    }

    @Test
    fun `게이트 문턱 - 서로 다른 3일이면 3`() {
        val days = setOf(today, today - 2, today - 5)
        assertEquals(3, OpenDays.countInWindow(days, today))
    }

    @Test
    fun `미래 날짜는 창에서 제외 - 기기 시계 변경 방어`() {
        val days = setOf(today, today + 3)
        assertEquals(1, OpenDays.countInWindow(days, today))
    }

    @Test
    fun `보관 정리 - 21일 밖은 버린다`() {
        val old = today - OpenDays.RETENTION_DAYS
        val kept = OpenDays.record(setOf(old, today - 5), today)
        assertTrue(old !in kept, "보관 창 밖 날짜가 남았다")
        assertTrue(today in kept && (today - 5) in kept)
    }

    @Test
    fun `보관 경계 - 20일 전은 유지된다`() {
        val edge = today - (OpenDays.RETENTION_DAYS - 1)
        assertTrue(edge in OpenDays.record(setOf(edge), today))
    }

    @Test
    fun `창 0 이하는 0 - 비정상 입력 방어`() {
        assertEquals(0, OpenDays.countInWindow(setOf(today), today, window = 0))
    }
}
