package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BaselineTest {
    private val delta = 1e-9

    @Test
    fun steadyRoutine_hoversNeutral_notSaturated() {
        // #2 포화 회귀: 주3 걷기(활동일 base 30, 휴식일 0). 오늘도 30이면 계수 1.0이어야(1.5 아님).
        val prior = listOf(30.0, 0.0, 30.0, 0.0, 30.0, 0.0, 0.0, 30.0, 0.0, 30.0, 0.0, 30.0, 0.0, 0.0)
        assertEquals(1.0, Baseline.personalCoefficient(todayBase = 30.0, priorDailyBases = prior), delta)
    }

    @Test
    fun doingMoreThanUsual_raisesCoef_cappedAtMax() {
        val prior = listOf(30.0, 0.0, 30.0, 0.0, 30.0)
        // 오늘 60 = 평소(30)의 2배 → 1.5 상한 클램프
        assertEquals(XpEngine.PERSONAL_COEF_MAX, Baseline.personalCoefficient(60.0, prior), delta)
        // 오늘 45 = 1.5배 → 1.5 (경계)
        assertEquals(1.5, Baseline.personalCoefficient(45.0, prior), delta)
        // 오늘 36 = 1.2배 → 1.2
        assertEquals(1.2, Baseline.personalCoefficient(36.0, prior), delta)
    }

    @Test
    fun doingLessThanUsual_lowersCoef_cappedAtMin() {
        val prior = listOf(30.0, 0.0, 30.0, 0.0, 30.0)
        assertEquals(XpEngine.PERSONAL_COEF_MIN, Baseline.personalCoefficient(10.0, prior), delta) // 0.33→0.8 클램프
        assertEquals(0.8, Baseline.personalCoefficient(24.0, prior), delta) // 0.8 경계
    }

    @Test
    fun coldStart_noHistory_isNeutral() {
        assertEquals(1.0, Baseline.personalCoefficient(30.0, emptyList()), delta)
        assertEquals(1.0, Baseline.personalCoefficient(30.0, listOf(0.0, 0.0, 0.0)), delta) // 휴식만
    }

    @Test
    fun restDayToday_isNeutral() {
        assertEquals(1.0, Baseline.personalCoefficient(0.0, listOf(30.0, 30.0)), delta)
    }

    @Test
    fun onlyLastWindowDaysCounted() {
        // 15일 전 값(1000)은 창 밖 → 무시. 최근 14일 활동일 평균만.
        val prior = listOf(1000.0) + List(14) { 30.0 }
        assertEquals(1.0, Baseline.personalCoefficient(30.0, prior), delta)
    }
}
