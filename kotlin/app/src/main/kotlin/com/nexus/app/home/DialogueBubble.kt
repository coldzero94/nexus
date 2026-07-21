package com.nexus.app.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.core.DialogueSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 캐릭터 대사 말풍선 (#29·#212) — 채택 기분의 대사 풀 우선, 비면 상태별 기본 풀 폴백. 반복 회피로
 * 한 줄. 대사는 코드가 아닌 assets JSON(데이터 테이블)이라 하드코딩 문자열 규칙의 대상이 아니다.
 */
@Composable
internal fun DialogueBubble(spriteState: String, moodLines: List<String>) {
    val context = LocalContext.current
    var line by remember(spriteState, moodLines) { mutableStateOf<String?>(null) }
    LaunchedEffect(spriteState, moodLines) {
        line = withContext(Dispatchers.IO) {
            val candidates = moodLines.ifEmpty {
                CharacterAssets(context).loadDialoguePool().linesOrDefault(spriteState)
            }
            val memory = DialogueMemory(context)
            val picked = DialogueSelector.pick(candidates, memory.recent, Random.nextInt(candidates.size))
            memory.recent = DialogueSelector.remember(memory.recent, picked, DialogueMemory.RECENT_CAPACITY)
            picked
        }
    }
    line?.let {
        Text(
            text = stringResource(R.string.home_dialogue_format, it),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
