package com.nexus.core

/**
 * 관측된 기록 출처 한 건 (#205) — 신뢰 등급 판정 **전에** 모으는 원시 관측.
 *
 * @property packageName `dataOrigin.packageName` — 이 레코드를 쓴 앱.
 * @property recordedOnThisDevice 레코드 메타의 기기 정보가 **지금 이 기기**와 일치하는가.
 *   판정은 app 계층이 한다(core는 `Build`를 모른다).
 * @property method 기록 방식. 수기·미상 관측은 온디바이스 소스의 근거가 되지 못한다.
 */
data class ObservedSource(val packageName: String, val recordedOnThisDevice: Boolean, val method: RecordingMethod)

/**
 * 현재 기기 온디바이스 소스 판별 (#205) — SPN 변경 후 오분류를 막는다.
 *
 * ## 왜 API가 아니라 관측인가 (스파이크 결론)
 *
 * `TrustMapping.kt`의 TODO는 `getCurrentDeviceDataSource()`가 확정되면 붙이겠다고 미뤄 뒀는데,
 * **Health Connect 1.1.0에 그런 API가 없다.** `Metadata`가 주는 건 `dataOrigin`(쓴 앱의 패키지)과
 * `device`(제조사·모델·타입)뿐이다. 즉 "현재 기기의 온디바이스 소스 패키지"를 물어볼 창구가 없고,
 * **데이터에서 알아내는 것**이 유일한 길이다.
 *
 * 그래서 이렇게 판별한다: 레코드의 `device`가 지금 이 기기와 일치하고 그 기록이 수기가 아니면,
 * 그 레코드를 쓴 패키지는 **이 기기가 온디바이스로 기록한 소스**다.
 *
 * ## 왜 이게 필요했나
 *
 * 하드코딩 allowlist는 `com.sec.android.app.shealth`를 tierB로 본다. 2026-06 SPN 변경으로 온디바이스
 * 기록의 소스 패키지가 달라지면, 사용자가 **자기 폰으로 자동 기록한 진짜 운동이 Tier C(미신뢰)로
 * 떨어져 XP에서 제외**된다. 사용자가 아무것도 잘못하지 않았는데 성장이 멈추고, 원인은 화면에
 * 드러나지 않는다. 그리고 **복구 창이 닫힌다**: 미인정 세션엔 원장 행이 써지지 않으므로 나중에
 * 정상 분류되면 같은 멱등성 키로 지급되지만, 읽기 창(워커 7일·화면 28일)을 지나면 영구 미지급이다.
 *
 * ## 왜 tierB이고 tierA가 아닌가
 *
 * tierB의 정의가 "폰 기록"이다. 이 기기에서 자동 기록된 건 정확히 그것이고, 워치+심박(tierA)이라고
 * 주장할 근거는 없다.
 *
 * ⚠ 다만 **그 상한이 담아내는 건 리더보드 가중치(0.85)뿐**이다 — 개인 XP는 A·B가 모두 100%이고
 * MVP가 지급하는 건 개인 XP뿐이라, C→B 승격은 사실상 0% → 100%다. 게다가 `Metadata.device`는
 * **쓰는 앱이 채우는 값**이라(HC가 호스트와 대조하지 않는다) 이 판별은 위조 가능한 입력에 기댄다.
 * 받아들인 위험이고 근거는 `docs/STACK.md §1`에 적었다 — "관측이 오염돼도 피해가 갇힌다"고
 * 쓰지 않는 이유가 그것이다.
 *
 * ## 수기·미상을 배제하는 이유
 *
 * 수기 입력은 `TrustPolicy.classify`가 allowlist와 **무관하게** C로 보낸다. 그래서 수기 관측을
 * 근거로 삼아도 그 앱의 수기 기록이 승격되지는 않지만, **그 앱의 다른 자동 기록**이 승격된다.
 * "이 기기에서 손으로 입력한 앱"이 "이 기기가 자동 기록하는 소스"의 근거가 되면 안 되므로 뺀다.
 */
object DeviceSourceResolver {

    /**
     * 관측 목록에서 현재 기기 온디바이스 소스 패키지를 추린다.
     *
     * @return tierB로 병합할 패키지 집합. 근거가 없으면 빈 집합 — 그때는 하드코딩 기본값만 쓴다
     *   (없는 근거로 등급을 올리지 않는다).
     */
    fun onDeviceSources(observed: List<ObservedSource>): Set<String> = observed
        .asSequence()
        .filter { it.recordedOnThisDevice }
        .filter { it.method == RecordingMethod.AUTO_RECORDED || it.method == RecordingMethod.ACTIVELY_RECORDED }
        .map { it.packageName }
        .filter { it.isNotBlank() }
        .toSet()

    /**
     * 관측을 반영한 allowlist. 근거가 없으면 [base]를 그대로 돌려준다.
     *
     * 병합은 **더하기만** 한다 — 관측이 기본값을 빼앗을 수는 없다. 삼성헬스 패키지가 관측되지
     * 않은 배치에서 기본값이 사라지면, 그 배치만 등급이 달라지는 비결정적 판정이 된다.
     */
    fun merge(base: DataOriginAllowlist, observed: List<ObservedSource>): DataOriginAllowlist =
        base.withCurrentDeviceSources(onDeviceSources(observed))
}
