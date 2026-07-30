package com.nexus.core

/**
 * Health Connect 가용성 3상태 (#236) — 이진으로 뭉개면 **고칠 수 있는 상태가 막다른 길로 보인다**.
 *
 * 갤럭시 프리인스톨 HC는 구버전인 경우가 흔하고, 그때 SDK는 `UPDATE_REQUIRED`를 준다. 이걸
 * "사용 불가"로 접으면 테스터는 업데이트 한 번으로 될 일을 영구 불가로 이해하고 이탈한다.
 */
enum class HealthAvailability {
    /** 정상 — 권한 요청으로 진행. */
    Available,

    /** 구버전 HC — **업데이트 CTA**를 줘야 한다. 진짜 불가가 아니다. */
    UpdateRequired,

    /** 이 기기에서 HC를 쓸 수 없음 — 액션 없는 안내(데모 모드). */
    Unavailable,
}

/**
 * 연결 게이트 (#236, E15-8) — 코어 루프 진입 자격을 판정한다.
 *
 * ## 왜 all-or-nothing이 문제였나
 *
 * 기존 판정은 요청 권한 **전부**를 요구했다(`containsAll(ALL)`). 그런데 백그라운드 읽기·과거 이력은
 * 안드로이드가 **별도로 게이팅해 자주 거부되는** 권한이다. 하나만 거부해도 `connected=false`가 되어
 * 걸음·운동을 다 승인한 사용자가 영구 데모 모드에 갇혔다. 12명 표본 알파에서 이런 오탈락은
 * 전환·게이트 지표를 직접 훼손한다.
 *
 * 그래서 권한을 두 등급으로 나눈다:
 * - **필수**: 걸음·운동 읽기. 이게 없으면 XP를 만들 수 없어 앱의 존재 이유가 없다.
 * - **선택**: 심박·수면·백그라운드·과거 이력. 없으면 **기능이 일부 줄어들 뿐** 코어 루프는 돈다.
 */
object ConnectGate {
    /**
     * 코어 루프에 들어갈 수 있는가 — **필수 권한이 전부 승인됐을 때만**.
     *
     * @param granted 실제로 승인된 권한 집합(`getGrantedPermissions()` 결과).
     * @param required 필수 권한 집합. 호출측(app)이 플랫폼 상수로 구성한다 — core는 HC에 의존하지 않는다.
     */
    fun isConnected(granted: Set<String>, required: Set<String>): Boolean =
        required.isNotEmpty() && granted.containsAll(required)

    /**
     * 승인되지 않은 선택 권한 — "꺼진 능력"을 표시하고 비차단 재요청을 걸 대상.
     *
     * 차단하지 않는 게 핵심이다. 이 목록이 비어 있지 않아도 [isConnected]는 true일 수 있다.
     */
    fun missingOptional(granted: Set<String>, optional: Set<String>): Set<String> = optional - granted
}
