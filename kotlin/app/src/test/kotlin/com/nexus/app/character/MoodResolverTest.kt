package com.nexus.app.character

import com.nexus.core.MoodEvaluator
import com.nexus.core.MoodRule
import com.nexus.core.MoodTable
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
