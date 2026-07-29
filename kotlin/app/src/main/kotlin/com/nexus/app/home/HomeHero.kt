package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.LivelyCharacter
import com.nexus.app.character.equipRenderLayers
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.DialogueSelector
import com.nexus.core.GreetingVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 홈 히어로 밴드 (#256, E16-6) — 캐릭터·대사·컨디션을 상단 전용 컨테이너로 묶어 화면의 최상위
 * 앵커로 만든다. 톤(surfaceContainerHigh)·큰 라운드로 아래 종속 카드(surfaceContainerLow)와
 * 위계를 벌린다. 정적 레이아웃 강조만 — 숨쉬기(#217)·앰비언스(#115)·축하(#219)와 비중복.
 */
@Composable
internal fun HomeHero(
    spriteState: String,
    moodLines: List<String>,
    condition: Double,
    restMode: Boolean,
    greeting: GreetingVariant = GreetingVariant.None,
) {
    // 장착 장비를 본체 위에 반영 (#37) — 카탈로그 로드 실패 시 본체만(빈 레이어)
    val context = LocalContext.current
    val equipLayers by produceState(emptyList<String>(), spriteState) {
        value = equipRenderLayers(context, spriteState)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(NexusSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.md),
        ) {
            // 생동감 (#217) — 숨쉬기·첫 등장 팝·탭 반응. 스프라이트 자체는 그대로다(신규 아트 0).
            // 대사 풀은 미리 한 번만 읽는다 — 탭 순간에 assets를 열면 바운스가 시작되는 프레임이 끊긴다.
            val petPool by produceState(emptyList<String>()) {
                value = withContext(Dispatchers.IO) {
                    CharacterAssets(context).loadDialoguePool().linesOrDefault(PET_STATE)
                }
            }
            // 반복 회피 기록은 상시 대사(DialogueMemory)와 분리 — 섞으면 짧은 즉답이 상시 대사를 밀어낸다
            var petRecent by remember { mutableStateOf(emptyList<String>()) }
            var petLine by remember { mutableStateOf<String?>(null) }
            // 같은 대사가 다시 뽑혀도 표시 시간이 갱신되도록 nonce를 함께 키로 쓴다
            var petNonce by remember { mutableIntStateOf(0) }

            LivelyCharacter(
                state = spriteState,
                modifier = Modifier.size(NexusSpacing.heroSprite),
                equipLayers = equipLayers,
                onPet = {
                    if (petPool.isNotEmpty()) {
                        val picked = DialogueSelector.pick(petPool, petRecent, Random.nextInt(petPool.size))
                        petRecent = DialogueSelector.remember(petRecent, picked, PET_RECENT_CAPACITY)
                        petLine = picked
                        petNonce++
                    }
                },
            )
            // 반응 대사는 잠깐 덮었다 사라진다 — 상시 대사를 영구히 밀어내면 기분(#212) 표현이 죽는다
            LaunchedEffect(petLine, petNonce) {
                if (petLine != null) {
                    delay(PET_LINE_HOLD_MILLIS)
                    petLine = null
                }
            }
            DialogueBubble(spriteState, moodLines, override = petLine, greeting = greeting)
            // 컨디션 게이지 (#257) — 바닥·3존 커스텀 시각화(스톡 프로그레스 대체)
            ConditionGaugeBar(condition, restMode)
        }
    }
}

/** 반응 대사가 화면에 머무는 시간 — 읽고 넘어갈 만큼만. 길면 상시 대사를 가린다. */
private const val PET_LINE_HOLD_MILLIS = 2600L

/** 반응 대사 풀의 상태 키 — `dialogue.json`에 추가하면 코드 수정 없이 반영된다 (#217). */
private const val PET_STATE = "pet"

/** 반응 대사 반복 회피 기억 크기 — 풀(5줄)보다 작아야 항상 뽑을 후보가 남는다. */
private const val PET_RECENT_CAPACITY = 3
