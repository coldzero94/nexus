package com.nexus.app.health

import android.os.Build
import androidx.health.connect.client.records.metadata.Device
import com.nexus.core.RecordingMethod
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 현재 기기 판정 (#205) — SPN 변경 후 오분류를 막는 판별의 **app 쪽 절반**.
 *
 * ## 왜 여기까지만 테스트하나
 *
 * `ExerciseRepository` 수준에서 "관측 → allowlist 병합 → 등급"을 통째로 세우려면 페이크 레코드의
 * `dataOrigin`을 지정해야 하는데, `Metadata`의 전체 생성자가 **라이브러리 internal**이라 공개
 * 팩토리(`autoRecordedWithId(id, device)`)로는 소스 패키지를 넣을 수 없다(`HealthFakes` 주석에도
 * 같은 한계가 적혀 있다). 병합→등급 체인은 `DeviceSourceResolverTest`(core)가 전수로 덮고,
 * 여기서는 **그 체인에 들어가는 판정**을 본다 — 틀리기 쉬운 쪽이 이쪽이다.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceIdentityTest {

    private fun phone(
        manufacturer: String? = Build.MANUFACTURER,
        model: String? = Build.MODEL,
        type: Int = Device.TYPE_PHONE,
    ) = Device(manufacturer = manufacturer, model = model, type = type)

    @Test
    fun `제조사와 모델이 이 기기와 같은 폰이면 이 기기다`() {
        assertTrue(DeviceIdentity.isThisDevice(phone()))
    }

    @Test
    fun `대소문자는 무시한다`() {
        // HC에 실려 오는 문자열의 대소문자는 기기·앱마다 다르다
        assertTrue(DeviceIdentity.isThisDevice(phone(manufacturer = Build.MANUFACTURER.uppercase())))
        assertTrue(DeviceIdentity.isThisDevice(phone(model = Build.MODEL.lowercase())))
    }

    @Test
    fun `다른 모델이면 이 기기가 아니다`() {
        assertFalse(DeviceIdentity.isThisDevice(phone(model = "SM-OTHER")))
    }

    @Test
    fun `다른 제조사면 이 기기가 아니다`() {
        assertFalse(DeviceIdentity.isThisDevice(phone(manufacturer = "other-maker")))
    }

    /**
     * 워치가 폰과 같은 제조사·모델 문자열을 실을 수 있다. 착용 기기 타입을 거부하지 않으면 워치
     * 기록이 "이 폰의 온디바이스 소스"로 계산돼, 워치 소스가 tierA 대신 tierB 경로로 새어 들어간다.
     */
    @Test
    fun `착용 기기 타입은 이 기기가 아니다`() {
        listOf(
            Device.TYPE_WATCH,
            Device.TYPE_FITNESS_BAND,
            Device.TYPE_CHEST_STRAP,
            Device.TYPE_RING,
            Device.TYPE_HEAD_MOUNTED,
        ).forEach { type ->
            assertFalse(DeviceIdentity.isThisDevice(phone(type = type)), "타입 $type 이 통과했다")
        }
    }

    /**
     * **타입 미상은 통과시킨다.** `TYPE_PHONE`만 인정하면 기능이 조용히 아무것도 안 할 위험이 크다 —
     * HC는 타입을 요구하지 않아 정직한 기록기가 `TYPE_UNKNOWN`으로 쓸 수 있고(이 저장소 페이크도
     * 그렇다), HC 1.1.0에는 태블릿 타입이 아예 없다. 게다가 `type`은 쓰는 앱이 정하는 값이라
     * 보안 가치도 없다 — 제조사·모델 일치가 실질 판정이다.
     */
    @Test
    fun `타입이 미상이어도 제조사·모델이 맞으면 이 기기다`() {
        assertTrue(DeviceIdentity.isThisDevice(phone(type = Device.TYPE_UNKNOWN)))
    }

    /**
     * "모르겠다"를 "일치"로 접으면 기기 정보가 비어 오는 서드파티 기록이 전부 온디바이스 소스로
     * 승격돼 allowlist가 무의미해진다.
     */
    @Test
    fun `기기 정보가 없거나 비면 이 기기가 아니다`() {
        assertFalse(DeviceIdentity.isThisDevice(null))
        assertFalse(DeviceIdentity.isThisDevice(phone(manufacturer = null)))
        assertFalse(DeviceIdentity.isThisDevice(phone(model = null)))
        assertFalse(DeviceIdentity.isThisDevice(phone(manufacturer = "")))
        assertFalse(DeviceIdentity.isThisDevice(phone(model = "   ")))
    }

    @Test
    fun `관측은 기기 판정과 기록 방식을 그대로 담는다`() {
        // autoMetadata의 기기는 제조사·모델이 비어 있다 — 이 기기로 보지 않는다
        val observed = DeviceIdentity.observe(autoMetadata(id = "s1"), RecordingMethod.AUTO_RECORDED)

        assertEquals(RecordingMethod.AUTO_RECORDED, observed.method)
        assertFalse(observed.recordedOnThisDevice)
    }
}
