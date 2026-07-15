package com.nexus.core

/**
 * 활동 기록 도메인 모델 (MVP §4). provenance(dataOrigin·recordingMethod) + 신뢰 등급을 함께 지님.
 * 순수 데이터 — HC 레코드에서 app이 변환해 넣는다. XP 산출은 E3에서 이 위에 쌓인다.
 */
data class ActivityRecord(
    val type: ActivityType?,
    val minutes: Int,
    val dataOrigin: String,
    val recordingMethod: RecordingMethod,
    val hasHeartRate: Boolean,
    val trustTier: TrustTier,
) {
    val isXpEligible: Boolean get() = TrustPolicy.isXpEligible(trustTier)

    companion object {
        /** provenance로 신뢰 등급을 판정해 레코드를 만든다. */
        fun of(
            type: ActivityType?,
            minutes: Int,
            dataOrigin: String,
            recordingMethod: RecordingMethod,
            hasHeartRate: Boolean,
            allowlist: DataOriginAllowlist = DataOriginAllowlist.DEFAULT,
        ): ActivityRecord = ActivityRecord(
            type = type,
            minutes = minutes,
            dataOrigin = dataOrigin,
            recordingMethod = recordingMethod,
            hasHeartRate = hasHeartRate,
            trustTier = TrustPolicy.classify(recordingMethod, dataOrigin, hasHeartRate, allowlist),
        )
    }
}
