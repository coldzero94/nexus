package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #222 — 판정 이유. 핵심 계약: **이유가 함의하는 등급 == classify 결과**(두 분기가 어긋나면
 * 화면이 "B등급인데 워치+심박이라 A" 같은 모순을 보여준다).
 */
class TrustExplainerTest {
    /** 파라미터 조합 1건 — 중첩 루프 대신 곱집합을 펼쳐 검증한다. */
    private data class Case(
        val method: RecordingMethod,
        val origin: String,
        val hasHr: Boolean,
        val allowlist: DataOriginAllowlist,
    )

    private val watch = "com.samsung.android.wear.shealth"
    private val phone = "com.sec.android.app.shealth"
    private val unknown = "com.example.unknown"

    @Test
    fun `워치 + 심박은 A`() {
        val reason = TrustExplainer.reasonFor(RecordingMethod.AUTO_RECORDED, watch, hasHeartRate = true)
        assertEquals(TrustReason.WATCH_WITH_HEART_RATE, reason)
        assertEquals(TrustTier.A, reason.tier)
    }

    @Test
    fun `워치지만 심박 없으면 B`() {
        val reason = TrustExplainer.reasonFor(RecordingMethod.AUTO_RECORDED, watch, hasHeartRate = false)
        assertEquals(TrustReason.WATCH_WITHOUT_HEART_RATE, reason)
        assertEquals(TrustTier.B, reason.tier)
    }

    @Test
    fun `폰 기록은 B - 개인 성장 100% 반영 대상`() {
        val reason = TrustExplainer.reasonFor(RecordingMethod.AUTO_RECORDED, phone, hasHeartRate = false)
        assertEquals(TrustReason.PHONE_RECORDED, reason)
        assertEquals(TrustTier.B, reason.tier)
        assertEquals(1.0, reason.tier.personalXpMultiplier)
    }

    @Test
    fun `수기 입력은 소스와 무관하게 C`() {
        val reason = TrustExplainer.reasonFor(RecordingMethod.MANUAL_ENTRY, watch, hasHeartRate = true)
        assertEquals(TrustReason.MANUAL_ENTRY, reason)
        assertEquals(TrustTier.C, reason.tier)
    }

    @Test
    fun `미등록 소스는 C`() {
        val reason = TrustExplainer.reasonFor(RecordingMethod.AUTO_RECORDED, unknown, hasHeartRate = true)
        assertEquals(TrustReason.UNKNOWN_SOURCE, reason)
        assertEquals(TrustTier.C, reason.tier)
    }

    @Test
    fun `이유가 함의하는 등급은 classify 결과와 항상 일치`() {
        val origins = listOf(watch, phone, unknown)
        // 화이트리스트 축도 포함 — SPN 변경(withCurrentDeviceSource)이 한쪽에만 반영되는 드리프트 방지
        val allowlists = listOf(
            DataOriginAllowlist.DEFAULT,
            DataOriginAllowlist.DEFAULT.withCurrentDeviceSource(unknown),
        )
        val cases = RecordingMethod.entries.flatMap { method ->
            origins.flatMap { origin ->
                listOf(true, false).flatMap { hr ->
                    allowlists.map { allowlist -> Case(method, origin, hr, allowlist) }
                }
            }
        }
        cases.forEach { c ->
            val classified = TrustPolicy.classify(c.method, c.origin, c.hasHr, c.allowlist)
            val explained = TrustExplainer.reasonFor(c.method, c.origin, c.hasHr, c.allowlist).tier
            assertEquals(classified, explained, "method=${c.method} origin=${c.origin} hr=${c.hasHr}")
        }
    }

    @Test
    fun `현재 기기 소스가 병합되면 미상이 아니라 폰 기록으로 설명된다`() {
        val allowlist = DataOriginAllowlist.DEFAULT.withCurrentDeviceSource(unknown)
        val reason = TrustExplainer.reasonFor(RecordingMethod.AUTO_RECORDED, unknown, false, allowlist)
        assertEquals(TrustReason.PHONE_RECORDED, reason)
        assertEquals(TrustTier.B, reason.tier)
    }
}
