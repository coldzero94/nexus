package com.nexus.app.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.CharacterComposer
import com.nexus.app.onboarding.OnboardingStore
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusMotion
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.FirstSession
import com.nexus.core.FirstSessionCue

/** 축하 표정 (#211·#66) — mood_triggers.json의 자랑 표정 키. 아트가 없으면 폴백. */
private const val PROUD_FACE = "proud_sparkle"

/**
 * 첫 행동 코치 (#211, E14-1) — "지금 잠깐 걸으면 첫 성장을 볼 수 있어요".
 *
 * 소급 레벨 연출(#44)은 이력 레벨 2+에만 발동해서 완전 신규·저이력 사용자는 홈에 착지해도
 * 할 일을 못 찾았다. 여기서 **다음 한 걸음**을 구체적으로 지정해 첫 성공 경험까지 데려간다.
 *
 * 카피는 반영 속도를 약속하지 않는다(불변식 ⑤) — "지금 걸으면 곧바로 보인다"고 하면 HC 전파
 * 30~60분에 배신당한다. 대신 '오늘 안에'로 두어 지킬 수 있는 말만 한다.
 */
@Composable
internal fun FirstCoachCard(onDismiss: () -> Unit) {
    NexusCard(title = stringResource(R.string.first_coach_title)) {
        Text(stringResource(R.string.first_coach_body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.settlement_open))
        }
    }
}

/**
 * 첫 활동 XP 축하 (#211) — 사용자의 움직임이 원장에 처음 쌓인 순간 1회.
 *
 * '만났다 → 자랐다' 루프의 닫는 절반이다. 이 카드가 증명하려는 건 하나뿐이다:
 * **네 움직임이 이 아이를 자라게 했다.** 그래서 수치가 아니라 캐릭터가 주인공이고,
 * 소급분으로는 절대 뜨지 않는다([com.nexus.core.FirstSession] 기준선).
 *
 * dismiss는 visible 토글 — 노드를 즉시 제거하면 exit 연출이 생략된다(#61 패턴).
 */
@Composable
internal fun FirstXpCard(fallbackSpriteState: String, visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 축하엔 자랑스러운 표정이 맞다 — 아트(#66)가 없으면 기존 폴백(idle/walk)을 그대로 쓴다
    val assets = remember(context) { CharacterAssets(context) }
    val spriteState = remember(assets, fallbackSpriteState) {
        if (assets.frameResIdOrNull(PROUD_FACE, 0) != null) PROUD_FACE else fallbackSpriteState
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = NexusMotion.CelebrationSpring) + fadeIn(),
        exit = fadeOut(),
    ) {
        NexusCard(emphasis = CardEmphasis.Highlight, title = stringResource(R.string.first_xp_title)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.md),
            ) {
                CharacterComposer.CharacterSprite(
                    state = spriteState,
                    modifier = Modifier.size(NexusSpacing.inlineSprite),
                )
                Text(stringResource(R.string.first_xp_body), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settlement_open))
            }
        }
    }
}

/**
 * 첫 세션 안내 판정 (#211) — 기준선을 세우고 같은 로드에서 바로 판정한다.
 *
 * ## 세 가지 안전장치
 *
 * 1. **자격**([OnboardingStore.firstSessionEligible]): 이번 릴리스 이후 온보딩을 마친 사용자만.
 *    몇 주째 쓰던 알파 테스터에게 "첫 성장까지, 10분"은 거짓말이다.
 * 2. **기준선 지연**([awaitingFirstData]): 흡수할 이력이 아직 도착하지 않았으면 기준선을 잡지 않는다.
 *    0으로 박아두면 30~60분 뒤 도착한 지난 28일치가 통째로 '증가분'으로 보인다(#44 소급분).
 * 3. **오늘 활동 조건**(core [FirstSession.cue]): 그래도 새는 경우를 위해, 축하는 오늘 움직임이
 *    있을 때만. 지난 날짜의 소급분은 오늘 활동 XP를 올리지 못한다.
 *
 * 기준선은 한 번 박히면 덮어쓰지 않는다 — 다시 잡으면 그 사이의 첫 활동이 소급분으로 오인된다.
 */
internal fun resolveFirstSessionCue(
    store: OnboardingStore,
    lifetimeXp: Int,
    todayXp: Int,
    awaitingFirstData: Boolean,
): FirstSessionCue {
    if (!store.firstSessionEligible) return FirstSessionCue.None

    val baseline = if (store.firstXpBaselineXp == FirstSession.NO_BASELINE) {
        // 아직 아무것도 안 왔으면 흡수할 게 없다 — 다음 로드에서 다시 시도한다
        if (awaitingFirstData) return FirstSessionCue.None
        store.firstXpBaselineXp = lifetimeXp
        lifetimeXp // 세운 값으로 이번 로드부터 판정 — 코치를 한 번 미루면 '첫 방문'을 놓친다
    } else {
        store.firstXpBaselineXp
    }

    return FirstSession.cue(
        baselineXp = baseline,
        lifetimeXp = lifetimeXp,
        todayActivityXp = todayXp,
        coachShown = store.firstCoachShown,
        celebrated = store.firstXpCelebrated,
    )
}
