package com.nexus.app.health

import android.os.Build
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.nexus.core.ObservedSource
import com.nexus.core.RecordingMethod

/**
 * 레코드 기기 메타 ↔ 지금 이 기기 (#205).
 *
 * ## 왜 제조사·모델 비교인가
 *
 * Health Connect에는 "현재 기기의 데이터 소스"를 묻는 API가 없다(1.1.0 확인). 레코드가 주는
 * 기기 정보는 `Device(type, manufacturer, model)`뿐이고, 그걸 `Build`와 맞춰 보는 것이
 * **이 기기가 쓴 기록인지** 판정할 수 있는 전부다. 근거는 [com.nexus.core.DeviceSourceResolver] KDoc.
 *
 * ## 타입은 '폰만 허용'이 아니라 '착용 기기 거부'다
 *
 * 처음엔 `TYPE_PHONE`만 인정했는데 그러면 **기능이 조용히 아무것도 안 할 위험**이 크다: HC는 타입을
 * 요구하지 않아서 정직한 기록기가 `TYPE_UNKNOWN`으로 쓸 수 있고(이 저장소의 페이크도 그렇다),
 * HC 1.1.0에는 **태블릿 타입이 아예 없다**. 그 경우 관측이 매 배치 0건이 되어 SPN 오분류가 그대로
 * 남는데, 테스트는 전부 통과한다.
 *
 * 게다가 `type`은 [REJECTED]든 아니든 **쓰는 앱이 정하는 값**이라 보안 가치가 없다. 그래서 착용
 * 기기 타입만 거부한다 — 워치 기록이 "이 폰의 온디바이스 소스"로 계산되는 경로는 그것으로 닫히고
 * (게다가 워치는 자기 모델명을 실어 모델 비교에서도 걸린다), 태블릿·미상은 통과한다.
 *
 * ## 못 알아보면 아니라고 답한다
 *
 * 제조사·모델이 비어 있으면 false다. "모르겠다"를 "일치"로 접으면, 기기 정보가 비어 오는
 * 서드파티 기록이 전부 온디바이스 소스로 승격돼 allowlist가 무의미해진다.
 */
internal object DeviceIdentity {

    /**
     * 착용 기기 타입 — 이 폰의 온디바이스 소스 근거가 될 수 없다. 워치는 tierA 판정을 별도로 받는다.
     */
    private val REJECTED = setOf(
        Device.TYPE_WATCH,
        Device.TYPE_FITNESS_BAND,
        Device.TYPE_CHEST_STRAP,
        Device.TYPE_RING,
        Device.TYPE_HEAD_MOUNTED,
    )

    /** 이 레코드가 **지금 이 기기가 쓴 기록**인가. */
    fun isThisDevice(device: Device?): Boolean {
        if (device == null || device.type in REJECTED) return false
        val manufacturer = device.manufacturer?.takeIf { it.isNotBlank() } ?: return false
        val model = device.model?.takeIf { it.isNotBlank() } ?: return false
        return manufacturer.equals(Build.MANUFACTURER, ignoreCase = true) &&
            model.equals(Build.MODEL, ignoreCase = true)
    }

    /** 레코드 메타 → core 관측. */
    fun observe(metadata: Metadata, method: RecordingMethod): ObservedSource = ObservedSource(
        packageName = metadata.dataOrigin.packageName,
        recordedOnThisDevice = isThisDevice(metadata.device),
        method = method,
    )
}
