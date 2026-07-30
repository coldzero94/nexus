package com.nexus.app.health

import androidx.health.connect.client.records.metadata.Metadata
import com.nexus.core.RecordingMethod

/** HC recordingMethod(Int) → core RecordingMethod(순수 enum). */
fun Int.toRecordingMethod(): RecordingMethod = when (this) {
    Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> RecordingMethod.ACTIVELY_RECORDED
    Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> RecordingMethod.AUTO_RECORDED
    Metadata.RECORDING_METHOD_MANUAL_ENTRY -> RecordingMethod.MANUAL_ENTRY
    else -> RecordingMethod.UNKNOWN
}

// SPN 대응은 #205에서 완료 — HC에 'getCurrentDeviceDataSource()' 같은 API가 없어(1.1.0 확인)
// 레코드 기기 메타를 Build와 맞춰 관측으로 판별한다. [DeviceIdentity] · [com.nexus.core.DeviceSourceResolver].
