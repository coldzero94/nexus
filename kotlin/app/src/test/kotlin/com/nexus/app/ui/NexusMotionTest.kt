package com.nexus.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** #262 — 모션 스케일 계약. 리듀스드모션(0)=즉시, 정상(1)=원값, 반감(0.5)=절반. */
class NexusMotionTest {
    @Test
    fun `정상 스케일은 원 duration 유지`() {
        assertEquals(240, NexusMotion.scaledDuration(240, 1f))
        assertEquals(360, NexusMotion.scaledDuration(NexusMotion.DURATION_LONG, 1f))
    }

    @Test
    fun `리듀스드모션(0)은 즉시(0ms)`() {
        assertEquals(0, NexusMotion.scaledDuration(240, 0f))
        assertEquals(0, NexusMotion.scaledDuration(NexusMotion.DURATION_XLONG, 0f))
    }

    @Test
    fun `반감 스케일은 절반 반올림`() {
        assertEquals(120, NexusMotion.scaledDuration(240, 0.5f))
        assertEquals(60, NexusMotion.scaledDuration(120, 0.5f))
    }

    @Test
    fun `음수 스케일은 0으로 클램프`() {
        assertEquals(0, NexusMotion.scaledDuration(240, -1f))
    }
}
