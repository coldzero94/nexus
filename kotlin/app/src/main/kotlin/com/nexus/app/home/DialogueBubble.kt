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
import com.nexus.core.GreetingVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 캐릭터 대사 말풍선 (#29·#212) — 채택 기분의 대사 풀 우선, 비면 상태별 기본 풀 폴백. 반복 회피로
 * 한 줄. 대사는 코드가 아닌 assets JSON(데이터 테이블)이라 하드코딩 문자열 규칙의 대상이 아니다.
 *
 * @param override 있으면 이 줄을 대신 보여준다 — 쓰다듬기 반응(#217)처럼 사용자의 행동에 즉답할 때.
 *   상시 대사를 밀어내는 게 아니라 잠깐 덮는 것이라, 사라지는 시점은 호출자가 정한다.
 */
@Composable
internal fun DialogueBubble(
    spriteState: String,
    moodLines: List<String>,
    override: String? = null,
    greeting: GreetingVariant = GreetingVariant.None,
) {
    val context = LocalContext.current
    var line by remember(spriteState, moodLines, greeting) { mutableStateOf<String?>(null) }
    LaunchedEffect(spriteState, moodLines, greeting) {
        line = withContext(Dispatchers.IO) {
            val pool = CharacterAssets(context).loadDialoguePool()
            // 맥락 인사(#220)가 잡히면 그 풀이 먼저 — 기분 대사(#212)보다 "지금 나를 알아본" 쪽이 앞선다.
            // 풀 키가 JSON에 없으면 linesOrDefault가 기본 상태로 떨어져 조용히 기존 동작이 된다.
            val candidates = greeting.poolKey()
                ?.let { pool.lines[it] }
                ?: moodLines.ifEmpty { pool.linesOrDefault(spriteState) }
            val memory = DialogueMemory(context)
            val picked = DialogueSelector.pick(candidates, memory.recent, Random.nextInt(candidates.size))
            memory.recent = DialogueSelector.remember(memory.recent, picked, DialogueMemory.RECENT_CAPACITY)
            picked
        }
    }
    (override ?: line)?.let {
        Text(
            text = stringResource(R.string.home_dialogue_format, it),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 변주 → `dialogue.json` 풀 키 (#220). 대사 추가·수정은 JSON만(코드 무수정). */
private fun GreetingVariant.poolKey(): String? = when (this) {
    GreetingVariant.None -> null
    GreetingVariant.Morning -> "greeting_morning"
    GreetingVariant.Evening -> "greeting_evening"
    GreetingVariant.FirstActivityToday -> "greeting_active_today"
    GreetingVariant.BackAfterShortGap -> "greeting_back_short"
}
