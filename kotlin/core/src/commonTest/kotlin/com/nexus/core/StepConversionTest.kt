package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StepConversionTest {
    private val delta = 1e-9

    @Test
    fun walkingBase_convertsStepsToPoints() {
        assertEquals(0.0, StepConversion.walkingBase(0), delta)
        assertEquals(1.0, StepConversion.walkingBase(100), delta)
        assertEquals(10.0, StepConversion.walkingBase(1000), delta)
        assertEquals(100.0, StepConversion.walkingBase(10_000), delta)
        assertEquals(0.5, StepConversion.walkingBase(50), delta)
    }

    @Test
    fun walkingBase_rejectsNegative() {
        assertFailsWith<IllegalArgumentException> { StepConversion.walkingBase(-1) }
    }
}
