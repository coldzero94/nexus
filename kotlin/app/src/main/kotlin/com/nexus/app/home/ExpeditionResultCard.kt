package com.nexus.app.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.nexus.app.ui.NexusIcons
import com.nexus.app.ui.celebrationEnter
import com.nexus.core.ExpeditionReward

/**
 * 원정 개봉 연출 (#68, E5-7) — 8시간을 기다린 것에 대한 답.
 *
 * ## 왜 이 카드가 필요한가
 *
 * 개봉이 상태만 지우고 카운터만 올리고 있었다. 8시간을 기다렸는데 버튼을 누르면 카드가 사라질 뿐이라
 * "무엇을 위해 기다렸나"에 대한 답이 없었다. `docs/MVP.md`가 원정에 준 역할은 **하루 2~3회 자연
 * 재방문**과 **동기화 지연(30~60분) 흡수**인데, 둘 다 "돌아와서 뭔가 있었다"는 경험이 있어야 성립한다.
 *
 * ## 보상은 이야기지 숫자가 아니다
 *
 * XP를 주지 않는다 — 원정이 활동과 무관한 XP 경로가 되면 "움직이면 자란다"는 전제가 흐려지고
 * 원장에 활동 무관 지급이 섞인다. 근거는 [ExpeditionRewardPicker] KDoc. 카피는 `expeditions.json`에서
 * 온다(하드코딩 금지 — 보상 추가는 JSON만).
 *
 * ## 등장 전환이 실제로 돌게 한다
 *
 * `AnimatedVisibility`를 보상 유무 **바깥**에 두고 `visible`을 false→true로 바꾼다. 노드가
 * `visible = true`인 채로 컴포지션에 들어오면 Compose가 등장 전환을 건너뛴다(#218에서 같은 함정).
 *
 * 나타났음은 `liveRegion`으로 낭독한다 — 홈 상단에 삽입되는 카드라 조용하면 스크린리더 사용자에게
 * 도달하지 않는다(#224).
 */
@Composable
internal fun ExpeditionResultCard(reward: ExpeditionReward?, onDismiss: () -> Unit) {
    // 퇴장 중에는 reward가 이미 null이라 마지막 값을 붙잡아 둔다 — 안 그러면 페이드아웃하며 빈 카드가 보인다
    val shown = remember { mutableStateOf(reward) }
    if (reward != null) shown.value = reward

    AnimatedVisibility(visible = reward != null, enter = celebrationEnter(), exit = fadeOut()) {
        val current = shown.value ?: return@AnimatedVisibility
        NexusCard(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            emphasis = CardEmphasis.Celebration,
            titleIcon = NexusIcons.expedition,
            title = stringResource(R.string.expedition_result_title),
        ) {
            Text(current.title, style = MaterialTheme.typography.titleSmall)
            Text(
                current.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.expedition_result_dismiss)) }
            }
        }
    }
}
