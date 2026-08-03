package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 평생 활동일 (#113, E5-13).
 *
 * 핵심은 **취소를 반영한다**는 것이다. 원장은 append-only라 삭제 대신 보상 이벤트를 덧붙이는데,
 * 하루가 통째로 상쇄됐는데도 그날을 세면 "함께한 100일"이 거짓이 된다 — 사용자가 Health Connect에서
 * 기록을 지웠는데 앱이 계속 세고 있는 상태다.
 */
class LifetimeSignalsTest {

    @Test
    fun `양수인 날만 센다`() {
        val days = mapOf(1L to 30, 2L to 0, 3L to 45)

        assertEquals(2, LifetimeSignals.activeDays(days))
    }

    /** 취소로 하루가 상쇄되면 그날은 활동일이 아니다 — 지운 기록이 영원히 남지 않게. */
    @Test
    fun `취소로 상쇄된 날은 세지 않는다`() {
        val days = mapOf(1L to 30, 2L to 0, 3L to -10)

        assertEquals(1, LifetimeSignals.activeDays(days))
    }

    @Test
    fun `기록이 없으면 0이다`() {
        assertEquals(0, LifetimeSignals.activeDays(emptyMap()))
    }

    /** 창(28일)과 무관하다 — 아주 오래된 날도 그대로 센다. 그게 '평생'의 정의다. */
    @Test
    fun `오래된 날도 센다`() {
        val days = (0L until 400L).associateWith { 10 }

        assertEquals(400, LifetimeSignals.activeDays(days))
    }
}
