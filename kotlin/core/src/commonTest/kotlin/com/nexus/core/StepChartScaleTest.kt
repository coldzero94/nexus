package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** #258 — 걸음 막대 스케일. 최댓값 대비 비율·0 안전이 핵심. */
class StepChartScaleTest {
    @Test
    fun `최댓값은 1, 나머지는 비율`() {
        assertEquals(listOf(1f, 0.5f, 0.25f), StepChartScale.barRatios(listOf(100L, 50L, 25L)))
    }

    @Test
    fun `전부 0이면 전부 0 - 0 나눗셈 안전`() {
        assertEquals(listOf(0f, 0f, 0f), StepChartScale.barRatios(listOf(0L, 0L, 0L)))
    }

    @Test
    fun `빈 리스트는 빈 결과`() {
        assertEquals(emptyList(), StepChartScale.barRatios(emptyList()))
    }

    @Test
    fun `단일 값은 1`() {
        assertEquals(listOf(1f), StepChartScale.barRatios(listOf(8321L)))
    }

    @Test
    fun `0인 날은 0, 활동일은 비율 유지`() {
        assertEquals(
            listOf(0f, 1f, 0f, 0.5f),
            StepChartScale.barRatios(listOf(0L, 200L, 0L, 100L)),
        )
    }

    @Test
    fun `음수는 0으로 클램프`() {
        assertEquals(listOf(0f, 1f), StepChartScale.barRatios(listOf(-10L, 100L)))
    }
}
