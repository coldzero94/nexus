package com.nexus.app.growth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.celebrationEnter
import com.nexus.core.StoryFragment

/**
 * 이야기 조각 획득 축하 (#112, E5-14) — 완료 기준의 "확률적으로 '조각 획득' 알림이 뜨고".
 *
 * ## 왜 도감 카운터만으로는 부족한가
 *
 * 이 기능의 값어치는 **물어왔다는 순간**에 있다. 도감 숫자만 조용히 올라가면 사용자는 성장 탭
 * 맨 아래까지 스크롤해서 세어봐야 알 수 있고, 그러면 "운동했더니 뭔가 생겼다"가 아니라
 * "언젠가 늘어나 있는 목록"이 된다. 배지(#218)가 정확히 같은 이유로 축하 카드를 얻었다.
 *
 * ## 본문까지 여기서 보여준다
 *
 * 배지 축하는 이름·설명 한 줄이면 되지만 조각은 **읽는 게 보상**이다. 도감으로 가라고 안내하면
 * 획득과 열람 사이에 탭 이동이 끼어 보상이 식는다.
 *
 * ## 시스템 알림이 아닌 이유
 *
 * 알림 권한과 방해 예산은 원정 귀환(`ExpeditionReturnWorker`)·리마인더가 이미 쓰고 있다.
 * 조각은 놓쳐도 사라지지 않는 수집물이라(대기 집합에 남는다) 푸시로 끊을 값어치가 없다.
 */
@Composable
internal fun StoryDropCard(fragments: List<StoryFragment>, visible: Boolean, onDismiss: () -> Unit) {
    // 비었을 때 일찍 반환하지 않는다 — 노드가 visible=true인 채로 들어오면 등장 전환이 생략된다.
    // 퇴장 중에는 목록이 이미 비어 마지막 값을 붙잡아 둔다 (#218과 같은 이유).
    val shown = remember { mutableStateOf(fragments) }
    if (fragments.isNotEmpty()) shown.value = fragments

    AnimatedVisibility(
        visible = visible && fragments.isNotEmpty(),
        enter = celebrationEnter(),
        exit = fadeOut(),
    ) {
        NexusCard(
            // 축하가 나타났음을 낭독한다 (#224) — 시각 채널에만 남으면 축하가 도달하지 않는다
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            emphasis = CardEmphasis.Celebration,
            title = if (shown.value.size == 1) {
                stringResource(R.string.story_drop_title)
            } else {
                stringResource(R.string.story_drop_title_multi, shown.value.size)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.md)) {
                shown.value.forEach { fragment ->
                    Column(
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
                    ) {
                        Text(fragment.title, style = MaterialTheme.typography.titleSmall)
                        Text(fragment.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.celebrate_dismiss))
                }
            }
        }
    }
}
