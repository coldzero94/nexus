package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XpExplainerTest {

    private fun session(type: ActivityType?, minutes: Int, tier: TrustTier = TrustTier.A, epochDay: Long = 0L) =
        SessionInput(type, minutes, tier, epochDay)

    @Test
    fun emptyDay_zeroXp_noCapNotes() {
        val e = XpExplainer.explainDay(emptyList(), epochDay = 0L)
        assertEquals(0, e.cappedXp)
        assertTrue(e.lines.isEmpty())
        assertFalse(e.kneeApplied)
        assertFalse(e.hardCapped)
    }

    @Test
    fun singleSession_underKnee_rawEqualsCapped() {
        // 걷기 30분 = 30pt — 상한 미적용 구간에선 raw == capped
        val e = XpExplainer.explainDay(listOf(session(ActivityType.WALKING, minutes = 30)), epochDay = 0L)
        assertEquals(30, e.rawPoints)
        assertEquals(30, e.cappedXp)
        assertFalse(e.kneeApplied)
    }

    @Test
    fun tierC_lineIsVisible_butExcluded() {
        // 수기 기록은 목록에 남되(투명성) XP 미반영 — GrowthCalculator와 동일 규칙
        val e = XpExplainer.explainDay(
            listOf(
                session(ActivityType.STRENGTH, minutes = 40, tier = TrustTier.C),
                session(ActivityType.WALKING, minutes = 10),
            ),
            epochDay = 0L,
        )
        assertEquals(2, e.lines.size)
        assertFalse(e.lines[0].countsForXp)
        assertEquals(60, e.lines[0].basePoints) // 근거 점수는 보여준다
        assertEquals(10, e.cappedXp)
    }

    @Test
    fun unknownType_lineVisible_zeroBase() {
        val e = XpExplainer.explainDay(listOf(session(type = null, minutes = 25)), epochDay = 0L)
        assertEquals(1, e.lines.size)
        assertFalse(e.lines[0].countsForXp)
        assertEquals(0, e.lines[0].basePoints)
    }

    @Test
    fun kneeApplied_between200and300() {
        // 러닝 125분 = 250pt → 200 + 50×0.5 = 225 (체감 O, 하드캡 X)
        val e = XpExplainer.explainDay(listOf(session(ActivityType.RUNNING, minutes = 125)), epochDay = 0L)
        assertEquals(250, e.rawPoints)
        assertEquals(225, e.cappedXp)
        assertTrue(e.kneeApplied)
        assertFalse(e.hardCapped)
    }

    @Test
    fun hardCap_at300() {
        // 러닝 250분 = 500pt → 350 체감 → 하드캡 300
        val e = XpExplainer.explainDay(listOf(session(ActivityType.RUNNING, minutes = 250)), epochDay = 0L)
        assertEquals(300, e.cappedXp)
        assertTrue(e.hardCapped)
    }

    @Test
    fun otherDays_excluded() {
        val e = XpExplainer.explainDay(
            listOf(
                session(ActivityType.WALKING, minutes = 30, epochDay = 1L),
                session(ActivityType.WALKING, minutes = 20, epochDay = 0L),
            ),
            epochDay = 0L,
        )
        assertEquals(1, e.lines.size)
        assertEquals(20, e.cappedXp)
    }

    @Test
    fun matchesGrowthCalculator_forSingleDay() {
        // 분해 합계와 성장 탭 합계는 항상 일치해야 한다 (단일 산식 계약)
        val sessions = listOf(
            session(ActivityType.RUNNING, minutes = 90),
            session(ActivityType.STRENGTH, minutes = 100, tier = TrustTier.B),
            session(ActivityType.WALKING, minutes = 40, tier = TrustTier.C),
        )
        val e = XpExplainer.explainDay(sessions, epochDay = 0L)
        val g = GrowthCalculator.compute(sessions)
        assertEquals(g.totalXp, e.cappedXp)
    }
}
