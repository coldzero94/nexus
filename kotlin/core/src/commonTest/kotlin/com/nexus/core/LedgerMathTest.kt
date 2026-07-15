package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerMathTest {

    @Test
    fun perDayCap_appliedIndependently() {
        // 1일차 raw 500 → 300(하드캡), 2일차 100 → 100
        assertEquals(400, LedgerMath.cappedTotalXp(mapOf(0L to 500.0, 1L to 100.0)))
    }

    @Test
    fun cancellation_reversesWithinGrantDay() {
        // 지급 250 − 취소 100 = 150 (상한 이전에 상쇄 → 캡 미적용 구간)
        assertEquals(150, LedgerMath.cappedTotalXp(mapOf(0L to 150.0)))
        // 상한 걸린 날의 취소: raw 400−100=300 → 캡 200+50=250
        assertEquals(250, LedgerMath.cappedTotalXp(mapOf(0L to 300.0)))
    }

    @Test
    fun overCancelledDay_clampsToZero() {
        // 과취소(방어): 일합 -50 → 0
        assertEquals(0, LedgerMath.cappedTotalXp(mapOf(0L to -50.0)))
        assertEquals(100, LedgerMath.cappedTotalXp(mapOf(0L to -50.0, 1L to 100.0)))
    }

    @Test
    fun emptyLedger_isZero() {
        assertEquals(0, LedgerMath.cappedTotalXp(emptyMap()))
    }
}
