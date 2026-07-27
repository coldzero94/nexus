package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #222 — 판정 이유. 핵심 계약: **이유가 함의하는 등급 == classify 결과**(두 분기가 어긋나면
 * 화면이 "B등급인데 워치+심박이라 A" 같은 모순을 보여준다).
 */
class TrustExplainerTest {
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
        for (method in RecordingMethod.entries) {
            for (origin in origins) {
                for (hr in listOf(true, false)) {
                    val classified = TrustPolicy.classify(method, origin, hr)
                    val explained = TrustExplainer.reasonFor(method, origin, hr).tier
                    assertEquals(classified, explained, "method=$method origin=$origin hr=$hr")
                }
            }
        }
    }
}
