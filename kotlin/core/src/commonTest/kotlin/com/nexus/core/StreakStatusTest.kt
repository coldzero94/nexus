package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** 기세 상태 그레이스·단조 고정 (#214) — 무처벌 표시 계약. active는 오래된→최신, 마지막이 오늘. */
class StreakStatusTest {

    private fun s(vararg days: Boolean) = days.toList()

    @Test
    fun todayActive_countsToday() {
        val st = StreakCalculator.status(s(true, true, true), priorLongest = 0)
        assertEquals(3, st.current)
        assertEquals(false, st.todayPending)
    }

    @Test
    fun todayInactive_keepsYesterdayStreak_asPending() {
        // 오늘 아직 → 끊김 아님: 어제까지 2일 유지 + 그레이스
        val st = StreakCalculator.status(s(true, true, false), priorLongest = 0)
        assertEquals(2, st.current)
        assertEquals(true, st.todayPending)
    }

    @Test
    fun brokenBeforeToday_isZero() {
        // 어제 비활동(진짜 끊김) + 오늘 아직 → 0, 그레이스
        val st = StreakCalculator.status(s(true, false, false), priorLongest = 0)
        assertEquals(0, st.current)
        assertEquals(true, st.todayPending)
    }

    @Test
    fun consecutiveActive_counts() {
        // 호출자가 접은 시리즈(휴식 폴드 포함)가 연속 true면 그대로 카운트 — 폴드 검증은 StreakResolverTest
        val st = StreakCalculator.status(s(true, true, true, true), priorLongest = 0)
        assertEquals(4, st.current)
    }

    @Test
    fun longestIsMonotonic_neverBelowPrior() {
        // 현재가 짧아도 과거 최장은 보존(퇴행 없음)
        val st = StreakCalculator.status(s(false, true), priorLongest = 12)
        assertEquals(1, st.current)
        assertEquals(12, st.longest)
    }

    @Test
    fun longestTracksCurrentWhenExceedsPrior() {
        val st = StreakCalculator.status(s(true, true, true, true, true), priorLongest = 3)
        assertEquals(5, st.current)
        assertEquals(5, st.longest)
    }

    @Test
    fun emptySeries_isZero() {
        val st = StreakCalculator.status(emptyList(), priorLongest = 0)
        assertEquals(0, st.current)
        assertEquals(true, st.todayPending)
    }
}
