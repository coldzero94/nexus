package com.nexus.app.telemetry

/**
 * 계측 이벤트 allowlist (#46, E8-1) — **이 enum이 보낼 수 있는 신호의 전부**다.
 * 이벤트는 발생 사실만 기록한다: 건강 파생 수치(걸음·운동 시간·심박·XP·컨디션·레벨)는
 * 이름에도 파라미터에도 싣지 않는다 (Play 정책 — "의도치 않은 수신도 위반").
 *
 * 새 이벤트 추가 = ① 여기 상수 추가 ② [TelemetryPolicyTest]의 고정 allowlist 갱신
 * — 테스트가 함께 안 바뀌면 CI가 깨진다(리뷰 강제 장치). 퍼널 이벤트는 E8-2에서 확장.
 */
enum class TelemetryEvent(val signal: String) {
    /** 앱 열림 — 리텐션 분모. */
    APP_OPENED("app.opened"),

    // 퍼널 (#47, E8-2) — 온보딩 → 권한 → 첫 XP → 위젯 → 원정. 아래 4종은 사용자당 1회([Telemetry.recordOnce]).

    /** 온보딩 완료(연결 여부 무관). */
    ONBOARDING_COMPLETED("funnel.onboardingCompleted"),

    /** Health Connect 권한 연결 성공. */
    PERMISSION_GRANTED("funnel.permissionGranted"),

    /** 원장에 첫 XP 적립 확인 — 수치가 아니라 발생 사실만. */
    FIRST_XP("funnel.firstXp"),

    /** 홈 위젯 최초 설치. */
    WIDGET_INSTALLED("funnel.widgetInstalled"),

    /** 원정 개봉 — 반복 발생(참여 지표 겸용). */
    EXPEDITION_OPENED("funnel.expeditionOpened"),
}
