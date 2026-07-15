package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrowthCalculatorTest {

    private fun session(type: ActivityType?, minutes: Int, tier: TrustTier = TrustTier.A, epochDay: Long = 0L) =
        SessionInput(type, minutes, tier, epochDay)

    @Test
    fun emptySessions_yieldLevelOneBalanced() {
        val g = GrowthCalculator.compute(emptyList())
        assertEquals(0, g.totalXp)
        assertEquals(1, g.level)
        assertEquals(ClassAffinity.BALANCED, g.affinity)
        assertTrue(g.axisShares.isEmpty())
        assertTrue(g.stats.isEmpty())
    }

    @Test
    fun runningDominant_yieldsAgilityAffinity_andSplitsStats() {
        // 러닝 60분(Tier A) = base 120 → 지구력/민첩 50:50
        val g = GrowthCalculator.compute(listOf(session(ActivityType.RUNNING, minutes = 60)))
        assertEquals(120, g.totalXp)
        assertEquals(ClassAffinity.AGILITY, g.affinity)
        assertEquals(60, g.stats[Stat.ENDURANCE])
        assertEquals(60, g.stats[Stat.AGILITY])
    }

    @Test
    fun tierC_countsForAffinity_butNotXp() {
        val g = GrowthCalculator.compute(
            listOf(
                session(ActivityType.STRENGTH, minutes = 40, tier = TrustTier.C),
                session(ActivityType.WALKING, minutes = 10),
            ),
        )
        assertEquals(10, g.totalXp) // XP는 걷기 10분(Tier A)만
        assertEquals(null, g.stats[Stat.STRENGTH]) // 스탯에도 0 기여 — XP 스킵과 분리 회귀 방지
        assertEquals(ClassAffinity.STRENGTH, g.affinity) // 성향 비중은 근력 60 vs 걷기 10
    }

    @Test
    fun unknownType_ignoredEntirely() {
        val g = GrowthCalculator.compute(listOf(session(type = null, minutes = 60)))
        assertEquals(0, g.totalXp)
        assertTrue(g.axisShares.isEmpty())
    }

    @Test
    fun sameDaySessions_hitDailyCap() {
        // 같은 날 걷기 100분 × 5세션 = raw 500 → 200 + (500-200)×0.5 = 350 → 하드캡 300 (MVP §5)
        val g = GrowthCalculator.compute(List(5) { session(ActivityType.WALKING, minutes = 100) })
        assertEquals(300, g.totalXp)
        assertEquals(300, g.stats[Stat.ENDURANCE]) // 배분 합 = 상한 XP (보존)
    }

    @Test
    fun differentDays_notCappedTogether() {
        // 이틀에 나눠 raw 200+200 → 일별 상한 미달, 합 400
        val g = GrowthCalculator.compute(
            listOf(
                session(ActivityType.WALKING, minutes = 200, epochDay = 0L),
                session(ActivityType.WALKING, minutes = 200, epochDay = 1L),
            ),
        )
        assertEquals(400, g.totalXp)
    }

    @Test
    fun tierB_getsFullPersonalXp() {
        // MVP §5 '워치 없어도 1급': 개인 레벨엔 B도 100% — 0.85는 리더보드 전용 (리뷰 R1)
        val g = GrowthCalculator.compute(listOf(session(ActivityType.RUNNING, minutes = 60, tier = TrustTier.B)))
        assertEquals(120, g.totalXp)
    }

    @Test
    fun statsSum_equalsTotalXp_acrossMixedDay() {
        // 같은 날 러닝 90분(raw 180) + 근력 100분 Tier B(raw 150, 개인 100%) = 330 → 캡 200+65 = 265
        val g = GrowthCalculator.compute(
            listOf(
                session(ActivityType.RUNNING, minutes = 90),
                session(ActivityType.STRENGTH, minutes = 100, tier = TrustTier.B),
            ),
        )
        assertEquals(265, g.totalXp)
        assertEquals(g.totalXp, g.stats.values.sum()) // 스탯 배분 총합 = 총 XP (합 보존 불변식)
    }

    @Test
    fun mixedDay_accumulatesSameType_andSharesUsePreCapBase() {
        // 같은 날: 걷기 30+20(동일 유형 누적), 러닝 105(base 210), 근력 41(base 61) = raw 321 → 캡 260
        val g = GrowthCalculator.compute(
            listOf(
                session(ActivityType.WALKING, minutes = 30),
                session(ActivityType.WALKING, minutes = 20),
                session(ActivityType.RUNNING, minutes = 105),
                session(ActivityType.STRENGTH, minutes = 41),
            ),
        )
        assertEquals(260, g.totalXp) // 200 + 121×0.5 = 260.5 → 절사
        assertEquals(g.totalXp, g.stats.values.sum()) // 배분 잔여 유실 없음
        assertTrue(g.stats.keys.none { it.locked }) // 잠금 스탯(집중·친화)에 배분 금지
        assertEquals(210.0 / 321, g.axisShares[ActivityType.RUNNING]!!, 1e-9) // 성향은 캡 이전 base 기준
    }

    @Test
    fun axisShares_sumToOne() {
        val g = GrowthCalculator.compute(
            listOf(
                session(ActivityType.WALKING, minutes = 30),
                session(ActivityType.RUNNING, minutes = 30, epochDay = 1L),
            ),
        )
        val sum = g.axisShares.values.sum()
        assertTrue(sum > 0.999 && sum < 1.001)
    }
}
