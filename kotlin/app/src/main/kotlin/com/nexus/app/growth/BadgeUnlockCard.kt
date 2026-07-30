package com.nexus.app.growth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.nexus.app.R
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.celebrationEnter
import com.nexus.core.Badge

/**
 * 배지 획득 축하 (#218, E14-8) — 목록에만 조용히 얹히던 배지에 '따는 찰나'를 준다.
 *
 * ## 왜 필요했나
 *
 * `newlyUnlocked`는 #175에서 이미 계산되고 있었는데 **소비처가 없었다**("연출은 후속"이라는 주석만
 * 남아 있었다). 배지가 목록에 조용히 얹힐 뿐이라 획득 순간의 보상감이 0이었다. 수집형 리텐션은
 * 따는 찰나의 연출에서 나오므로, 배선된 신호를 연출로 잇는 것만으로 값이 나온다.
 *
 * ## 묶어서 한 번
 *
 * 동시에 여러 개가 열려도 카드 하나에 모아 보여준다. 배지마다 카드를 띄우면 첫 동기화에서 다섯 장이
 * 연달아 뜨는 폭주가 된다.
 *
 * ## 이름·설명은 표에서 온다
 *
 * 카피를 하드코딩하지 않는다 — 배지 추가는 `badges.json`만 고치는 게 #69의 계약이고, 축하 카피를
 * 코드에 박으면 새 배지가 이름 없이 축하된다.
 *
 * ## 나타났음을 낭독한다
 *
 * `liveRegion`이 없으면 축하가 **시각 채널에만** 남는다 — 배지 영역은 요약보다 늦게 도착해서
 * 카드가 사용자의 현재 포커스 **위에** 삽입되고, 그러면 아무것도 낭독되지 않은 채 아래 노드들만
 * 밀린다. 레벨업 카드(#224)가 같은 이유로 같은 처리를 한다.
 *
 * ## 레벨업 카드와 동시에 뜨지 않는다
 *
 * 우선순위는 호출부([GrowthScreen])가 정한다 — 레벨업이 먼저다. 두 축하가 겹치면 각각의 무게가
 * 반씩 깎이고, 화면 상단이 카드 두 장으로 막힌다.
 */
@Composable
internal fun BadgeUnlockCard(badges: List<Badge>, visible: Boolean, onDismiss: () -> Unit) {
    // 비었을 때 **일찍 반환하지 않는다.** 그러면 배지가 도착하는 순간 AnimatedVisibility 노드가
    // `visible = true`인 채로 컴포지션에 들어오고, 그때 Compose는 등장 전환을 **건너뛴다**(목표 상태로
    // 초기화한다). 완료 기준의 '스케일+페이드 1회'가 실제로는 안 도는 것이다. 노드를 먼저 만들어 두고
    // visible을 false→true로 바꿔야 전환이 돈다.
    //
    // 퇴장 중에는 `badges`가 이미 비어 있을 수 있어(확인 후 대기 집합이 비워진다) 마지막 비어 있지 않은
    // 목록을 붙잡아 둔다 — 안 그러면 페이드아웃하는 동안 빈 카드가 보인다.
    val shown = remember { mutableStateOf(badges) }
    if (badges.isNotEmpty()) shown.value = badges

    AnimatedVisibility(
        visible = visible && badges.isNotEmpty(),
        enter = celebrationEnter(),
        exit = fadeOut(),
    ) {
        NexusCard(
            // 축하가 나타났음을 낭독한다 (#224) — 시각 채널에만 남으면 축하가 도달하지 않는다
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            emphasis = CardEmphasis.Celebration,
            title = if (shown.value.size == 1) {
                stringResource(R.string.badge_unlock_title)
            } else {
                stringResource(R.string.badge_unlock_title_multi, shown.value.size)
            },
        ) {
            shown.value.forEach { badge ->
                // 목록과 같은 행 컴포넌트 — 축하에서 본 모습 그대로 목록에 남는다 (#266)
                BadgeGlyphRow(
                    name = badge.name,
                    description = badge.description,
                    icon = badge.icon,
                    earned = true,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.badge_unlock_dismiss)) }
            }
        }
    }
}
