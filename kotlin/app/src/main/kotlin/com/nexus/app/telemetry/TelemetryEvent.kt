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

    // 신호 이름에 'step'을 쓰지 않는다: 건강 앱에서 step은 걸음 수를 뜻해 denylist가 잡고(그게 맞다),
    // 사람이 읽어도 혼동된다. 'stage'로 부른다.
    //
    // 온보딩 스텝별 세분화 (#226, E14-16) — #47의 확장이다. 4단계 중 어디서 이탈하는지,
    // 몇 %가 권한 거부→데모로 빠지는지 알 수 없어 12인 알파 퍼널 관찰이 무의미했다.
    // 사용자당 1회([Telemetry.recordOnce]) — 스텝을 되돌아가도 이탈 지점이 흐려지지 않게.

    /** 온보딩 1단계 진입 = 앱을 처음 열고 시작한 사람. 퍼널의 분모. */
    ONBOARDING_STAGE_WELCOME("funnel.onboardingStageWelcome"),

    /** 2단계 — 권한이 왜 필요한지 설명. */
    ONBOARDING_STAGE_RATIONALE("funnel.onboardingStageRationale"),

    /** 3단계 — 삼성헬스 연동 안내. HC 미가용·업데이트 필요면 여기로 온다(#236). */
    ONBOARDING_STAGE_SAMSUNG_HEALTH("funnel.onboardingStageSamsungHealth"),

    /** 4단계 — 주간 목표 선택. */
    ONBOARDING_STAGE_WEEKLY_GOAL("funnel.onboardingStageWeeklyGoal"),

    /**
     * 권한 요청을 거부함 — 데모로 빠지는 가장 큰 원인이라 별도 신호로 본다.
     *
     * [PERMISSION_GRANTED]의 여집합으로 추론할 수도 있지만, 요청 화면에 도달하지 못한 경우(HC 미가용)와
     * 구분되지 않는다. 거부는 카피·설명의 문제고 미가용은 기기의 문제라 대응이 다르다.
     */
    PERMISSION_DENIED("funnel.permissionDenied"),

    /** 데모 모드로 계속하기를 선택 — 연결 없이 앱을 쓰기로 한 사람. */
    DEMO_CHOSEN("funnel.demoChosen"),

    /**
     * 배지 획득 축하가 표시됨 (#218) — **발생 사실만**. 어떤 배지인지·몇 개인지는 싣지 않는다.
     *
     * `recordOnce`가 아니라 매번 기록한다: 첫 획득뿐 아니라 수집이 계속 일어나는지가 리텐션
     * 신호이고, 배지 id·개수가 없어 페이로드는 이름 하나로 고정이다(불변식 ②).
     */
    BADGE_UNLOCKED("growth.badgeUnlocked"),
}
