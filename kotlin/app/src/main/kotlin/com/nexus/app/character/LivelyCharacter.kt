package com.nexus.app.character

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nexus.app.R
import com.nexus.app.ui.LocalMotionScale
import com.nexus.app.ui.NexusMotion
import kotlinx.coroutines.launch

/** 숨쉬기 한 주기(ms) — 사람 호흡에 가깝게 느리게. 빠르면 초조해 보인다. */
private const val BREATH_PERIOD_MS = 2600

/** 숨쉬기 진폭 — 1.0 ↔ 이 값. 크면 '떠 있는' 느낌이라 아주 작게. */
private const val BREATH_SCALE = 1.025f

/** 첫 등장 팝의 시작 스케일 — 작게 나타나 제자리로. */
private const val POP_FROM_SCALE = 0.86f

/** 쓰다듬기 바운스의 눌림 스케일. */
private const val PET_PRESS_SCALE = 0.92f

/**
 * 생동감 있는 캐릭터 (#217, E14-7) — 상시 숨쉬기 + 첫 등장 팝 + 탭 반응.
 *
 * 홈 캐릭터는 2프레임 티커뿐이라 상시 미동이 없고 탭에 전혀 반응하지 않았다. 자체 리포트가 경계한
 * '정적 마스코트'다. 타마고치·Finch의 가장 기본적인 애착 훅이 '만지면 반응한다'와 상시 생동감이라,
 * **신규 아트 0장**으로 트랜스폼만 얹어 회복한다(스프라이트·위젯 합성 경로는 건드리지 않는다).
 *
 * ## 프레임마다 리컴포즈하지 않는다
 *
 * 숨쉬기 값은 [State]로 들고 **`graphicsLayer` 람다 안에서만** 읽는다. 컴포지션 스코프에서 읽으면
 * 무한 트랜지션이 매 프레임 이 스코프를 무효화하고, 하위 [CharacterComposer.CharacterSprite]가
 * `List` 파라미터 탓에 skippable이 아니라 `Resources.getIdentifier` 조회까지 매 프레임 다시 돈다.
 * 홈 최상단에서 상시 발생하므로 draw 단계에 가두는 게 필수다.
 *
 * 모션은 [LocalMotionScale]을 따른다 — 시스템에서 애니메이션을 끄면 정지 프레임으로 남는다.
 * 상시 미동은 전정기관 장애가 있는 사용자에게 증상을 유발할 수 있어 타협 대상이 아니다.
 *
 * @param onPet 반응이 **수락된** 탭(연타 억제 통과)에만 불린다. 대사 교체 등 화면 반응은 호출자 몫.
 */
@Composable
internal fun LivelyCharacter(
    state: String,
    modifier: Modifier = Modifier,
    equipLayers: List<String> = emptyList(),
    onPet: () -> Unit = {},
) {
    val motionScale = LocalMotionScale.current
    val animated = motionScale > 0f

    val breath = rememberBreathScale(motionScale)

    // 첫 등장 팝 + 탭 바운스를 한 Animatable로 — 둘이 각자 스케일을 잡으면 서로 덮어쓴다.
    // state를 키로 두지 않아 기분 변화로 스프라이트가 바뀌어도 다시 팝하지 않는다.
    val reaction = remember { Animatable(if (animated) POP_FROM_SCALE else 1f) }
    LaunchedEffect(animated) {
        if (animated) reaction.animateTo(1f, NexusMotion.CelebrationSpring) else reaction.snapTo(1f)
    }

    val scope = rememberCoroutineScope()
    val throttle = remember { PetReactionThrottle() }
    val haptics = LocalHapticFeedback.current
    val pressDuration = NexusMotion.scaledDuration(NexusMotion.DURATION_SHORT, motionScale)
    val label = stringResource(R.string.character_content_desc)

    Box(
        modifier = modifier
            // 스프라이트 로드 전에는 하위에 설명 노드가 없어 "버튼"으로만 읽힌다 — 바깥에서 보장한다
            .semantics { contentDescription = label }
            .graphicsLayer {
                val scale = breath.value * reaction.value
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = null, // 리플 없음 — 반응은 바운스·햅틱이 담당
                indication = null,
                onClickLabel = stringResource(R.string.character_pet_action),
                role = Role.Button,
            ) {
                // 벽시계는 NTP 보정으로 뒤로 갈 수 있다 — 그러면 연타 억제가 영구히 잠긴다
                if (!throttle.accept(android.os.SystemClock.uptimeMillis())) return@clickable
                // 모션이 꺼져 있어도 반응은 남긴다 — 시각 외 확인 수단이 하나는 있어야 한다
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPet()
                if (!animated) return@clickable
                scope.launch {
                    reaction.animateTo(PET_PRESS_SCALE, tween(pressDuration))
                    reaction.animateTo(1f, NexusMotion.CelebrationSpring)
                }
            },
    ) {
        CharacterComposer.CharacterSprite(
            state = state,
            modifier = Modifier.matchParentSize(),
            equipLayers = equipLayers,
        )
    }
}

/**
 * 숨쉬기 스케일 (#217) — [State]로 돌려주어 호출측이 draw 단계에서만 읽게 한다.
 * 컴포지션에서 읽으면 무한 트랜지션이 매 프레임 리컴포즈를 유발한다([LivelyCharacter] KDoc).
 */
@Composable
private fun rememberBreathScale(motionScale: Float): State<Float> {
    if (motionScale <= 0f) return remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "breath")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = BREATH_SCALE,
        animationSpec = infiniteRepeatable(
            // 스케일이 0.5배면 호흡도 그만큼 느려진다 — 부분 감속 설정을 이진값으로 뭉개지 않는다
            animation = tween(
                durationMillis = NexusMotion.scaledDuration(BREATH_PERIOD_MS, 1f / motionScale),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
}
