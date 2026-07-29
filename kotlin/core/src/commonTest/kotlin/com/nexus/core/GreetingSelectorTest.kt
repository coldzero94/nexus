package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #220 — 인사 변주. 두 가지가 핵심이다:
 * ① 시간대 경계가 정확할 것 ② **3일+ 공백을 건드리지 않을 것**(복귀 환영 #30과 이중 축하 금지).
 */
class GreetingSelectorTest {
    private fun select(hour: Int = 12, active: Int = 0, gap: Int = 0) = GreetingSelector.select(hour, active, gap)

    @Test
    fun `시간대 경계`() {
        assertEquals(TimeOfDay.MORNING, GreetingSelector.timeOfDay(GreetingSelector.MORNING_START_HOUR))
        assertEquals(TimeOfDay.MORNING, GreetingSelector.timeOfDay(GreetingSelector.DAY_START_HOUR - 1))
        assertEquals(TimeOfDay.DAY, GreetingSelector.timeOfDay(GreetingSelector.DAY_START_HOUR))
        assertEquals(TimeOfDay.DAY, GreetingSelector.timeOfDay(GreetingSelector.EVENING_START_HOUR - 1))
        assertEquals(TimeOfDay.EVENING, GreetingSelector.timeOfDay(GreetingSelector.EVENING_START_HOUR))
    }

    @Test
    fun `심야는 저녁의 연장 — 새벽 3시에 좋은 아침은 어색하다`() {
        assertEquals(TimeOfDay.EVENING, GreetingSelector.timeOfDay(0))
        assertEquals(TimeOfDay.EVENING, GreetingSelector.timeOfDay(3))
        assertEquals(TimeOfDay.EVENING, GreetingSelector.timeOfDay(23))
    }

    @Test
    fun `오늘 움직였으면 그걸 먼저 알아본다 — 시간대 인사보다 앞선다`() {
        assertEquals(GreetingVariant.FirstActivityToday, select(hour = 7, active = 30))
        assertEquals(GreetingVariant.FirstActivityToday, select(hour = 20, active = 30))
    }

    @Test
    fun `하루 이틀 공백은 반가움`() {
        assertEquals(GreetingVariant.BackAfterShortGap, select(gap = 1))
        assertEquals(GreetingVariant.BackAfterShortGap, select(gap = 2))
    }

    @Test
    fun `3일 이상 공백은 복귀 환영이 맡는다 — 말풍선은 비켜준다`() {
        // 한 번의 복귀를 두 번 축하하지 않는다 (#30 ReturnWelcomePolicy)
        val threshold = ReturnWelcomePolicy.WELCOME_GAP_DAYS
        assertEquals(GreetingVariant.None, select(hour = 12, gap = threshold))
        assertEquals(GreetingVariant.None, select(hour = 12, gap = threshold + 10))
        // 다만 시간대 인사까지 막지는 않는다 — 아침이면 아침 인사는 한다
        assertEquals(GreetingVariant.Morning, select(hour = 7, gap = threshold))
    }

    @Test
    fun `아침 저녁만 인사하고 낮은 기존 대사에 맡긴다`() {
        assertEquals(GreetingVariant.Morning, select(hour = 7))
        assertEquals(GreetingVariant.Evening, select(hour = 21))
        assertEquals(GreetingVariant.None, select(hour = 14))
    }
}
