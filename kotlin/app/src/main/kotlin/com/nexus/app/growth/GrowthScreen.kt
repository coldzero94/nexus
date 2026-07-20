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
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.ConnectNotice
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
internal data class GrowthChange(val levelUpTo: Int?, val affinityChangedTo: ClassAffinity?)

/** 성장 탭 화면 상태 — 요약과 오늘 XP 분해는 같은 세션 스냅샷에서 계산(불일치 방지). */
internal data class GrowthUiState(val summary: GrowthSummary, val today: DayXpExplanation)

/**
 * 성장 로드 결과 (#144): 권한 문제는 "실패"가 아니라 미연결 상태 — 에러 문구 대신
 * 데모 안내로 라우팅한다. [Failure]만 growth_error를 쓴다(#130 catch 경로).
 */
internal sealed interface GrowthLoad {
    data class Success(val state: GrowthUiState) : GrowthLoad

    data object PermissionDenied : GrowthLoad

    data object Failure : GrowthLoad
}

@Composable
fun GrowthScreen(manager: HealthConnectManager, modifier: Modifier = Modifier, onReconnect: (() -> Unit)? = null) {
    val context = LocalContext.current
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val ledger = remember { RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao()) }
    val stateStore = remember { GrowthStateStore(context) }
    var load by remember { mutableStateOf<GrowthLoad?>(null) }
    var change by remember { mutableStateOf<GrowthChange?>(null) }
    var badges by remember { mutableStateOf<BadgeState?>(null) }
    var celebrationVisible by remember { mutableStateOf(true) }

    LaunchedEffect(exerciseRepo) {
        // HC 미가용(repo null)과 권한 회수(SecurityException)는 같은 "미연결" 안내로 (#144)
        val loaded = if (exerciseRepo == null) GrowthLoad.PermissionDenied else loadGrowth(exerciseRepo, ledger)
        load = loaded
        if (loaded is GrowthLoad.Success) {
            change = detectChange(stateStore, loaded.state.summary)
            // 기준점 소비는 "확인"(dismiss) 시점 — 감지 시점에 갱신하면 회전·프로세스 사망으로
            // 카드가 영영 소실된다(#61 리뷰). 변화가 없을 때만 여기서 기준점을 세팅(최초 방문 포함).
            if (change == null) stateStore.recordSeen(loaded.state.summary.level, loaded.state.summary.affinity)
            // 배지는 부가 정보 — 실패해도 성장 화면은 그대로(loadBadges가 null 반환) (#175)
            badges = loadBadges(context, manager, cumulativeXp = loaded.state.summary.totalXp)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.growth_title), style = MaterialTheme.typography.headlineSmall)
        when (val current = load) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            GrowthLoad.PermissionDenied ->
                ConnectNotice(
                    onReconnect,
                    body = stringResource(R.string.growth_demo_body, ClassAffinityCalculator.WINDOW_DAYS),
                )

            GrowthLoad.Failure ->
                Text(stringResource(R.string.growth_error), style = MaterialTheme.typography.bodyMedium)

            is GrowthLoad.Success -> {
                change?.let { c ->
                    // dismiss는 visible 토글 — 노드를 즉시 제거하면 exit 연출이 생략된다
                    CelebrationCard(c, visible = celebrationVisible) {
                        celebrationVisible = false
                        // 확인한 순간이 기준점 — 재진입 시 같은 변화를 다시 축하하지 않는다
                        stateStore.recordSeen(current.state.summary.level, current.state.summary.affinity)
                    }
                }
                GrowthContent(current.state)
                badges?.let { BadgesCard(it) }
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

/**
 * ActivityScreen.loadActivity와 같은 catch 계약 (#130 — 실패는 드러내고 취소는 전파).
 * 단 SecurityException은 실패가 아닌 [GrowthLoad.PermissionDenied]로 — 권한 회수는
 * 데모 안내+재연결 유도가 맞다(#144).
 */
private suspend fun loadGrowth(repo: ExerciseRepository, ledger: RewardLedgerRepository): GrowthLoad = try {
    val zone = ZoneId.systemDefault()
    val raw = repo.readRecentSessions(days = ClassAffinityCalculator.WINDOW_DAYS)
    // 화면 로드도 원장을 최신으로(멱등) — 워커 주기를 기다리지 않고 표시가 원장과 일치 (#163)
    ledger.grantSessions(raw, zone, epochMillis = System.currentTimeMillis())
    val sessions = raw.map {
        SessionInput(
            type = it.type,
            minutes = it.durationMinutes.toInt(),
            tier = it.trustTier,
            // 일일 상한 그룹핑 키 — 사용자 시간대 기준 날짜 (GrowthCalculator KDoc)
            epochDay = it.start.atZone(zone).toLocalDate().toEpochDay(),
        )
    }
    // 누적 XP·레벨은 전 기간 원장 합산 (#163) — 28일 창 이탈로 레벨이 내려가던 v1 한계 해소.
    // 성향·능력치·오늘 분해는 스펙대로 28일 창 유지.
    val ledgerTotal = ledger.cappedTotalXp()
    val summary = GrowthCalculator.compute(sessions)
    GrowthLoad.Success(
        GrowthUiState(
            summary = summary.copy(
                totalXp = ledgerTotal,
                level = LevelCurve.displayLevel(ledgerTotal),
                progress = LevelCurve.progressToNextLevel(ledgerTotal),
            ),
            today = XpExplainer.explainDay(sessions, epochDay = LocalDate.now(zone).toEpochDay()),
        ),
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "growth load IO failure", e)
    GrowthLoad.Failure
} catch (e: RemoteException) {
    Log.w(TAG, "growth load remote failure", e)
    GrowthLoad.Failure
} catch (e: SecurityException) {
    Log.w(TAG, "growth load permission failure", e)
    GrowthLoad.PermissionDenied
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "growth load invalid-argument failure", e)
    GrowthLoad.Failure
} catch (e: IllegalStateException) {
    Log.w(TAG, "growth load state failure", e)
    GrowthLoad.Failure
} catch (e: android.database.SQLException) {
    // 원장 DB 문제(디스크·손상)는 화면 크래시 대신 에러 표시 (#163)
    Log.w(TAG, "growth ledger db failure", e)
    GrowthLoad.Failure
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
