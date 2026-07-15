package com.nexus.app.growth

import android.os.RemoteException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.core.ActivityType
import com.nexus.core.ClassAffinity
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.DayXpExplanation
import com.nexus.core.GrowthCalculator
import com.nexus.core.GrowthSummary
import com.nexus.core.LevelCurve
import com.nexus.core.SessionInput
import com.nexus.core.Stat
import com.nexus.core.StatMapping
import com.nexus.core.XpEngine
import com.nexus.core.XpExplainer
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "GrowthScreen"

/** 성장 변화 연출 대상 (#61) — 성장 탭 진입 시 기준점([GrowthStateStore])과의 차이. */
internal data class GrowthChange(val levelUpTo: Int?, val affinityChangedTo: com.nexus.core.ClassAffinity?)

/** 성장 탭 화면 상태 — 요약과 오늘 XP 분해는 같은 세션 스냅샷에서 계산(불일치 방지). */
internal data class GrowthUiState(val summary: GrowthSummary, val today: DayXpExplanation)

@Composable
fun GrowthScreen(manager: HealthConnectManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val stateStore = remember { GrowthStateStore(context) }
    var data by remember { mutableStateOf<GrowthUiState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var change by remember { mutableStateOf<GrowthChange?>(null) }
    var celebrationVisible by remember { mutableStateOf(true) }

    LaunchedEffect(exerciseRepo) {
        val loaded = if (exerciseRepo == null) null else loadGrowth(exerciseRepo)
        data = loaded
        if (loaded != null) {
            change = detectChange(stateStore, loaded.summary)
            // 기준점은 감지 직후 갱신 — 탭 재진입 시 같은 변화를 두 번 축하하지 않는다 (#61)
            stateStore.recordSeen(loaded.summary.level, loaded.summary.affinity)
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.growth_title), style = MaterialTheme.typography.headlineSmall)
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            data == null -> Text(stringResource(R.string.growth_error), style = MaterialTheme.typography.bodyMedium)

            else -> {
                change?.let { c ->
                    // dismiss는 visible 토글 — 노드를 즉시 제거하면 exit 연출이 생략된다
                    CelebrationCard(c, visible = celebrationVisible) { celebrationVisible = false }
                }
                GrowthContent(data!!)
            }
        }
    }
}

/**
 * 기준점 대비 변화 감지 (#61): 레벨업은 상승만(최초 방문·창 이탈로 인한 하락은 무연출),
 * 성향 변화는 기준점이 있을 때만. 변화 없으면 null(카드 미노출).
 */
private fun detectChange(store: GrowthStateStore, summary: GrowthSummary): GrowthChange? {
    val lastLevel = store.lastSeenLevel
    val lastAffinity = store.lastSeenAffinity
    val levelUpTo = summary.level.takeIf { lastLevel in 1 until it }
    val affinityChangedTo = summary.affinity.takeIf { lastAffinity != null && lastAffinity != it }
    if (levelUpTo == null && affinityChangedTo == null) return null
    return GrowthChange(levelUpTo, affinityChangedTo)
}

/** ActivityScreen.loadActivity와 같은 catch 계약 (#130 — 실패는 드러내고 취소는 전파). */
private suspend fun loadGrowth(repo: ExerciseRepository): GrowthUiState? = try {
    val zone = ZoneId.systemDefault()
    val sessions = repo.readRecentSessions(days = ClassAffinityCalculator.WINDOW_DAYS).map {
        SessionInput(
            type = it.type,
            minutes = it.durationMinutes.toInt(),
            tier = it.trustTier,
            // 일일 상한 그룹핑 키 — 사용자 시간대 기준 날짜 (GrowthCalculator KDoc)
            epochDay = it.start.atZone(zone).toLocalDate().toEpochDay(),
        )
    }
    GrowthUiState(
        summary = GrowthCalculator.compute(sessions),
        today = XpExplainer.explainDay(sessions, epochDay = LocalDate.now(zone).toEpochDay()),
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "growth load IO failure", e)
    null
} catch (e: RemoteException) {
    Log.w(TAG, "growth load remote failure", e)
    null
} catch (e: SecurityException) {
    Log.w(TAG, "growth load permission failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "growth load invalid-argument failure", e)
    null
} catch (e: IllegalStateException) {
    Log.w(TAG, "growth load state failure", e)
    null
}

@Composable
private fun GrowthContent(data: GrowthUiState) {
    TodayXpCard(data.today)
    LevelCard(data.summary)
    AffinityCard(data.summary)
    StatsCard(data.summary)
    Text(
        stringResource(R.string.growth_scope_note, ClassAffinityCalculator.WINDOW_DAYS),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LevelCard(data: GrowthSummary) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(R.string.growth_level_format, data.level),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { data.progress.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.growth_xp_format, data.totalXp, XpEngine.FORMULA_VERSION),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AffinityCard(data: GrowthSummary) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.growth_affinity_format, stringResource(data.affinity.labelRes())),
                style = MaterialTheme.typography.titleMedium,
            )
            ActivityType.entries.forEach { type ->
                ShareRow(stringResource(type.labelRes()), data.axisShares[type] ?: 0.0)
            }
        }
    }
}

@Composable
private fun ShareRow(label: String, share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { share.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatsCard(data: GrowthSummary) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.growth_stats_title), style = MaterialTheme.typography.titleMedium)
            StatMapping.unlockedStats.forEach { stat ->
                StatRow(stringResource(stat.labelRes()), data.stats[stat] ?: 0)
            }
            StatMapping.lockedStats.forEach { stat ->
                Text(
                    stringResource(R.string.growth_stat_locked_format, stringResource(stat.labelRes())),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.growth_stat_value_format, value),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
