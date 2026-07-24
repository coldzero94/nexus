package com.nexus.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.LevelCurve
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.ZoneId

/**
 * 초기 레벨 부여 연출 (#44, E7-3) — READ_HEALTH_DATA_HISTORY로 지난 28일을 소급 지급하고
 * "이미 이만큼 성장해 있었어요"를 보여준다. 이력이 없으면(레벨 1·XP 0) 조용히 건너뛴다.
 * 소급 지급은 원장 멱등 진입점(grantSessions) 재사용 — 성장 탭 합계와 어긋날 수 없다.
 */
@Composable
fun InitialLevelScene(manager: HealthConnectManager, onDone: (markShown: Boolean) -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (level, totalXp)

    LaunchedEffect(Unit) {
        val repo = manager.exerciseRepositoryOrNull()
        if (repo == null) {
            onDone(false) // HC 미가용 — 다음 실행에서 재시도(무비용 스킵)
            return@LaunchedEffect
        }
        val ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao())
        val total = backfillOrNull(repo, ledger)
        if (total == null) {
            // 일시 실패 — 연출을 영구 포기하지 않고 다음 실행에서 재시도(#44 리뷰 F1)
            onDone(false)
            return@LaunchedEffect
        }
        val level = LevelCurve.displayLevel(total)
        if (level < MIN_LEVEL_TO_CELEBRATE) {
            onDone(true) // 이력이 적어 레벨 1이면 연출 생략 확정 — "이미 레벨 1"은 자랑이 아니다
        } else {
            result = level to total
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val loaded = result
        if (loaded == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(NexusSpacing.lg))
            Text(stringResource(R.string.initial_level_loading), style = MaterialTheme.typography.bodyMedium)
        } else {
            CharacterComposer.CharacterSprite(state = "walk", modifier = Modifier.size(160.dp))
            Spacer(Modifier.height(NexusSpacing.xl))
            Text(
                stringResource(R.string.initial_level_title, loaded.first),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(NexusSpacing.md))
            Text(
                stringResource(R.string.initial_level_body, loaded.second),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(NexusSpacing.xxl))
            Button(onClick = { onDone(true) }) {
                Text(stringResource(R.string.initial_level_continue))
            }
        }
    }
}

/** 소급 지급 + 합산 — 실패는 로그 후 null(#130 계약: 구체 catch, 취소 전파). */
private suspend fun backfillOrNull(repo: ExerciseRepository, ledger: RewardLedgerRepository): Int? = try {
    ledger.grantSessions(
        sessions = repo.readRecentSessions(days = ClassAffinityCalculator.WINDOW_DAYS),
        zone = ZoneId.systemDefault(),
        epochMillis = System.currentTimeMillis(),
    )
    ledger.cappedTotalXp()
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    android.util.Log.w(TAG, "backfill IO failure", e)
    null
} catch (e: android.os.RemoteException) {
    android.util.Log.w(TAG, "backfill remote failure", e)
    null
} catch (e: SecurityException) {
    android.util.Log.w(TAG, "backfill permission failure", e)
    null
} catch (e: IllegalArgumentException) {
    android.util.Log.w(TAG, "backfill invalid-argument failure", e)
    null
} catch (e: IllegalStateException) {
    android.util.Log.w(TAG, "backfill state failure", e)
    null
} catch (e: android.database.SQLException) {
    android.util.Log.w(TAG, "backfill db failure", e)
    null
}

private const val TAG = "InitialLevelScene"

/** 연출 최소 레벨 — 신규와 같은 레벨 1로는 "이미 성장" 서사가 성립하지 않는다. */
private const val MIN_LEVEL_TO_CELEBRATE = 2
