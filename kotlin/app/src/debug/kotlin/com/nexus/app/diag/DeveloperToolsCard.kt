package com.nexus.app.diag

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.app.R
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.HealthSyncWorker
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.LedgerIntegrity
import com.nexus.core.LedgerRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 디버그 전용 개발자 도구 (#245, E15-12) — 관측·재현·리셋을 한 카드에 모았다.
 *
 * ## 왜 소스셋으로 갈랐나
 *
 * 이 파일은 `src/debug`에만 있다. `BuildConfig.DEBUG` 분기로 숨기면 코드와 문자열이 릴리스 APK에
 * **그대로 들어간다** — "원장 전체 삭제" 같은 버튼이 리버싱 가능한 형태로 실린다는 뜻이다.
 * 소스셋을 가르면 릴리스는 [NoopDeveloperToolsCard]를 컴파일하므로 문자열도 코드도 존재하지 않는다.
 * `DeveloperToolsGateTest`가 릴리스 진입점·문자열 부재를 고정한다.
 *
 * ## 시드는 합성 수치만
 *
 * 시드 버튼은 Health Connect를 건드리지 않는다. 원장에 **합성 XP 행**을 직접 넣는다 — 실제
 * 걸음·세션을 만들어내면 그게 곧 원장에 박제되는 건강 파생 값이 되고, 테스터 기기에서 실제
 * 활동과 구분되지 않는다. 키에 `synthetic-` 접두어를 박아 나중에도 식별된다.
 */
@Composable
internal fun DeveloperToolsCard(manager: HealthConnectManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var readout by remember { mutableStateOf<DevToolsReadout?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        readout = DevToolsReadout(
            rows = runCatching { DiagnosticsCollector.ledgerRows(context) }.getOrNull(),
            snapshot = runCatching {
                DiagnosticsCollector.renderText(context, manager, System.currentTimeMillis())
            }.getOrElse { it.message.orEmpty() },
        )
    }

    NexusCard(title = stringResource(R.string.dev_tools_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm), modifier = modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dev_tools_desc), style = MaterialTheme.typography.bodySmall)
            IntegrityReadout(readout?.rows)
            SnapshotReadout(readout?.snapshot)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            SyncWorkerRow(context)
            DevAction(R.string.dev_tools_refresh) { reload++ }
            // 자동 경로는 보고만 한다 — 크래시로 스택을 받고 싶을 때만 여기서 명시적으로 요청한다
            DevAction(R.string.dev_tools_verify_crash) {
                readout?.rows?.let { LedgerIntegrityGuard.verifyOrCrash(context, it) }
            }
            DevAction(R.string.dev_tools_snapshot_share) { shareSnapshot(context, readout?.snapshot) }

            SeedActions(context, scope) { message ->
                note = message
                reload++
            }
            ResetActions(context, scope) { message ->
                note = message
                reload++
            }
        }
    }
}

@Composable
private fun IntegrityReadout(rows: List<LedgerRow>?) {
    if (rows == null) {
        Text(stringResource(R.string.dev_tools_integrity_unread), style = MaterialTheme.typography.bodySmall)
        return
    }
    val violations = LedgerIntegrity.check(rows)
    val text = if (violations.isEmpty()) {
        stringResource(R.string.dev_tools_integrity_ok)
    } else {
        stringResource(R.string.dev_tools_integrity_violations, violations.joinToString { it.name })
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(R.string.dev_tools_recomputed_total, LedgerIntegrity.recomputeTotalXp(rows)),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SnapshotReadout(snapshot: String?) {
    Text(stringResource(R.string.dev_tools_snapshot_title), style = MaterialTheme.typography.titleSmall)
    Text(snapshot.orEmpty(), style = MaterialTheme.typography.bodySmall)
}

/**
 * 워커 1회 실행 + 결과 표시.
 *
 * `doWork`를 직접 부르지 않고 WorkManager에 넣는다 — 우리가 알고 싶은 건 함수의 반환값이 아니라
 * **WorkManager가 그 반환값을 어떻게 해석했는가**(성공/재시도/실패)이고, 제약·백오프가 붙은
 * 실제 경로에서만 재현되는 문제가 있다.
 */
@Composable
private fun SyncWorkerRow(context: Context) {
    // Flow는 호출마다 새 인스턴스라 remember 없이는 리컴포지션마다 구독이 끊겼다 재생성된다
    val flow = remember(context) { HealthSyncWorker.manualSyncFlow(context) }
    val infos by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val state = infos.lastOrNull()?.state?.name.orEmpty()

    Text(stringResource(R.string.dev_tools_sync_state, state), style = MaterialTheme.typography.bodySmall)
    DevAction(R.string.dev_tools_run_sync) { HealthSyncWorker.enqueueNow(context) }
}

@Composable
private fun SeedActions(context: Context, scope: CoroutineScope, onDone: (String) -> Unit) {
    val seeded = stringResource(R.string.dev_tools_seed_done, DevLedgerSeeder.SEED_DAYS)

    DevAction(R.string.dev_tools_seed) {
        scope.launch {
            DevLedgerSeeder.seedWeek(context)
            onDone(seeded)
        }
    }
    DevAction(R.string.dev_tools_seed_mixed_day) {
        scope.launch {
            DevLedgerSeeder.seedMixedFormulaDay(context)
            onDone(seeded)
        }
    }
    DevAction(R.string.dev_tools_seed_orphan) {
        scope.launch {
            DevLedgerSeeder.seedOrphanCancellation(context)
            onDone(seeded)
        }
    }
}

@Composable
private fun ResetActions(context: Context, scope: CoroutineScope, onDone: (String) -> Unit) {
    val cleared = stringResource(R.string.dev_tools_cleared)
    val tokenCleared = stringResource(R.string.dev_tools_token_cleared)

    DevAction(R.string.dev_tools_token_reset) {
        DevResets.resetChangesToken(context)
        onDone(tokenCleared)
    }
    DevAction(R.string.dev_tools_clear_ledger) {
        scope.launch {
            DevResets.clearLedger(context)
            onDone(cleared)
        }
    }
    DevAction(R.string.dev_tools_clear_sync) {
        DevResets.clearSyncState(context)
        onDone(cleared)
    }
    DevAction(R.string.dev_tools_clear_firsts) {
        DevResets.clearTelemetryFirsts(context)
        onDone(cleared)
    }
}

@Composable
private fun DevAction(labelRes: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes))
    }
}

/**
 * 스냅샷 공유 — `ACTION_SEND` 평문.
 *
 * 파일로 쓰지 않는 이유는 SAF·FileProvider 배관이 얻는 것보다 크고, 스냅샷이 20줄 미만의 평문이라
 * 카톡·이슈 댓글에 그대로 붙는 게 실제로 더 빠르기 때문이다.
 */
private fun shareSnapshot(context: Context, snapshot: String?) {
    if (snapshot.isNullOrBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.dev_tools_snapshot_subject))
        putExtra(Intent.EXTRA_TEXT, snapshot)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.dev_tools_snapshot_share)))
}
