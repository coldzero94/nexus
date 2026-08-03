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
 * 맵핑하는지. 엔진 자체는 core에서 검증되므로 여기선 "홈 신호 → 기분" 배선의 정합만 고정한다.
 *
 * 표는 **인라인 축약본**이다 — 실제 `mood_triggers.json`과 같지 않다(운동 종류별 규칙 p3~5가 없다).
 * 실제 표를 태우는 검증은 [ActivityReactionTest]가 한다. 여기서 축약본을 쓰는 이유는 이 파일의
 * 명제가 "표가 옳은가"가 아니라 "홈 신호가 컨텍스트로 제대로 옮겨지는가"이기 때문이다.
 */
class MoodResolverTest {

    // 배선 검증용 인라인 축약표 — 종류별 규칙은 일부러 뺐다(위 KDoc 참고)
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
            MoodResolver.buildMoodContext(
                // 종류별 분이 곧 총 활동 분이다 — 따로 받으면 프로덕션에서 못 만드는 조합이 생긴다
                minutesByType = if (todayActiveMin > 0) mapOf(ActivityType.WALKING to todayActiveMin) else emptyMap(),
                personalCoef = personalCoef,
                restMode = restMode,
                weeklyGoalMet = weeklyGoalMet,
                condition = 70,
            ),
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

    /**
     * 축하는 **넘긴 날 하루**다. 주간 활동일 수는 주 안에서 줄지 않으므로 `>= goalDays`로 두면
     * 달성한 뒤 남은 요일 내내 뿌듯(p1)이 다른 기분을 전부 먹는다 — 매일 운동하는 사용자일수록
     * 반응 다양성을 못 보게 된다(#114 리뷰).
     */
    @Test
    fun weeklyGoalMet_firesOnlyOnTheDayItIsCrossed() {
        assertEquals(true, MoodResolver.weeklyGoalMet(activeDaysThisWeek = 4, goalDays = 4, activeToday = true))
        assertEquals(false, MoodResolver.weeklyGoalMet(activeDaysThisWeek = 3, goalDays = 4, activeToday = true))
        // 이미 넘긴 주의 다음 활동일 — 다시 축하하지 않는다
        assertEquals(false, MoodResolver.weeklyGoalMet(activeDaysThisWeek = 5, goalDays = 4, activeToday = true))
        // 넘긴 주의 쉬는 날 — 활동이 없으면 축하 대상이 아니다
        assertEquals(false, MoodResolver.weeklyGoalMet(activeDaysThisWeek = 4, goalDays = 4, activeToday = false))
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
        // 수요일에 3일째 → 목표 3일을 오늘 넘겼다
        assertTrue(ctx(sessions, wed, goalDays = 3).weeklyGoalMet)
        assertFalse(ctx(sessions, wed, goalDays = 4).weeklyGoalMet)
        // 목표가 2일이면 화요일에 이미 넘겼다 — 수요일엔 축하하지 않는다
        assertFalse(ctx(sessions, wed, goalDays = 2).weeklyGoalMet)
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
