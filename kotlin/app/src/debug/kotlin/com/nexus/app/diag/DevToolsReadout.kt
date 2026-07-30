package com.nexus.app.diag

import com.nexus.core.LedgerRow

/**
 * 개발자 도구 카드의 라이브 리드아웃 (#245) — 원장 투영과 진단 평문을 한 번에 갱신한다.
 *
 * 둘을 따로 상태로 두면 '다시 읽기' 한 번에 한쪽만 갱신되는 어긋남이 생긴다. 진단을 볼 때
 * 원장과 스냅샷이 다른 시점을 가리키면 그게 곧 오진의 원인이다.
 */
internal data class DevToolsReadout(val rows: List<LedgerRow>?, val snapshot: String)
