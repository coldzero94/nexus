package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer
import com.nexus.app.character.equipRenderLayers
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.ConditionEngine
import kotlin.math.roundToInt

/**
 * 홈 히어로 밴드 (#256, E16-6) — 캐릭터·대사·컨디션을 상단 전용 컨테이너로 묶어 화면의 최상위
 * 앵커로 만든다. 톤(surfaceContainerHigh)·큰 라운드로 아래 종속 카드(surfaceContainerLow)와
 * 위계를 벌린다. 정적 레이아웃 강조만 — 숨쉬기(#217)·앰비언스(#115)·축하(#219)와 비중복.
 */
@Composable
internal fun HomeHero(spriteState: String, moodLines: List<String>, condition: Double) {
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
            CharacterComposer.CharacterSprite(
                state = spriteState,
                modifier = Modifier.size(140.dp),
                equipLayers = equipLayers,
            )
            DialogueBubble(spriteState, moodLines)
            ConditionStrip(condition)
        }
    }
}

/** 컨디션 스트립 (#256) — 히어로 안의 인라인 게이지(별도 카드 없이). 소프트 손실(20~100). */
@Composable
private fun ConditionStrip(condition: Double) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_condition_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_condition_value, condition.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LinearProgressIndicator(
            progress = { (condition / ConditionEngine.MAX).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
