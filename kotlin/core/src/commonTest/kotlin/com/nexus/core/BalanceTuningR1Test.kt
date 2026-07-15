package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** #62 라운드 1 튜닝 — 신뢰계수 레벨 분리(#2②) · 시작 레벨 바닥값 · 연속성 보너스(#2③). */
class BalanceTuningR1Test {

    private val delta = 1e-9

    @Test
    fun trust_personalLevel_noPhonePenalty() {
        // 개인 레벨: 수기(C)만 제외, A·B 모두 100%
        assertEquals(1.0, TrustTier.A.personalXpMultiplier, delta)
        assertEquals(1.0, TrustTier.B.personalXpMultiplier, delta)
        assertEquals(0.0, TrustTier.C.personalXpMultiplier, delta)
        // 리더보드 가중치는 그대로 차등 보존
        assertEquals(1.0, TrustTier.A.xpMultiplier, delta)
        assertEquals(0.85, TrustTier.B.xpMultiplier, delta)
        assertEquals(0.0, TrustTier.C.xpMultiplier, delta)
    }

    @Test
    fun displayLevel_floorsAtOne() {
        assertEquals(1, LevelCurve.displayLevel(0)) // 신규
        assertEquals(1, LevelCurve.displayLevel(50)) // 저활동(레벨 0)도 표시 1
        assertEquals(1, LevelCurve.displayLevel(100))
        assertEquals(4, LevelCurve.displayLevel(800)) // 바닥 위는 그대로
    }

    @Test
    fun consistencyBonus_rewardsMaintainedFrequency() {
        assertEquals(1.05, ConsistencyBonus.weeklyMultiplier(activeDaysThisWeek = 3, activeDaysPrevWeek = 3), delta)
        assertEquals(1.05, ConsistencyBonus.weeklyMultiplier(4, 2), delta) // 증가
        assertEquals(1.0, ConsistencyBonus.weeklyMultiplier(2, 3), delta) // 감소 → 보너스 없음
        assertEquals(1.0, ConsistencyBonus.weeklyMultiplier(0, 0), delta) // 무활동
    }
}
