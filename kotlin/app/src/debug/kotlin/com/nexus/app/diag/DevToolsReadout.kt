package com.nexus.app.diag

import com.nexus.core.LedgerRow

/**
 * 개발자 도구 카드의 라이브 리드아웃 (#245) — 원장 투영과 진단 평문을 한 번에 갱신한다.
 *
 * 따로 상태로 두면 '다시 읽기' 한 번에 한쪽만 갱신되는 어긋남이 생긴다. 진단을 볼 때 값들이
 * 다른 시점을 가리키면 그게 곧 오진의 원인이다.
 *
 * @property deviceSources 관측된 현재 기기 온디바이스 소스 (#205). 비어 있으면 **SPN 대응이 실제로는
 *   아무것도 안 하고 있다**는 뜻이다 — 기기 메타가 예상과 다르면 관측이 매 배치 0건이 되고, 그때
 *   화면·로그에 아무 차이가 없어 "동작 중"과 구분되지 않는다. 실기기에서 그걸 구분할 유일한 수단이라
 *   여기 띄운다(패키지명은 건강 파생 값이 아니다).
 */
internal data class DevToolsReadout(
    val rows: List<LedgerRow>?,
    val snapshot: String,
    val deviceSources: Set<String> = emptySet(),
)
