package com.nexus.app.home

import com.nexus.core.ActivityType
import com.nexus.core.SessionInput
import com.nexus.core.TrustTier
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #215 — 주 경계(월요일)·활동일 필터가 만나는 앱 레이어 이음새. 순수 산술은 core WeeklyGoalTest가
 * 덮고, 여기선 "일요일에도 이번 주 월요일부터 센다"와 "수기(Tier C)는 안 센다"를 고정한다.
 */
class WeeklyProgressResolverTest {
    private val monday = LocalDate.of(2026, 7, 27) // 월요일
    private val sunday = monday.plusDays(6)

    private fun session(date: LocalDate, tier: TrustTier = TrustTier.A, type: ActivityType? = ActivityType.WALKING) =
        SessionInput(type = type, minutes = 30, tier = tier, epochDay = date.toEpochDay())

    @Test
    fun `일요일에도 이번 주 월요일부터 센다 - 주 경계 밀림 없음`() {
        val sessions = listOf(session(monday), session(monday.plusDays(3)), session(sunday))
        val progress = resolveWeeklyProgress(sessions, today = sunday, goalDays = 4)
        assertEquals(3, progress.activeDays)
        assertEquals(4, progress.goalDays)
        assertEquals(1, progress.daysLeft) // 일요일 = 이번 주 마지막 날
    }

    @Test
    fun `지난 주 세션은 이번 주에 안 센다 - 주 경계 리셋`() {
        val sessions = listOf(session(monday.minusDays(1)), session(monday.minusDays(3)), session(monday))
        val progress = resolveWeeklyProgress(sessions, today = monday, goalDays = 4)
        assertEquals(1, progress.activeDays)
        assertEquals(7, progress.daysLeft) // 월요일 = 이번 주 7일 남음
    }

    @Test
    fun `수기(Tier C)는 XP가 0이라 활동일에 안 센다`() {
        val sessions = listOf(
            session(monday, tier = TrustTier.A),
            session(monday.plusDays(1), tier = TrustTier.C),
        )
        assertEquals(1, resolveWeeklyProgress(sessions, today = monday.plusDays(2), goalDays = 4).activeDays)
    }

    @Test
    fun `종목 미상 세션은 활동일에 안 센다`() {
        val sessions = listOf(session(monday, type = null), session(monday.plusDays(1)))
        assertEquals(1, resolveWeeklyProgress(sessions, today = monday.plusDays(2), goalDays = 4).activeDays)
    }

    @Test
    fun `설정 목표가 분모로 그대로 반영된다`() {
        val sessions = listOf(session(monday))
        assertEquals(2, resolveWeeklyProgress(sessions, today = monday, goalDays = 2).goalDays)
        assertEquals(6, resolveWeeklyProgress(sessions, today = monday, goalDays = 6).goalDays)
    }
}
