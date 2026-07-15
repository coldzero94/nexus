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

// TODO(#9/SPN): 2026-06 SPN 변경 대응 — 현재 기기 온디바이스 소스 패키지를
//   DataOriginAllowlist.withCurrentDeviceSource(...)로 병합. getCurrentDeviceDataSource() 확정 후 연결.
