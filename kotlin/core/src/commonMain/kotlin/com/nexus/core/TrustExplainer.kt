package com.nexus.core

/**
 * 등급 판정 이유 (#222, E14-12) — [TrustPolicy.classify]가 그 등급을 준 **실제 근거**.
 * UI가 "왜 이 등급인지"를 문구로 바꿔 보여준다(문구는 리소스, 판정은 여기).
 */
enum class TrustReason {
    /** 워치 기록 + 심박 시계열 → A. */
    WATCH_WITH_HEART_RATE,

    /** 워치 소스지만 심박이 없어 → B. */
    WATCH_WITHOUT_HEART_RATE,

    /** 폰 기록 → B(개인 성장 100% 반영). */
    PHONE_RECORDED,

    /** 사람이 직접 입력 → C(XP 제외). */
    MANUAL_ENTRY,

    /** 등록되지 않은 소스 → C(XP 제외). */
    UNKNOWN_SOURCE,
}

/**
 * 판정 근거 설명 (#222) — [TrustPolicy.classify]와 **같은 분기**를 쓴다. 등급과 이유가 어긋나지
 * 않도록 이유에서 등급을 역산([TrustReason.tier])하고, 두 함수가 같은 입력에 같은 결론을 낸다는
 * 사실은 단위 테스트가 고정한다.
 *
 * 표현 주의(RESEARCH §7.3): '조작 불가'·'인증' 같은 단정 표현을 쓰지 않는다 — 근거를 설명할 뿐
 * 진위를 판정하지 않는다.
 */
object TrustExplainer {
    /** 이 입력이 그 등급을 받은 이유 — [TrustPolicy.classify]와 동일 순서의 분기. */
    fun reasonFor(
        recordingMethod: RecordingMethod,
        dataOrigin: String,
        hasHeartRate: Boolean,
        allowlist: DataOriginAllowlist = DataOriginAllowlist.DEFAULT,
    ): TrustReason = when {
        recordingMethod == RecordingMethod.MANUAL_ENTRY -> TrustReason.MANUAL_ENTRY
        dataOrigin in allowlist.tierA && hasHeartRate -> TrustReason.WATCH_WITH_HEART_RATE
        dataOrigin in allowlist.tierA -> TrustReason.WATCH_WITHOUT_HEART_RATE
        dataOrigin in allowlist.tierB -> TrustReason.PHONE_RECORDED
        else -> TrustReason.UNKNOWN_SOURCE
    }
}

/** 이 이유가 함의하는 등급 — [TrustPolicy.classify] 결과와 항상 일치해야 한다(테스트로 고정). */
val TrustReason.tier: TrustTier
    get() = when (this) {
        TrustReason.WATCH_WITH_HEART_RATE -> TrustTier.A
        TrustReason.WATCH_WITHOUT_HEART_RATE, TrustReason.PHONE_RECORDED -> TrustTier.B
        TrustReason.MANUAL_ENTRY, TrustReason.UNKNOWN_SOURCE -> TrustTier.C
    }
