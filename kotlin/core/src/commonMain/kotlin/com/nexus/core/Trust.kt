package com.nexus.core

/** 기록 방식 — HC recordingMethod의 순수 표현(core는 안드로이드 비의존). */
enum class RecordingMethod {
    AUTO_RECORDED,
    ACTIVELY_RECORDED,
    MANUAL_ENTRY,
    UNKNOWN,
}

/**
 * 신뢰 등급 (MVP §4). Tier C는 XP 제외(계수 0).
 * ⚠ 신뢰 계수는 개인 레벨엔 미적용 권고(#2 시뮬 결과) — 향후 리더보드 가중에만. 여기선 구조만 보존.
 */
enum class TrustTier(val xpMultiplier: Double) {
    A(1.0), // 워치 세션 + 심박 시계열
    B(0.85), // 자동/능동 폰 기록·신뢰 앱
    C(0.0), // 수기·미상 — XP 제외
}

/**
 * dataOrigin(패키지명) 신뢰 화이트리스트. **원격 구성 가능 구조** — 하드코딩 금지(기본값만 제공).
 * ⚠ 2026-06 SPN 변경: 온디바이스 소스가 "android" 대신 getCurrentDeviceDataSource()로 옴 →
 *   현재 기기 소스를 런타임에 tierB로 병합([withCurrentDeviceSource]).
 */
data class DataOriginAllowlist(val tierA: Set<String>, val tierB: Set<String>) {
    fun withCurrentDeviceSource(packageName: String): DataOriginAllowlist = copy(tierB = tierB + packageName)

    companion object {
        /** 원격 구성 전 기본값. 실제 패키지·워치 소스는 #12 실측·원격 구성으로 확정. */
        val DEFAULT =
            DataOriginAllowlist(
                tierA = setOf("com.samsung.android.wear.shealth"),
                tierB = setOf("com.sec.android.app.shealth"),
            )
    }
}

/** 신뢰 필터 3종 (#9, STACK.md §1). */
object TrustPolicy {
    /** 일일 XP 인정 상한(anti-abuse). 적용은 XP 엔진(E3). */
    const val DAILY_ACCEPTED_XP_CAP: Int = 300

    /**
     * 필터 ①수기 제외 ②dataOrigin 등급 ③(심박 유무로 A/B 구분).
     * - MANUAL_ENTRY → 무조건 C
     * - tierA 소스 + 심박 → A / tierA(심박 없음)·tierB → B
     * - 그 외(미상 소스) → C
     */
    fun classify(
        recordingMethod: RecordingMethod,
        dataOrigin: String,
        hasHeartRate: Boolean,
        allowlist: DataOriginAllowlist = DataOriginAllowlist.DEFAULT,
    ): TrustTier = when {
        recordingMethod == RecordingMethod.MANUAL_ENTRY -> TrustTier.C
        dataOrigin in allowlist.tierA && hasHeartRate -> TrustTier.A
        dataOrigin in allowlist.tierA || dataOrigin in allowlist.tierB -> TrustTier.B
        else -> TrustTier.C
    }

    /** XP 인정 대상인가(수기·미상 제외). */
    fun isXpEligible(tier: TrustTier): Boolean = tier != TrustTier.C
}
