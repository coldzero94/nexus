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
        assertEquals(25, e.kneeReducedPoints)
        assertFalse(e.hardCapped)
    }

    @Test
    fun hardCap_at300() {
        // 러닝 250분 = 500pt → 350 체감 → 하드캡 300 (체감 −150은 니 귀속, 나머지 −50은 하드캡 클리핑)
        val e = XpExplainer.explainDay(listOf(session(ActivityType.RUNNING, minutes = 250)), epochDay = 0L)
        assertEquals(500, e.rawPoints)
        assertEquals(300, e.cappedXp)
        assertTrue(e.kneeApplied)
        assertEquals(150, e.kneeReducedPoints)
        assertTrue(e.hardCapped)
    }

    @Test
    fun kneeEdge_exactly200_noKneeNote() {
        // 걷기 200분 = 정확히 니(200pt) — 체감 미적용(경계는 초과분부터). 거짓 체감 안내 방지.
        val e = XpExplainer.explainDay(listOf(session(ActivityType.WALKING, minutes = 200)), epochDay = 0L)
        assertEquals(200, e.rawPoints)
        assertEquals(200, e.cappedXp)
        assertFalse(e.kneeApplied)
        assertEquals(0, e.kneeReducedPoints)
        assertFalse(e.hardCapped)
    }

    @Test
    fun hardCapEdge_exactly400raw_reachedCountsAsCapped() {
        // 러닝 200분 = 400pt → 200 + 100 = 정확히 300 — "도달"도 하드캡으로 안내(경계 의미 고정)
        val e = XpExplainer.explainDay(listOf(session(ActivityType.RUNNING, minutes = 200)), epochDay = 0L)
        assertEquals(400, e.rawPoints)
        assertEquals(300, e.cappedXp)
        assertTrue(e.kneeApplied)
        assertEquals(100, e.kneeReducedPoints)
        assertTrue(e.hardCapped)
    }

    @Test
    fun allTierC_day_zeroRaw_noCapNotes() {
        // 전부 수기(C)인 날 — "오늘 왜 0 XP인가"의 핵심 시나리오. 제외분(base 360)이 raw에 새면 kneeApplied가 참이 된다.
        val e = XpExplainer.explainDay(
            listOf(
                session(ActivityType.STRENGTH, minutes = 40, tier = TrustTier.C),
                session(ActivityType.WALKING, minutes = 300, tier = TrustTier.C),
            ),
            epochDay = 0L,
        )
        assertEquals(2, e.lines.size)
        assertEquals(0, e.rawPoints)
        assertEquals(0, e.cappedXp)
        assertFalse(e.kneeApplied)
    }

    @Test
    fun mixedDay_truncation_caseTable() {
        // GrowthCalculatorTest.mixedDay와 대칭 케이스: 걷기 30+20, 러닝 105(210), 근력 41(61.5→61) = raw 321 → 260
        val e = XpExplainer.explainDay(
            listOf(
                session(ActivityType.WALKING, minutes = 30),
                session(ActivityType.WALKING, minutes = 20),
                session(ActivityType.RUNNING, minutes = 105),
                session(ActivityType.STRENGTH, minutes = 41),
            ),
            epochDay = 0L,
        )
        assertEquals(listOf(30, 20, 210, 61), e.lines.map { it.basePoints })
        assertEquals(321, e.rawPoints)
        assertEquals(260, e.cappedXp) // 200 + 60.5 절사
        assertTrue(e.kneeApplied)
        assertEquals(60, e.kneeReducedPoints) // 60.5 절사
        assertFalse(e.hardCapped)
    }

    @Test
    fun multiDayParity_sumOfDaysEqualsGrowthTotal() {
        // UI 계약은 일 단위: Σ explainDay(d) == 성장 탭 totalXp — 날짜 간 raw 오염도 함께 잡는다
        val sessions = listOf(
            session(ActivityType.RUNNING, minutes = 125, epochDay = 0L), // 250 → 225 (체감)
            session(ActivityType.WALKING, minutes = 30, epochDay = 1L), // 30 (무캡)
        )
        val day0 = XpExplainer.explainDay(sessions, epochDay = 0L)
        val day1 = XpExplainer.explainDay(sessions, epochDay = 1L)
        assertEquals(225, day0.cappedXp)
        assertEquals(30, day1.cappedXp)
        assertEquals(GrowthCalculator.compute(sessions).totalXp, day0.cappedXp + day1.cappedXp)
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
