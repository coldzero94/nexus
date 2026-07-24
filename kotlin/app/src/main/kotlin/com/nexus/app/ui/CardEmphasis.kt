package com.nexus.app.ui

/**
 * 카드 강조 위계 (#254, E16-4) — 정보 경중을 색으로 드러낸다(M3 컨테이너 롤 매핑).
 * 스톡 회색 카드 나열에서 벗어나 "지금 뭐가 중요한지"가 읽히게 한다. 매핑은 [NexusCard] 참조.
 */
enum class CardEmphasis { Neutral, Highlight, Celebration }
