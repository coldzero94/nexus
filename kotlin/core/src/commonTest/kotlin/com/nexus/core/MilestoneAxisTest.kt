package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 마일스톤 축이 배지와 **별개**인지 (#113, E5-13).
 *
 * 같은 엔진을 쓰되 축이 달라야 한다 — 조건이 겹치면 한 성취가 두 목록에서 동시에 열려
 * "장기 누적"이라는 별개 축의 의미가 사라진다.
 */
class MilestoneAxisTest {

    private val badgeVars = setOf("level", "cumulativeXp", "activeDaysTotal", "streakDays", "bestDaySteps")

    @Test
    fun `평생 활동일이 어휘에 있다`() {
        assertTrue("activeDaysLifetime" in BadgeContext.VARS)
    }

    /**
     * 창 기반 [BadgeContext.activeDaysTotal]과 **다른 값**이어야 한다. 같은 변수를 쓰면
     * 28일 창 밖으로 나간 날이 빠져 100일 마일스톤이 영영 안 열린다.
     */
    @Test
    fun `평생 활동일은 창 기반 활동일과 별개 값이다`() {
        val context = BadgeContext(activeDaysTotal = 12, activeDaysLifetime = 140)

        val vars = context.toVars()

        assertEquals(12.0, vars["activeDaysTotal"])
        assertEquals(140.0, vars["activeDaysLifetime"])
    }

    @Test
    fun `기존 배지 어휘는 그대로 남는다`() {
        // 마일스톤을 붙이며 배지 조건이 깨지면 이미 획득한 배지 판정이 흔들린다
        assertTrue(BadgeContext.VARS.containsAll(badgeVars))
    }
}
