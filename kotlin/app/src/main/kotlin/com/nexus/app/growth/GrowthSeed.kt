package com.nexus.app.growth

/**
 * 테스트가 세우는 성장 화면 초기 상태 (#320·#263) — **프로덕션은 항상 null**.
 *
 * `initialLoad`·`initialChange`를 따로 받다가 하나로 묶었다. 컨스트럭터 파라미터가 8개가 되어
 * detekt 임계를 넘긴 게 계기지만, 묶은 게 더 맞다: 둘은 **같은 목적의 한 개념**("이 화면을 이 상태로
 * 세워라")이고, 이름이 붙으면 프로덕션 경로에 이런 값이 흘러들 자리가 없다는 것도 한눈에 보인다.
 *
 * [change]가 필요한 이유는 AC 검증 때문이다. 축하 카드(#61)와 히어로의 **공존**이 완료 기준인데,
 * `change`는 실제 로드가 기준점과 비교해 만들므로 주입 없이는 축하가 뜬 화면을 세울 수 없었다.
 *
 * @property load 로드 분기(로딩·미연결·실패·성공).
 * @property change 축하 대상 변화(레벨업·성향 변화).
 */
internal data class GrowthSeed(val load: GrowthLoad? = null, val change: GrowthChange? = null)
