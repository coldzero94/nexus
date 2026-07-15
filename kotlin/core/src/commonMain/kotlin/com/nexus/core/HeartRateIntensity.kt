package com.nexus.core

/** 심박존 (최대심박 대비 %). 각 존의 강도 보정 배수 포함. */
enum class HrZone(
    val minPctOfMax: Double,
    val intensityMultiplier: Double,
) {
    Z1(0.50, 0.8), // 매우 가벼움
    Z2(0.60, 0.9), // 가벼움
    Z3(0.70, 1.0), // 보통(기준)
    Z4(0.80, 1.1), // 힘듦
    Z5(0.90, 1.2), // 최대
}

/**
 * 심박존 강도 보정 (#15, MVP §5): 심박 시계열이 있으면 기본점수에 ±20% 보정.
 * HC에 effortScore 대응물이 없어 **자체 산출 — 추정치**(UI에 추정 표기 필요).
 * 최대심박은 나이 미수집 시 [DEFAULT_MAX_HR] 추정.
 */
object HeartRateIntensity {
    const val MIN_CORRECTION = 0.8
    const val MAX_CORRECTION = 1.2
    const val DEFAULT_MAX_HR = 190 // 나이 입력 시 220-나이 등으로 정밀화

    fun zoneFor(
        heartRate: Int,
        maxHeartRate: Int = DEFAULT_MAX_HR,
    ): HrZone {
        require(maxHeartRate > 0) { "maxHeartRate must be > 0" }
        val pct = heartRate.toDouble() / maxHeartRate
        return when {
            pct >= HrZone.Z5.minPctOfMax -> HrZone.Z5
            pct >= HrZone.Z4.minPctOfMax -> HrZone.Z4
            pct >= HrZone.Z3.minPctOfMax -> HrZone.Z3
            pct >= HrZone.Z2.minPctOfMax -> HrZone.Z2
            else -> HrZone.Z1 // Z1 미만(매우 낮음)도 최소 보정 바닥
        }
    }

    /** 평균 심박 기반 단순 보정 (#8 ExerciseSummary.avgHeartRate 용). */
    fun correction(
        avgHeartRate: Int,
        maxHeartRate: Int = DEFAULT_MAX_HR,
    ): Double = zoneFor(avgHeartRate, maxHeartRate).intensityMultiplier

    /** 심박존 체류(초) 시간가중 보정 (정밀 — 심박 시계열 있을 때). 데이터 없으면 중립 1.0. */
    fun correctionFromDwelling(secondsPerZone: Map<HrZone, Int>): Double {
        val total = secondsPerZone.values.sum()
        if (total <= 0) return 1.0
        val weighted = secondsPerZone.entries.sumOf { (zone, sec) -> zone.intensityMultiplier * sec }
        return (weighted / total).coerceIn(MIN_CORRECTION, MAX_CORRECTION)
    }
}
