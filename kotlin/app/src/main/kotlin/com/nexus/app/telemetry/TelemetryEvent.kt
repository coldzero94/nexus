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
}
