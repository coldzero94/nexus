package com.nexus.app.growth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.loadStoryFragments
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusIcons
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.StoryDropPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 이야기 조각 도감 (#112, E5-14) — 운동이 물어온 조각을 모아 보는 곳.
 *
 * ## 왜 잠긴 조각을 나열하지 않는가
 *
 * 배지·마일스톤은 **목표**라 잠긴 항목을 보여줘야 한다("뭘 하면 열리지"). 조각은 목표가 아니라
 * **발견**이다 — 아직 못 본 조각의 제목을 미리 보여주면 발견이 체크리스트가 되고, 성장 탭은
 * 이미 잠긴 목록이 셋(배지·이달·마일스톤)이다. 여기서는 모은 것만 보여주고 남은 수만 알린다.
 *
 * ## 드롭은 어디서 굴러가는가
 *
 * 세션을 읽는 쪽(성장 로드)이 굴린다. 결과가 세션 id의 순수 함수라(core [StoryDropPicker])
 * 워커가 같은 세션을 몇 번 다시 읽어도 조각이 늘지 않는다 — 그게 이 기능이 성립하는 조건이다.
 */
internal suspend fun loadStoryCodex(context: Context, sessionIds: List<String>): StoryCodexState? = try {
    val (table, store) = withContext(Dispatchers.IO) {
        CharacterAssets(context).loadStoryFragments() to StoryCollectionStore(context)
    }
    // 세션마다 굴린다 — 같은 세션은 항상 같은 답이라 재동기화에 안전하다
    val dropped = sessionIds.mapNotNull { StoryDropPicker.drop(it, table, DROP_PERCENT) }
    val newlyIds = withContext(Dispatchers.IO) { store.collect(dropped.map { it.id }.toSet()) }
    val byId = table.fragments.associateBy { it.id }
    StoryCodexState(
        collected = table.fragments.filter { it.id in store.collected },
        total = table.fragments.size,
        newlyFound = newlyIds.mapNotNull { byId[it] },
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG_CODEX, "story codex IO failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG_CODEX, "story fragment table invalid", e)
    null
}

@Composable
internal fun StoryCodexCard(state: StoryCodexState, modifier: Modifier = Modifier) {
    NexusCard(
        modifier = modifier,
        titleIcon = NexusIcons.expedition,
        title = stringResource(R.string.growth_codex_title, state.collected.size, state.total),
    ) {
        if (state.collected.isEmpty()) {
            Text(
                text = stringResource(R.string.growth_codex_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@NexusCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.md)) {
            state.collected.forEach { fragment ->
                Column(
                    // 제목과 본문이 끊겨 들리면 본문이 무엇의 본문인지 알 수 없다 (#260 규칙)
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
                ) {
                    Text(fragment.title, style = MaterialTheme.typography.titleSmall)
                    Text(fragment.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 드롭 확률 — 운동 다섯 번에 한 번쯤. 너무 잦으면 발견이 아니라 배급이 된다. */
private const val DROP_PERCENT = 20

private const val TAG_CODEX = "StoryCodex"
