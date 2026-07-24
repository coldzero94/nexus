package com.nexus.core

/**
 * 걸음 막대 차트 스케일 (#258, E16-8) — 순수 함수. 일별 걸음 수를 그 주 최댓값 대비 비율([0,1])로
 * 매핑해 막대 높이를 정한다. 최댓값이 0(전부 무활동)이면 전부 0(0 나눗셈 안전) — 렌더 측에서
 * 무활동일은 얇은 baseline으로 그린다.
 */
object StepChartScale {
    /**
     * 각 값의 그 주 최댓값 대비 비율([0,1]). 음수·최댓값 0은 0으로 클램프(0 나눗셈·역방향 안전).
     * 절대 높이가 아니라 상대 비율이라, 렌더 높이(px/dp)는 호출측이 곱한다.
     */
    fun barRatios(values: List<Long>): List<Float> {
        val max = values.maxOrNull() ?: 0L
        if (max <= 0L) return List(values.size) { 0f }
        return values.map { (it.toDouble() / max).toFloat().coerceIn(0f, 1f) }
    }
}
