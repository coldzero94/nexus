package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeartRateIntensityTest {

    private val delta = 1e-9
    private val maxHr = 190

    // (심박, 기대 존) — maxHr 190. 부동소수 경계 회피 위해 존 내부값 사용.
    private val zoneCases = listOf(
        80 to HrZone.Z1, // 0.42
        95 to HrZone.Z1, // 0.50 (Z2 미만)
        120 to HrZone.Z2, // 0.63
        140 to HrZone.Z3, // 0.74
        160 to HrZone.Z4, // 0.84
        180 to HrZone.Z5, // 0.95
        190 to HrZone.Z5, // 1.00
    )

    @Test
    fun zoneFor_matchesCaseTable() {
        zoneCases.forEach { (hr, zone) ->
            assertEquals(zone, HeartRateIntensity.zoneFor(hr, maxHr), "zoneFor($hr)")
        }
    }

    @Test
    fun correction_isPlusMinus20Percent() {
        assertEquals(0.8, HeartRateIntensity.correction(95, maxHr), delta) // Z1
        assertEquals(1.0, HeartRateIntensity.correction(140, maxHr), delta) // Z3 기준
        assertEquals(1.2, HeartRateIntensity.correction(180, maxHr), delta) // Z5
    }

    @Test
    fun dwelling_timeWeighted() {
        assertEquals(1.0, HeartRateIntensity.correctionFromDwelling(mapOf(HrZone.Z3 to 600)), delta)
        // 절반 Z1(0.8) + 절반 Z5(1.2) → 1.0
        assertEquals(1.0, HeartRateIntensity.correctionFromDwelling(mapOf(HrZone.Z1 to 300, HrZone.Z5 to 300)), delta)
        assertEquals(1.1, HeartRateIntensity.correctionFromDwelling(mapOf(HrZone.Z4 to 600)), delta)
    }

    @Test
    fun dwelling_emptyIsNeutral() {
        assertEquals(1.0, HeartRateIntensity.correctionFromDwelling(emptyMap()), delta)
        assertEquals(1.0, HeartRateIntensity.correctionFromDwelling(mapOf(HrZone.Z1 to 0)), delta)
    }

    @Test
    fun zoneFor_rejectsInvalidMaxHr() {
        assertFailsWith<IllegalArgumentException> { HeartRateIntensity.zoneFor(120, 0) }
    }
}
