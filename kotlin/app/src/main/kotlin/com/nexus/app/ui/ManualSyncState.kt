package com.nexus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.nexus.app.health.HealthSyncWorker
import java.util.UUID

/**
 * '지금 확인'의 공유 상태 (#221·#213) — 신선도 줄과 첫 실행 빈 상태가 같은 동작을 쓴다.
 *
 * 각자 배선하면 한쪽에만 완료 후 갱신이 붙는 식으로 어긋난다(실제로 #213 리뷰에서 빈 상태의 버튼이
 * 눌러도 아무 반응이 없는 막다른 길이었다). 눌렀을 때·도는 중일 때·끝났을 때·실패했을 때를 한 곳에서 정한다.
 */
internal class ManualSyncState(val running: Boolean, val failed: Boolean, val checkNow: () -> Unit)

/**
 * @param onSyncFinished 사용자가 누른 동기화가 끝났을 때. 화면 수치를 다시 읽지 않으면 눌러도
 *   아무것도 바뀌지 않아 버튼이 고장으로 읽힌다.
 */
@Composable
internal fun rememberManualSync(onSyncFinished: () -> Unit): ManualSyncState {
    val context = LocalContext.current
    // Flow는 호출마다 새 인스턴스라 remember 없이는 리컴포지션마다 구독이 끊겼다 재생성된다
    val flow = remember(context) { HealthSyncWorker.manualSyncFlow(context) }
    val infos by flow.collectAsStateWithLifecycle(initialValue = emptyList())

    // 누른 시점의 워크 id들 — 이후 여기 없던 id가 완료되면 "이번에 누른 것"이 끝난 것이다.
    // 단순히 "완료된 워크가 있으면"으로 잡으면 지난 세션에 끝난 워크 탓에 진입마다 헛읽기가 돈다.
    var baselineIds by remember { mutableStateOf<Set<UUID>?>(null) }
    val newlyFinished = baselineIds?.let { base ->
        infos.any { it.state.isFinished && it.id !in base }
    } == true

    LaunchedEffect(newlyFinished) {
        if (newlyFinished) {
            baselineIds = null
            onSyncFinished()
        }
    }

    return ManualSyncState(
        running = infos.any { it.isInFlight() },
        failed = infos.lastOrNull()?.state == WorkInfo.State.FAILED,
        checkNow = {
            baselineIds = infos.mapTo(mutableSetOf()) { it.id }
            HealthSyncWorker.enqueueNow(context)
        },
    )
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
