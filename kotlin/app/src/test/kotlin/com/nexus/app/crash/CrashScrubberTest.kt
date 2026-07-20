package com.nexus.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 크래시 스크러버 고정 (#201) — 건강 파생 수치가 크래시 페이로드로 유출되지 않음을 assertion으로
 * 고정한다. 이 테스트가 깨지지 않고는 스크러빙을 무력화할 수 없다.
 */
class CrashScrubberTest {

    @Test
    fun masksDigits() {
        // 값 보간 예외가 생겨도 수치는 나가지 않는다
        assertEquals("steps=#", CrashScrubber.scrub("steps=8432"))
        assertEquals("hr # bpm at #", CrashScrubber.scrub("hr 142 bpm at 30"))
        assertEquals("#", CrashScrubber.scrub("2026"))
    }

    @Test
    fun leavesNonNumericTextUnchanged() {
        val msg = "NullPointerException in HomeUiController.onLoaded"
        assertEquals(msg, CrashScrubber.scrub(msg))
    }

    @Test
    fun nullSafe() {
        assertNull(CrashScrubber.scrub(null))
    }

    @Test
    fun emptyStays() {
        assertEquals("", CrashScrubber.scrub(""))
    }
}
