package com.nexus.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription

/**
 * 게이지 접근성 (#224, E14-14) — 진행바를 **한 노드로 묶고 사람이 읽는 값**을 준다.
 *
 * 기본 진행바는 원시 비율만 읽어 "78퍼센트"처럼 들린다. 무엇의 78%인지, 만점이 얼마인지 알 수 없어
 * 시각장애 사용자에겐 사실상 의미가 없다. 레이블 텍스트와 바가 **별개 노드**로 흩어져 있는 것도
 * 문제다 — 훑다가 둘을 이어 붙이지 못한다.
 *
 * 그래서 라벨+바를 감싼 컨테이너에 이 모디파이어를 걸어 하위 노드를 지우고([clearAndSetSemantics])
 * "컨디션, 78 / 100점" 한 문장으로 읽히게 한다.
 *
 * @param label 무엇의 게이지인지("컨디션"·"레벨 진행" 등).
 * @param stateText 현재 상태를 사람이 읽는 문장("78, 100점 만점"·"레벨 3까지 40 XP" 등).
 * @param heading 이 노드가 화면 구조의 표제인가. `clearAndSetSemantics`가 하위를 지우므로,
 *   게이지가 카드의 대표 콘텐츠인 경우(#263 성장 히어로) 이걸 켜지 않으면 그 카드가 TalkBack
 *   표제 단위 이동에서 **아예 건너뛰어진다** — 가장 중요한 카드가 도달 불가가 된다.
 */
fun Modifier.gaugeSemantics(label: String, stateText: String, heading: Boolean = false): Modifier =
    clearAndSetSemantics {
        contentDescription = label
        stateDescription = stateText
        if (heading) heading()
    }
