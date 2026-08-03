package com.nexus.app.home

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.CharacterComposer
import com.nexus.app.character.loadAnniversaries
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.celebrationEnter
import com.nexus.core.Anniversaries
import com.nexus.core.Anniversary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "Anniversary"

/**
 * 함께한 N일 기념일 축하 (#111, E5-16).
 *
 * ## 왜 축하할 값어치가 있나
 *
 * 레벨·배지는 **한 일**에 대한 보상이라 안 하면 안 온다. 기념일은 **그냥 시간이 흘렀다는 사실**이라
 * 노력과 무관하게 온다 — 게임화 동기가 수 주 내에 약해지는 구간(RESEARCH.md)에서, 성취 축과
 * 별개로 붙잡아 주는 게 이 축의 역할이다. 그래서 "며칠 운동했어요"를 절대 적지 않는다.
 * 그걸 적는 순간 성취 축이 되고, 못 한 사람에게는 축하가 아니라 성적표가 된다.
 *
 * ## 캐릭터를 함께 그린다
 *
 * 이 카드의 화자는 앱이 아니라 캐릭터다. 본문이 캐릭터의 말투로 쓰여 있는데 그림이 없으면
 * 누가 하는 말인지 모른다.
 */
@Composable
internal fun AnniversaryCard(anniversary: Anniversary?, visible: Boolean, onDismiss: () -> Unit) {
    // 비었을 때 일찍 반환하지 않는다 — 노드가 visible=true인 채로 들어오면 등장 전환이 생략된다.
    // 퇴장 중에는 값이 이미 null이라 마지막 값을 붙잡아 둔다 (#218과 같은 이유).
    val shown = remember { mutableStateOf(anniversary) }
    if (anniversary != null) shown.value = anniversary

    AnimatedVisibility(
        visible = visible && anniversary != null,
        enter = celebrationEnter(),
        exit = fadeOut(),
    ) {
        val current = shown.value ?: return@AnimatedVisibility
        NexusCard(
            // 축하가 나타났음을 낭독한다 (#224) — 시각 채널에만 남으면 축하가 도달하지 않는다
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            emphasis = CardEmphasis.Celebration,
            title = current.title,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NexusSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CharacterComposer.CharacterSprite(
                    state = "proud_sparkle",
                    modifier = Modifier.size(NexusSpacing.heroSprite),
                )
                Text(
                    text = current.body,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.celebrate_dismiss))
                }
            }
        }
    }
}

/**
 * 지금 띄울 기념일 — 없으면 null (#111).
 *
 * 표 로드 실패는 카드 미노출로 끝낸다: 기념일은 부가 연출이라 홈 전체를 막을 이유가 없다
 * (형제 로더들과 같은 catch 계약, #130).
 */
internal suspend fun loadAnniversary(
    context: Context,
    ledger: RewardLedgerRepository,
    todayEpochDay: Long,
    store: TogetherStore = TogetherStore(context),
): Anniversary? = try {
    val table = withContext(Dispatchers.IO) { CharacterAssets(context).loadAnniversaries() }
    val firstMet = withContext(Dispatchers.IO) {
        store.firstMetEpochDay(ledgerFirstEpochDay = ledger.firstEpochDay(), todayEpochDay = todayEpochDay)
    }
    Anniversaries.pendingAt(
        table = table,
        daysTogether = Anniversaries.daysTogether(firstMet, todayEpochDay),
        celebratedDays = withContext(Dispatchers.IO) { store.celebratedDays },
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "anniversary table IO failure", e)
    null
} catch (e: IllegalStateException) {
    Log.w(TAG, "anniversary state failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "anniversary table invalid", e)
    null
}
