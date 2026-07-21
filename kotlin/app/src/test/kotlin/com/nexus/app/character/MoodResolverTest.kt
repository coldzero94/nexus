package com.nexus.app.character

import com.nexus.core.ActivityType
import com.nexus.core.MoodEvaluator
import com.nexus.core.MoodRule
import com.nexus.core.MoodTable
import com.nexus.core.SessionInput
import com.nexus.core.TrustTier
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 기분 배선 조립 고정 (#212) — [MoodResolver.buildMoodContext]가 홈 신호를 올바른 [com.nexus.core.MoodContext]로
 * 맵핑하고, 실제 표(mood_triggers.json과 동일 규칙)로 평가 시 5분기가 각각 맞는 기분으로 떨어지는지 검증.
 * 엔진 자체는 core에서 검증되므로 여기선 "홈 신호 → 기분" 배선의 정합만 고정한다.
 */
class MoodResolverTest {

    // mood_triggers.json과 동일한 규칙(우선순위·식·표정) — 배선 검증용 인라인 표
    private val table = MoodTable(
        version = "test",
        rules = listOf(
            MoodRule(
                1,
                "뿌듯",
                "leveledUp || newBadge || weeklyGoalMet || newRecord",
                "proud_sparkle",
                "",
                listOf("뿌듯1"),
            ),
            MoodRule(
                2,
                "신남",
                "todayActiveMin > 0 && (personalCoef >= 1.3 || highIntensity)",
                "jump_hyped",
                "",
                listOf("신남1"),
            ),
            MoodRule(3, "평온", "todayActiveMin > 0", "calm_smile", "", listOf("평온1")),
            MoodRule(4, "휴식중", "todayActiveMin == 0 && (restMode || plannedRest)", "cozy_roll", "", listOf("휴식1")),
            MoodRule(5, "심심", "todayActiveMin == 0 && !restMode", "bored_lookaround", "", listOf("심심1")),
        ),
    )

    private fun moodOf(todayActiveMin: Int, personalCoef: Double, restMode: Boolean, weeklyGoalMet: Boolean): String? =
        MoodEvaluator.evaluate(
            table,
            MoodResolver.buildMoodContext(todayActiveMin, personalCoef, restMode, weeklyGoalMet, condition = 70),
        )?.mood

    @Test
    fun activeHighCoef_isHyped() {
        assertEquals("신남", moodOf(30, personalCoef = 1.4, restMode = false, weeklyGoalMet = false))
    }

    @Test
    fun activeNormal_isCalm() {
        assertEquals("평온", moodOf(20, personalCoef = 1.0, restMode = false, weeklyGoalMet = false))
    }

    @Test
    fun weeklyGoalMet_isProud_evenWhenActive() {
        // 뿌듯은 우선순위 1 — 활동 중이어도 목표 달성이 이긴다
        assertEquals("뿌듯", moodOf(20, personalCoef = 1.0, restMode = false, weeklyGoalMet = true))
    }

    @Test
    fun idleWithRest_isCozy() {
        assertEquals("휴식중", moodOf(0, personalCoef = 1.0, restMode = true, weeklyGoalMet = false))
    }

    @Test
    fun idleNoRest_isBored() {
        assertEquals("심심", moodOf(0, personalCoef = 1.0, restMode = false, weeklyGoalMet = false))
    }

    @Test
    fun weeklyGoalMet_thresholdIsInclusive() {
        assertEquals(true, MoodResolver.weeklyGoalMet(activeDaysThisWeek = 4, goalDays = 4))
        assertEquals(false, MoodResolver.weeklyGoalMet(activeDaysThisWeek = 3, goalDays = 4))
    }

    private fun session(date: LocalDate, type: ActivityType, minutes: Int) =
        SessionInput(type, minutes, TrustTier.B, date.toEpochDay())

    private fun ctx(sessions: List<SessionInput>, today: LocalDate, goalDays: Int) =
        MoodResolver.contextFromSessions(sessions, today, restMode = false, goalDays = goalDays, condition = 70)

    @Test
    fun contextFromSessions_weeklyGoalCountsDistinctDaysThisWeekOnly() {
        val wed = LocalDate.of(2026, 7, 22) // 수요일 — 주 시작(월) = 2026-07-20
        val sessions = listOf(
            session(LocalDate.of(2026, 7, 20), ActivityType.WALKING, 20), // 월
            session(LocalDate.of(2026, 7, 21), ActivityType.WALKING, 20), // 화
            session(wed, ActivityType.WALKING, 20), // 수
            session(wed, ActivityType.RUNNING, 10), // 수 중복 → distinct 3일
            session(LocalDate.of(2026, 7, 17), ActivityType.WALKING, 20), // 지난 금 → 이번주 제외
        )
        assertTrue(ctx(sessions, wed, goalDays = 3).weeklyGoalMet)
        assertFalse(ctx(sessions, wed, goalDays = 4).weeklyGoalMet)
    }

    @Test
    fun contextFromSessions_personalCoefIsTodayBaseOverPriorActiveAvg() {
        val today = LocalDate.of(2026, 7, 22)
        val sessions = listOf(
            session(today, ActivityType.WALKING, 25), // 오늘 base = 25
            session(LocalDate.of(2026, 7, 20), ActivityType.WALKING, 20), // prior base 20
            session(LocalDate.of(2026, 7, 18), ActivityType.WALKING, 20), // prior base 20 (활동일 평균 20)
        )
        assertEquals(1.25, ctx(sessions, today, goalDays = 7).personalCoef, 1e-9) // 25/20 (클램프 미도달)
    }

    @Test
    fun contextFromSessions_personalCoefNeutralWithoutPrior() {
        val today = LocalDate.of(2026, 7, 22)
        val only = listOf(session(today, ActivityType.WALKING, 30))
        assertEquals(1.0, ctx(only, today, goalDays = 7).personalCoef, 1e-9) // 콜드스타트: 비교 대상 없음 → 중립
    }
}
