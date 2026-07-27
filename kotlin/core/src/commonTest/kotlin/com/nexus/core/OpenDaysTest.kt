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
    fun `보관 정리 - 최근 N개만 남기고 오래된 것부터 버린다`() {
        // RETENTION_DAYS보다 많은 날짜를 넣어 상한이 실제로 동작하는지 확인
        val many = (0L until OpenDays.RETENTION_DAYS + 5).map { today - it }.toSet()
        val kept = OpenDays.record(many, today)
        assertEquals(OpenDays.RETENTION_DAYS, kept.size)
        assertTrue(today in kept, "가장 최근 날짜가 남아야 한다")
        assertTrue((today - OpenDays.RETENTION_DAYS) !in kept, "가장 오래된 날짜는 버려져야 한다")
    }

    @Test
    fun `시계가 앞으로 크게 튀어도 기존 기록이 지워지지 않는다`() {
        // 날짜 기준으로 자르면 기존 기록이 전부 사라졌다(#286 리뷰) — 개수 기준이라 보존된다
        val existing = setOf(today, today - 1, today - 2)
        val kept = OpenDays.record(existing, todayEpochDay = today + 400)
        assertTrue(existing.all { it in kept }, "시계 점프로 과거 기록이 소실됐다")
    }

    @Test
    fun `창 0 이하는 0 - 비정상 입력 방어`() {
        assertEquals(0, OpenDays.countInWindow(setOf(today), today, window = 0))
    }
}
