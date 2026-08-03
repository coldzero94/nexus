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
 * 세션을 읽는 **두 곳**이 각각 굴린다: 동기화 워커(#112 — 15분마다, 탭을 안 열어도 쌓인다)와
 * 성장 로드. 결과가 세션 id의 순수 함수라(core [StoryDropPicker]) 같은 세션을 몇 번 다시 읽어도
 * 조각이 늘지 않기 때문에 두 곳에서 굴려도 안전하다 — 그게 이 기능이 성립하는 조건이다.
 *
 * 워커만 굴리게 하지 않는 이유: 워커 창은 7일이고 화면 창은 28일이라, 워커가 놓친 기간(앱을
 * 오래 안 켠 사용자)을 화면 로드가 메운다.
 */
internal suspend fun loadStoryCodex(context: Context, sessionIds: List<String>): StoryCodexState? = try {
    val (table, store) = withContext(Dispatchers.IO) {
        CharacterAssets(context).loadStoryFragments() to StoryCollectionStore(context)
    }
    // 세션마다 굴린다 — 같은 세션은 항상 같은 답이라 재동기화에 안전하다
    val dropped = sessionIds.mapNotNull { StoryDropPicker.drop(it, table, DROP_PERCENT) }
    // 대기 집합은 워커가 미리 채워둘 수 있다 — 여기서 새로 얻은 것만 보면 백그라운드 획득을 놓친다
    val (collected, pending) = withContext(Dispatchers.IO) {
        store.collect(dropped.map { it.id }.toSet())
        store.collected to store.pending
    }
    StoryCodexState(
        collected = table.fragments.filter { it.id in collected },
        total = table.fragments.size,
        newlyFound = table.fragments.filter { it.id in pending },
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG_CODEX, "story codex IO failure", e)
    null
} catch (e: IllegalStateException) {
    // 형제 로더(loadBadges·loadMilestones)와 같은 계약 — 여기서 새면 async 스코프가 통째로
    // 취소돼 배지·이달·마일스톤까지 함께 사라진다
    Log.w(TAG_CODEX, "story codex state failure", e)
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

/**
 * 드롭 확률 — 운동 다섯 번에 한 번쯤.
 *
 * 매번 주면 발견이 아니라 배급이다: 도감이 며칠 만에 다 차고 그 뒤로는 운동해도 아무 일도
 * 안 일어난다. `internal`인 이유는 이 값이 표시가 아니라 **체감 리듬**을 정하기 때문에
 * 테스트가 실제 드롭 빈도를 직접 재야 하기 때문이다.
 */
internal const val DROP_PERCENT = 20

private const val TAG_CODEX = "StoryCodex"
