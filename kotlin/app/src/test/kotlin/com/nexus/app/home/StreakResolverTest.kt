package com.nexus.app.home

import com.nexus.core.StreakCalculator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 활동 시리즈 조립 고정 (#214) — 완료 기준의 "휴식 유지"가 실제로 여기서 작동하는지 검증.
 * XP>0 또는 휴식일이면 활동으로 접힌다(끊김 방지). today=10 기준, 창=[6..10].
 */
class StreakResolverTest {

    private val xpOn = mapOf(10L to 100.0, 9L to 100.0, 7L to 100.0, 6L to 100.0) // 8일은 무XP

    @Test
    fun restDayFolds_bridgesGapAndKeepsStreak() {
        // 8일은 XP 없지만 휴식일 → active로 접혀 6~10 연속 유지(끊김 방지)
        val series = buildActiveSeries(xpOn, isRestDay = { it == 8L }, startEpoch = 6, todayEpoch = 10)
        assertEquals(listOf(true, true, true, true, true), series)
        assertEquals(5, StreakCalculator.status(series, priorLongest = 0).current)
    }

    @Test
    fun noRest_gapBreaksSeries() {
        // 휴식 없으면 8일 무XP가 끊김 → 오늘(10) 기준 9·10만 연속
        val series = buildActiveSeries(xpOn, isRestDay = { false }, startEpoch = 6, todayEpoch = 10)
        assertEquals(listOf(true, true, false, true, true), series)
        assertEquals(2, StreakCalculator.status(series, priorLongest = 0).current)
    }

    @Test
    fun todayRest_substitutesGrace_notPending() {
        // 오늘(10) XP 없지만 휴식 → active=true → 그레이스가 아니라 오늘까지 카운트
        val onlyPast = mapOf(9L to 100.0, 8L to 100.0)
        val series = buildActiveSeries(onlyPast, isRestDay = { it == 10L }, startEpoch = 6, todayEpoch = 10)
        val status = StreakCalculator.status(series, priorLongest = 0)
        assertEquals(3, status.current) // 8,9,10(휴식)
        assertEquals(false, status.todayPending)
    }

    @Test
    fun cancelledDay_netZero_isInactive() {
        // 순 XP 0(취소로 상쇄)인 날은 비활동 — 휴식도 아니면 끊김
        val net = mapOf(10L to 100.0, 9L to 0.0, 8L to 100.0)
        val series = buildActiveSeries(net, isRestDay = { false }, startEpoch = 8, todayEpoch = 10)
        assertEquals(listOf(true, false, true), series)
    }
}
