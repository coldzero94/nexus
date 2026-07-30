package com.nexus.app.home

/**
 * 홈의 단발 카드 (#68) — 사용자가 확인하면 사라지는 것들.
 *
 * 닫기 동작이 카드마다 조금씩 다르다(아침·저녁은 오늘 다시 안 뜨게 마커를 남기고, 개봉 결과는
 * 표시 전용이라 상태만 지운다). 그 차이를 [HomeUiController.dismissCard] 한 곳에 모아 둔다.
 */
internal enum class HomeCard { MORNING, JOURNAL, EXPEDITION_REWARD }

/**
 * 홈이 원정 영역에 넘기는 것 묶음 (#68) — 출발·개봉·결과·닫기.
 *
 * 넷을 개별 파라미터로 넘기면 `HomeContent`가 파라미터 8개를 넘는다. 묶는 게 옳은 이유는 임계값이
 * 아니라 **넷이 한 기능의 조각**이라는 데 있다 — 원정 카드와 개봉 결과 카드는 같은 상호작용의 앞뒤다.
 */
internal data class ExpeditionUi(
    val onDepart: () -> Unit,
    val onOpen: () -> Unit,
    val reward: com.nexus.core.ExpeditionReward?,
    val onDismissReward: () -> Unit,
)
