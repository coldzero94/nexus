package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import com.nexus.app.R
import com.nexus.app.health.HealthSyncWorker
import com.nexus.app.health.TokenStore
import com.nexus.app.ui.NexusCard
import com.nexus.core.SyncFreshness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

/** 신선도 표시 갱신 주기 — "N분 전"이 화면에 머무는 동안 굳지 않게 (#221). */
private const val TICK_MILLIS = 60_000L

/**
 * 데이터 신선도 + 수동 새로고침 (#221, E14-11).
 *
 * 워커가 실패·지연돼도 화면은 경고 없이 오래된 값을 보여준다. 마지막 반영 시각을 드러내
 * "새 기록이 왜 안 보이지"를 사용자가 스스로 진단할 수 있게 하고, 오래됐을 때만 지연 안내를 붙인다.
 *
 * 톤 원칙 둘: ① 사용자를 꾸짖지 않는다(지연은 HC·기기 사정이다) ② **실시간을 약속하지 않는다**
 * (삼성헬스 → HC 전파에 30~60분 — 제품 불변식 ⑤). '지금 확인'은 HC에 이미 들어온 것을 당길 뿐이다.
 *
 * 미동기화(lastSync==0)면 아무것도 그리지 않는다 — 첫 연결 안내는 빈 상태 티켓(#213)의 몫이다.
 *
 * @param onSyncFinished 수동 동기화가 끝났을 때. 화면 수치를 다시 읽지 않으면 이 줄만 "방금 반영했어요"로
 *   바뀌고 위쪽 XP·걸음은 옛 값 그대로 남아, **오래된 숫자에 최신 딱지를 붙이는** 더 나쁜 상태가 된다.
 */
@Composable
internal fun FreshnessRow(onSyncFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }

    // Flow는 호출마다 새 인스턴스라 remember 없이는 리컴포지션마다 구독이 끊겼다 재생성된다
    val manualFlow = remember(context) { HealthSyncWorker.manualSyncFlow(context) }
    val manualInfos by manualFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val running = manualInfos.any { it.isInFlight() }
    val failed = manualInfos.lastOrNull()?.state == WorkInfo.State.FAILED

    // 최초 1회는 동기 읽기 — produceState의 초기값(Never)으로 한 프레임 비웠다 그리면 스크롤이 튄다
    var freshness by remember {
        mutableStateOf(SyncFreshness.evaluate(store.lastSyncEpochMillis, System.currentTimeMillis()))
    }

    // 워크 상태가 바뀔 때마다 다시 읽는다. `running`의 true→false 전이에 걸면, 워크가 즉시 끝나
    // ENQUEUED를 한 번도 관측하지 못한 경우(HC 미가용 등) 갱신 신호를 통째로 놓친다.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(manualInfos, lifecycle) {
        // 포그라운드에서만 tick — 백그라운드에서 1분마다 깨울 이유가 없고, 복귀 시 즉시 한 번 읽는다
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val last = withContext(Dispatchers.IO) { store.lastSyncEpochMillis }
                freshness = SyncFreshness.evaluate(last, System.currentTimeMillis())
                delay(TICK_MILLIS)
            }
        }
    }

    // 사용자가 누른 시점의 워크 id들 — 이후 여기 없던 id가 완료되면 "이번에 누른 것"이 끝난 것이다.
    // 단순히 "완료된 워크가 있으면"으로 잡으면 지난 세션에 끝난 워크 탓에 홈 진입마다 헛읽기가 돈다.
    var baselineIds by remember { mutableStateOf<Set<UUID>?>(null) }
    val newlyFinished = baselineIds?.let { base ->
        manualInfos.any { it.state.isFinished && it.id !in base }
    } == true

    // 완료된 수동 동기화는 원장을 갱신했을 수 있다 — 화면 수치도 함께 다시 읽는다
    LaunchedEffect(newlyFinished) {
        if (newlyFinished) {
            baselineIds = null
            onSyncFinished()
        }
    }

    val onCheckNow = {
        baselineIds = manualInfos.mapTo(mutableSetOf()) { it.id }
        HealthSyncWorker.enqueueNow(context)
    }

    val synced = freshness as? SyncFreshness.Synced ?: return
    when {
        failed -> FailureNotice(synced, running, onCheckNow, modifier)
        synced.delayed -> DelayedNotice(synced, running, onCheckNow, modifier)
        else -> FreshLine(synced, running, onCheckNow, modifier)
    }
}

/**
 * '진행 중'으로 볼 상태 (#221) — RUNNING이거나 **첫 시도 대기**인 것만.
 *
 * 재시도로 되돌아온 ENQUEUED(runAttemptCount > 0)까지 진행 중으로 치면, 백오프가 최대 5시간까지
 * 늘어나는 동안 버튼이 "확인 중…"으로 굳어 눌리지 않는다. 사용자에겐 고장으로 보인다.
 */
private fun WorkInfo.isInFlight(): Boolean = when (state) {
    WorkInfo.State.RUNNING -> true
    WorkInfo.State.ENQUEUED -> runAttemptCount == 0
    else -> false
}

/** 최신에 가까울 때 — 조용한 한 줄. 카드로 키우면 아무 문제 없을 때도 시선을 뺏는다. */
@Composable
private fun FreshLine(synced: SyncFreshness.Synced, running: Boolean, onCheckNow: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            elapsedText(synced),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RefreshButton(running, onCheckNow)
    }
}

/** 임계(3시간)를 넘겼을 때 — 안심 안내를 카드로. 원인을 밝히되 사용자를 탓하지 않는다. */
@Composable
private fun DelayedNotice(synced: SyncFreshness.Synced, running: Boolean, onCheckNow: () -> Unit, modifier: Modifier) {
    NexusCard(modifier = modifier, title = elapsedText(synced)) {
        Text(
            stringResource(R.string.freshness_delay_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RefreshButton(running, onCheckNow)
    }
}

/**
 * 수동 확인이 실패했을 때 (#221) — 침묵하면 "왜 안 보이지"에 답을 못 한다.
 *
 * 권한 회수가 가장 흔한 원인인데, 그 경우 홈 본문은 이미 [com.nexus.app.ui.ConnectNotice]로
 * 재연결을 안내하고 있다. 여기선 "확인이 실패했다"는 사실만 덧붙이고 버튼은 살려 둔다.
 */
@Composable
private fun FailureNotice(synced: SyncFreshness.Synced, running: Boolean, onCheckNow: () -> Unit, modifier: Modifier) {
    NexusCard(modifier = modifier, title = elapsedText(synced)) {
        Text(
            stringResource(R.string.freshness_check_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RefreshButton(running, onCheckNow)
    }
}

@Composable
private fun RefreshButton(running: Boolean, onCheckNow: () -> Unit) {
    // 실행 중엔 비활성 — WorkManager의 KEEP이 중복을 막지만 눌리는 버튼은 "안 먹는다"로 읽힌다
    TextButton(onClick = onCheckNow, enabled = !running) {
        Text(stringResource(if (running) R.string.freshness_checking else R.string.freshness_check_now))
    }
}

/** 경과 표기 — 1분 미만은 "방금", 1시간 미만은 분, 그 이상은 시간. */
@Composable
private fun elapsedText(synced: SyncFreshness.Synced): String = when {
    synced.minutesAgo < 1 -> stringResource(R.string.freshness_just_now)
    synced.hoursAgo < 1 -> stringResource(R.string.freshness_minutes_ago, synced.minutesAgo)
    else -> stringResource(R.string.freshness_hours_ago, synced.hoursAgo)
}
