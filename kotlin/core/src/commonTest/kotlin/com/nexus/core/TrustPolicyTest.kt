package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustPolicyTest {

    private val watch = "com.samsung.android.wear.shealth"
    private val phone = "com.sec.android.app.shealth"
    private val unknown = "com.random.thirdparty"

    // (recordingMethod, dataOrigin, hasHeartRate) → 기대 Tier
    private data class Case(
        val method: RecordingMethod,
        val origin: String,
        val hr: Boolean,
        val expected: TrustTier,
    )

    private val cases = listOf(
        // ① 수기 입력은 소스 무관 무조건 C
        Case(RecordingMethod.MANUAL_ENTRY, watch, true, TrustTier.C),
        Case(RecordingMethod.MANUAL_ENTRY, phone, false, TrustTier.C),
        // ② 워치 소스 + 심박 → A
        Case(RecordingMethod.ACTIVELY_RECORDED, watch, true, TrustTier.A),
        // 워치 소스지만 심박 없음 → B
        Case(RecordingMethod.ACTIVELY_RECORDED, watch, false, TrustTier.B),
        // 폰 신뢰 앱 → B
        Case(RecordingMethod.AUTO_RECORDED, phone, false, TrustTier.B),
        Case(RecordingMethod.ACTIVELY_RECORDED, phone, true, TrustTier.B),
        // ③ 미상 소스 → C
        Case(RecordingMethod.AUTO_RECORDED, unknown, true, TrustTier.C),
        Case(RecordingMethod.UNKNOWN, unknown, false, TrustTier.C),
    )

    @Test
    fun classify_matchesCaseTable() {
        cases.forEach { c ->
            assertEquals(
                c.expected,
                TrustPolicy.classify(c.method, c.origin, c.hr),
                "case: $c",
            )
        }
    }

    @Test
    fun manualEntry_isNotXpEligible() {
        val tier = TrustPolicy.classify(RecordingMethod.MANUAL_ENTRY, phone, false)
        assertFalse(TrustPolicy.isXpEligible(tier), "수기 입력은 XP 대상에서 제외")
    }

    @Test
    fun trustedSources_areXpEligible() {
        assertTrue(TrustPolicy.isXpEligible(TrustTier.A))
        assertTrue(TrustPolicy.isXpEligible(TrustTier.B))
    }

    @Test
    fun currentDeviceSource_mergedAsTierB() {
        // SPN 대응: 온디바이스 소스를 런타임에 tierB로 병합하면 B로 인정
        val onDevice = "com.samsung.android.something"
        val allowlist = DataOriginAllowlist.DEFAULT.withCurrentDeviceSource(onDevice)
        assertEquals(
            TrustTier.B,
            TrustPolicy.classify(RecordingMethod.AUTO_RECORDED, onDevice, false, allowlist),
        )
    }
}
