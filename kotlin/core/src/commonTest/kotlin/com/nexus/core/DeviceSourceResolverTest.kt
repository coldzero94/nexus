package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #205 — 현재 기기 온디바이스 소스 판별. 이 판별이 없으면 SPN 변경 후 **자기 폰으로 자동 기록한
 * 진짜 운동이 Tier C로 떨어져 XP에서 제외**된다(그리고 원장에 그대로 박제된다).
 */
class DeviceSourceResolverTest {
    private val samsungPostSpn = "com.samsung.android.shealth.ondevice"

    private fun observed(
        pkg: String,
        onThisDevice: Boolean = true,
        method: RecordingMethod = RecordingMethod.AUTO_RECORDED,
    ) = ObservedSource(pkg, onThisDevice, method)

    @Test
    fun `이 기기의 자동 기록 소스를 찾는다`() {
        val sources = DeviceSourceResolver.onDeviceSources(listOf(observed(samsungPostSpn)))

        assertEquals(setOf(samsungPostSpn), sources)
    }

    @Test
    fun `사용자가 직접 시작한 기록도 근거가 된다`() {
        // ACTIVELY_RECORDED는 사용자가 앱에서 '시작'을 누른 것 — 자동만큼 온디바이스 근거다
        val sources = DeviceSourceResolver.onDeviceSources(
            listOf(observed(samsungPostSpn, method = RecordingMethod.ACTIVELY_RECORDED)),
        )

        assertEquals(setOf(samsungPostSpn), sources)
    }

    @Test
    fun `다른 기기에서 온 기록은 근거가 아니다`() {
        // 워치·다른 폰의 기록은 '이 기기의 온디바이스 소스'를 말해주지 않는다
        val sources = DeviceSourceResolver.onDeviceSources(listOf(observed("com.other.app", onThisDevice = false)))

        assertTrue(sources.isEmpty())
    }

    @Test
    fun `수기 입력은 근거가 아니다`() {
        // "이 기기에서 손으로 입력한 앱"이 "이 기기가 자동 기록하는 소스"의 근거가 되면 안 된다
        val sources = DeviceSourceResolver.onDeviceSources(
            listOf(observed("com.manual.app", method = RecordingMethod.MANUAL_ENTRY)),
        )

        assertTrue(sources.isEmpty())
    }

    @Test
    fun `미상 기록 방식도 근거가 아니다`() {
        val sources = DeviceSourceResolver.onDeviceSources(
            listOf(observed("com.unknown.app", method = RecordingMethod.UNKNOWN)),
        )

        assertTrue(sources.isEmpty())
    }

    @Test
    fun `빈 패키지명은 무시한다`() {
        assertTrue(DeviceSourceResolver.onDeviceSources(listOf(observed(""), observed("   "))).isEmpty())
    }

    @Test
    fun `관측이 없으면 빈 집합`() {
        assertTrue(DeviceSourceResolver.onDeviceSources(emptyList()).isEmpty())
    }

    // ── allowlist 병합 ──

    @Test
    fun `병합은 기본값을 유지하고 더하기만 한다`() {
        val merged = DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, listOf(observed(samsungPostSpn)))

        assertTrue(DataOriginAllowlist.DEFAULT.tierB.all { it in merged.tierB }, "기본값이 사라졌다")
        assertTrue(samsungPostSpn in merged.tierB)
    }

    @Test
    fun `관측이 없으면 allowlist가 그대로다`() {
        // 삼성헬스 패키지가 관측되지 않은 배치에서 기본값이 사라지면 비결정적 판정이 된다
        assertEquals(
            DataOriginAllowlist.DEFAULT,
            DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, emptyList()),
        )
    }

    @Test
    fun `관측은 tierA를 건드리지 않는다`() {
        // 관측이 올릴 수 있는 상한이 B라는 것이 이 판별의 안전장치다
        val merged = DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, listOf(observed(samsungPostSpn)))

        assertEquals(DataOriginAllowlist.DEFAULT.tierA, merged.tierA)
        assertTrue(samsungPostSpn !in merged.tierA)
    }

    // ── 실제 결함 재현: 이게 이 티켓의 존재 이유다 ──

    /**
     * SPN 변경 후 시나리오. 병합이 없으면 **자기 폰의 자동 기록 운동이 C로 떨어져 XP가 0이 된다.**
     */
    @Test
    fun `병합 전에는 SPN 변경 후 온디바이스 기록이 Tier C로 떨어진다`() {
        val tier = TrustPolicy.classify(
            recordingMethod = RecordingMethod.AUTO_RECORDED,
            dataOrigin = samsungPostSpn,
            hasHeartRate = false,
            allowlist = DataOriginAllowlist.DEFAULT,
        )

        assertEquals(TrustTier.C, tier, "이 결함이 없으면 이 티켓이 필요 없다")
        assertTrue(!TrustPolicy.isXpEligible(tier), "XP에서 제외된다")
    }

    @Test
    fun `병합 후에는 Tier B로 정상 분류된다`() {
        val observed = listOf(observed(samsungPostSpn))
        val merged = DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, observed)

        val tier = TrustPolicy.classify(
            recordingMethod = RecordingMethod.AUTO_RECORDED,
            dataOrigin = samsungPostSpn,
            hasHeartRate = false,
            allowlist = merged,
        )

        assertEquals(TrustTier.B, tier)
        assertTrue(TrustPolicy.isXpEligible(tier))
    }

    @Test
    fun `병합해도 수기 기록은 여전히 Tier C다`() {
        // 병합이 수기 제외 필터를 뚫으면 안 된다 — 그건 anti-abuse의 1차 방어다
        val merged = DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, listOf(observed(samsungPostSpn)))

        val tier = TrustPolicy.classify(
            recordingMethod = RecordingMethod.MANUAL_ENTRY,
            dataOrigin = samsungPostSpn,
            hasHeartRate = true,
            allowlist = merged,
        )

        assertEquals(TrustTier.C, tier)
    }

    @Test
    fun `병합해도 미지의 외부 소스는 Tier C다`() {
        // 관측되지 않은 패키지는 승격되지 않는다 — 병합이 allowlist를 무력화하지 않는다
        val merged = DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, listOf(observed(samsungPostSpn)))

        val tier = TrustPolicy.classify(
            recordingMethod = RecordingMethod.AUTO_RECORDED,
            dataOrigin = "com.stranger.fitness",
            hasHeartRate = false,
            allowlist = merged,
        )

        assertEquals(TrustTier.C, tier)
    }

    @Test
    fun `관측된 소스가 심박이 있어도 A로 올라가지 않는다`() {
        // tierA는 워치 소스 전용 — 관측으로는 B가 상한이다
        val merged = DeviceSourceResolver.merge(DataOriginAllowlist.DEFAULT, listOf(observed(samsungPostSpn)))

        val tier = TrustPolicy.classify(
            recordingMethod = RecordingMethod.AUTO_RECORDED,
            dataOrigin = samsungPostSpn,
            hasHeartRate = true,
            allowlist = merged,
        )

        assertEquals(TrustTier.B, tier)
    }
}
