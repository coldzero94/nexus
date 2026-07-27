package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #215 — 주간 목표 진척. 주 경계·중복·미래 제외와 무처벌 프레이밍 입력이 핵심. */
class WeeklyGoalTest {
    // 임의의 월요일 기준(값 자체는 의미 없음, 상대 오프셋만 검증)
    private val monday = 20_000L
    private val wednesday = monday + 2

    @Test
    fun `주 구간 내 활동일만 센다`() {
        val days = listOf(monday, monday + 1, wednesday)
        assertEquals(3, WeeklyGoal.activeDaysThisWeek(days, monday, wednesday))
    }

    @Test
    fun `지난 주·미래 날짜는 제외`() {
        val days = listOf(monday - 3, monday - 1, monday, wednesday + 5)
        assertEquals(1, WeeklyGoal.activeDaysThisWeek(days, monday, wednesday))
    }

    @Test
    fun `같은 날 중복은 하루로`() {
        val days = listOf(monday, monday, monday, wednesday, wednesday)
        assertEquals(2, WeeklyGoal.activeDaysThisWeek(days, monday, wednesday))
    }

    @Test
    fun `빈 활동·뒤집힌 경계는 0`() {
        assertEquals(0, WeeklyGoal.activeDaysThisWeek(emptyList(), monday, wednesday))
        assertEquals(0, WeeklyGoal.activeDaysThisWeek(listOf(monday), monday, monday - 1))
    }

    @Test
    fun `세션 활동일 - 수기(Tier C)는 XP가 0이라 활동일에 안 센다`() {
        val sessions = listOf(
            SessionInput(ActivityType.WALKING, minutes = 30, tier = TrustTier.A, epochDay = monday),
            // 수기 입력 — XP 0이라 기세·오늘 XP와 어긋나지 않게 제외
            SessionInput(ActivityType.RUNNING, minutes = 40, tier = TrustTier.C, epochDay = monday + 1),
        )
        assertEquals(1, WeeklyGoal.activeDaysFromSessions(sessions, monday, wednesday))
    }

    @Test
    fun `세션 활동일 - 종목 미상은 제외, 같은 날 중복은 하루`() {
        val sessions = listOf(
            SessionInput(null, minutes = 20, tier = TrustTier.A, epochDay = monday),
            SessionInput(ActivityType.WALKING, minutes = 20, tier = TrustTier.B, epochDay = wednesday),
            SessionInput(ActivityType.STRENGTH, minutes = 30, tier = TrustTier.A, epochDay = wednesday),
        )
        assertEquals(1, WeeklyGoal.activeDaysFromSessions(sessions, monday, wednesday))
    }

    @Test
    fun `달성 판정 - 목표 이상이면 달성, 비정상 목표는 미달성`() {
        assertTrue(WeeklyGoal.isMet(activeDays = 4, goalDays = 4))
        assertTrue(WeeklyGoal.isMet(activeDays = 5, goalDays = 4))
        assertFalse(WeeklyGoal.isMet(activeDays = 3, goalDays = 4))
        assertFalse(WeeklyGoal.isMet(activeDays = 3, goalDays = 0))
    }

    @Test
    fun `남은 일수 - 달성 시 0으로 클램프`() {
        assertEquals(2, WeeklyGoal.remainingDays(activeDays = 2, goalDays = 4))
        assertEquals(0, WeeklyGoal.remainingDays(activeDays = 4, goalDays = 4))
        assertEquals(0, WeeklyGoal.remainingDays(activeDays = 6, goalDays = 4))
    }

    @Test
    fun `주 남은 날 - 월요일 7일, 일요일 1일`() {
        assertEquals(7, WeeklyGoal.daysLeftInWeek(monday, monday))
        assertEquals(5, WeeklyGoal.daysLeftInWeek(monday, wednesday))
        assertEquals(1, WeeklyGoal.daysLeftInWeek(monday, monday + 6))
    }
}
