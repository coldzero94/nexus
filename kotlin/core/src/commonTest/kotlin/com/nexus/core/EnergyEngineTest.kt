package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnergyEngineTest {

    @Test
    fun earned_isXpDividedByRate_floored() {
        // 10pt = 1 에너지, 절사: 95pt → 9
        assertEquals(9, EnergyEngine.earnedTotal(95))
        assertEquals(0, EnergyEngine.earnedTotal(9))
    }

    @Test
    fun balance_isEarnedMinusSpent_clampedAtZero() {
        assertEquals(4, EnergyEngine.balance(cappedTotalXp = 90, totalSpent = 5))
        // 원장 취소로 획득이 줄어 과소모 상태 — 음수 대신 0 (빚 없음)
        assertEquals(0, EnergyEngine.balance(cappedTotalXp = 20, totalSpent = 5))
    }

    @Test
    fun spend_rules_forExpedition() {
        // 하루 60pt(걷기 1시간) → 6 에너지 → 원정(3) 2회 가능, 3회째 불가 (MVP §3 하루 2~3회)
        assertTrue(EnergyEngine.canSpend(cappedTotalXp = 60, totalSpent = 0, cost = EnergyEngine.EXPEDITION_COST))
        assertTrue(EnergyEngine.canSpend(cappedTotalXp = 60, totalSpent = 3, cost = EnergyEngine.EXPEDITION_COST))
        assertFalse(EnergyEngine.canSpend(cappedTotalXp = 60, totalSpent = 6, cost = EnergyEngine.EXPEDITION_COST))
    }

    @Test
    fun invalidInputs_rejected() {
        assertFailsWith<IllegalArgumentException> { EnergyEngine.earnedTotal(-1) }
        assertFailsWith<IllegalArgumentException> { EnergyEngine.balance(10, -1) }
        assertFailsWith<IllegalArgumentException> { EnergyEngine.canSpend(10, 0, 0) }
    }
}
