package com.nexus.core

/** 기록 방식 — HC recordingMethod의 순수 표현(core는 안드로이드 비의존). */
enum class RecordingMethod {
    AUTO_RECORDED,
    ACTIVELY_RECORDED,
    MANUAL_ENTRY,
    UNKNOWN,
}

/**
 * 신뢰 등급 (MVP §4).
 * - [personalXpMultiplier]: **개인 레벨용** (#62/#2② 채택) — 수기·미상(C)만 제외, A·B 모두 100%.
 *   '워치 없어도 1급' 원칙: 폰 유저(B)에게 개인 성장 페널티를 주지 않는다.
 * - [xpMultiplier]: **리더보드 가중치**(향후) — A=1.0, B=0.85, C=0.0.
 */
enum class TrustTier(val xpMultiplier: Double, val personalXpMultiplier: Double) {
    A(1.0, 1.0), // 워치 세션 + 심박 시계열
    B(0.85, 1.0), // 폰 기록 — 리더보드는 감산, 개인 레벨은 100%
    C(0.0, 0.0), // 수기·미상 — XP 제외(개인·리더보드 공통)
}

/**
 * dataOrigin(패키지명) 신뢰 화이트리스트. **원격 구성 가능 구조** — 하드코딩 금지(기본값만 제공).
 *
 * ⚠ 2026-06 SPN 변경으로 온디바이스 기록의 소스 패키지가 달라질 수 있다. 그러면 사용자가 자기
 * 폰으로 자동 기록한 진짜 운동이 **Tier C(미신뢰)로 떨어져 XP에서 제외**된다 — 사용자가 아무것도
 * 잘못하지 않았는데 성장이 멈추고 원인은 화면에 드러나지 않으며, 원장은 append-only라 복구도 읽기 창 안에서만 된다
 * (미인정 세션엔 원장 행이 안 써지므로 나중에 정상 분류되면 지급되지만, 창을 지나면 영구 미지급).
 *
 * 그래서 현재 기기 소스를 런타임에 tierB로 병합한다([withCurrentDeviceSources]). "현재 기기 소스"를
 * 무엇으로 보는지는 [DeviceSourceResolver] — HC에 물어볼 API가 없어 **관측으로 판별**한다.
 */
data class DataOriginAllowlist(val tierA: Set<String>, val tierB: Set<String>) {
    fun withCurrentDeviceSource(packageName: String): DataOriginAllowlist = copy(tierB = tierB + packageName)

    /**
     * 관측된 현재 기기 소스들을 tierB에 병합. 빈 집합이면 그대로 — 근거 없이 등급을 올리지 않는다.
     *
     * tierA에는 절대 넣지 않는다. 관측이 올릴 수 있는 상한이 B라는 게 이 판별의 안전장치다
     * ([DeviceSourceResolver] KDoc).
     */
    fun withCurrentDeviceSources(packageNames: Set<String>): DataOriginAllowlist =
        if (packageNames.isEmpty()) this else copy(tierB = tierB + packageNames)

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
    ): TrustTier = // 판정 분기는 [TrustExplainer.reasonFor] 한 곳에만 둔다 — 등급과 "왜 이 등급인지"가
        // 서로 다른 분기를 갖고 있으면 화면이 모순된 설명을 보여준다(#222 리뷰).
        TrustExplainer.reasonFor(recordingMethod, dataOrigin, hasHeartRate, allowlist).tier

    /** XP 인정 대상인가(수기·미상 제외). */
    fun isXpEligible(tier: TrustTier): Boolean = tier != TrustTier.C
}
