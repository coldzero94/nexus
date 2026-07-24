package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #259 — XP 게이지 스케일. 니이하·초과·하드캡 근처 3구간 매핑이 핵심. */
class XpGaugeScaleTest {
    private val tol = 0.001f

    @Test
    fun `니 마커는 전체의 니÷캡 위치`() {
        // 200/300 ≈ 0.667
        assertEquals(0.6667f, XpGaugeScale.kneeFraction(), tol)
    }

    @Test
    fun `니 이하 - 선형 채움`() {
        assertEquals(0f, XpGaugeScale.fillFraction(0), tol)
        assertEquals(0.3333f, XpGaugeScale.fillFraction(100), tol) // 100/300
        assertFalse(XpGaugeScale.reachedKnee(100))
        assertFalse(XpGaugeScale.reachedKnee(199))
    }

    @Test
    fun `니 초과 - 소프트 구간`() {
        assertEquals(0.6667f, XpGaugeScale.fillFraction(200), tol) // 니 지점
        assertTrue(XpGaugeScale.reachedKnee(200))
        assertEquals(0.8333f, XpGaugeScale.fillFraction(250), tol) // 250/300
        assertTrue(XpGaugeScale.reachedKnee(250))
    }

    @Test
    fun `하드캡 근처·초과 - 1로 클램프`() {
        assertEquals(1f, XpGaugeScale.fillFraction(300), tol)
        assertEquals(1f, XpGaugeScale.fillFraction(350), tol) // 이론상 도달 불가지만 안전 클램프
    }

    @Test
    fun `음수 XP는 0으로 클램프`() {
        assertEquals(0f, XpGaugeScale.fillFraction(-10), tol)
    }

    @Test
    fun `비정상 캡은 0`() {
        assertEquals(0f, XpGaugeScale.fillFraction(100, cap = 0.0), tol)
        assertEquals(0f, XpGaugeScale.kneeFraction(cap = 0.0), tol)
    }
}
