package com.nexus.app.home

import android.os.RemoteException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.CharacterComposer
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.StepRepository
import com.nexus.app.settings.RestModeStore
import com.nexus.app.ui.ConnectNotice
import com.nexus.core.ConditionEngine
import com.nexus.core.DialogueSelector
import com.nexus.core.SessionInput
import com.nexus.core.XpEngine
import com.nexus.core.XpExplainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

private const val TAG = "HomeScreen"

/** 컨디션 파생 창 — 원장 배선 전의 표시 전용 폴드 범위 (ConditionEngine.fromDailyPoints). */
private const val CONDITION_WINDOW_DAYS = 28

/** 홈 상태 (#32, E4-8) — 3초 내 파악할 것들만. */
internal data class HomeUiState(
    val condition: Double,
    val todayXp: Int,
    val todayActiveMinutes: Int,
    val todaySteps: Long,
)

internal sealed interface HomeLoad {
    data class Success(val state: HomeUiState) : HomeLoad

    data object PermissionDenied : HomeLoad

    data object Failure : HomeLoad
}

/** 홈 (#32) — 캐릭터·컨디션·오늘 요약·다음 목표. 원정 상태는 E5에서 실데이터로. */
@Composable
fun HomeScreen(manager: HealthConnectManager, modifier: Modifier = Modifier, onReconnect: (() -> Unit)? = null) {
    val context = LocalContext.current
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val stepRepo = remember { manager.stepRepositoryOrNull() }
    var load by remember { mutableStateOf<HomeLoad?>(null) }

    val restStore = remember { RestModeStore(context) }
    LaunchedEffect(exerciseRepo, stepRepo) {
        load = if (exerciseRepo == null || stepRepo == null) {
            HomeLoad.PermissionDenied
        } else {
            loadHome(exerciseRepo, stepRepo, restStore)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val current = load) {
            null -> CircularProgressIndicator()

            HomeLoad.PermissionDenied -> ConnectNotice(onReconnect)

            HomeLoad.Failure ->
                Text(stringResource(R.string.home_error), style = MaterialTheme.typography.bodyMedium)

            is HomeLoad.Success -> HomeContent(current.state)
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState) {
    // 오늘 움직였으면 걷는 모습 — 데이터가 캐릭터에 그대로 새겨진다는 감각(임시 규칙, 기분 표는 E4-4)
    val spriteState = if (state.todayActiveMinutes > 0) "walk" else "idle"
    CharacterComposer.CharacterSprite(state = spriteState, modifier = Modifier.size(140.dp))
    DialogueBubble(spriteState)
    ConditionGauge(state.condition)
    TodaySummaryCard(state)
    ExpeditionPlaceholderCard()
    Text(
        text = nextGoalText(state),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun nextGoalText(state: HomeUiState): String = when {
    state.todayActiveMinutes < ACTIVE_GOAL_MINUTES ->
        stringResource(R.string.home_goal_move, ACTIVE_GOAL_MINUTES)

    state.condition < ConditionEngine.DEFAULT ->
        stringResource(R.string.home_goal_recovering)

    else -> stringResource(R.string.home_goal_done)
}

/** 다음 목표 문구의 활동 기준(분) — 컨디션 활동 문턱(10pt≈걷기 10분)과 맞춘다. */
private const val ACTIVE_GOAL_MINUTES = 10

/**
 * 캐릭터 대사 말풍선 (#29, E4-5) — 상태별 풀에서 반복 회피로 한 줄. 대사는 코드가 아닌
 * assets JSON(데이터 테이블)이라 하드코딩 문자열 규칙의 대상이 아니다 — 수정 = JSON만.
 */
@Composable
private fun DialogueBubble(spriteState: String) {
    val context = LocalContext.current
    var line by remember(spriteState) { mutableStateOf<String?>(null) }
    LaunchedEffect(spriteState) {
        line = withContext(Dispatchers.IO) {
            val candidates = CharacterAssets(context).loadDialoguePool().linesOrDefault(spriteState)
            val memory = DialogueMemory(context)
            val picked = DialogueSelector.pick(candidates, memory.recent, Random.nextInt(candidates.size))
            memory.recent = DialogueSelector.remember(memory.recent, picked, DialogueMemory.RECENT_CAPACITY)
            picked
        }
    }
    line?.let {
        Text(
            text = stringResource(R.string.home_dialogue_format, it),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** ActivityScreen.loadActivity와 같은 catch 계약 (#130) + 권한 회수는 안내로 (#144 패턴). */
private suspend fun loadHome(
    exerciseRepo: ExerciseRepository,
    stepRepo: StepRepository,
    restStore: RestModeStore,
): HomeLoad = try {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val sessions = exerciseRepo.readRecentSessions(days = CONDITION_WINDOW_DAYS).map {
        SessionInput(
            type = it.type,
            minutes = it.durationMinutes.toInt(),
            tier = it.trustTier,
            epochDay = it.start.atZone(zone).toLocalDate().toEpochDay(),
        )
    }
    // 컨디션은 코스메틱 — Tier C 포함 기본점수로 일자별 폴드 (ConditionEngine KDoc)
    val pointsByDay = sessions
        .filter { it.type != null }
        .groupBy { it.epochDay }
        .mapValues { (_, day) -> day.sumOf { XpEngine.baseScore(it.type!!, it.minutes).toDouble() } }
    val windowDays = (CONDITION_WINDOW_DAYS - 1 downTo 0).map { offset ->
        pointsByDay[today.minusDays(offset.toLong()).toEpochDay()] ?: 0.0
    }
    // 첫 기록일 이전은 "무활동"이 아니라 "데이터 없음" — 거기서부터 폴드해야 신규 사용자가
    // 빈 창 28일치 하락(≈바닥)으로 시작하지 않는다. 기록이 전혀 없으면 기본값.
    val firstRecordedIdx = windowDays.indexOfFirst { it > 0.0 }
    val condition = if (firstRecordedIdx == -1) {
        ConditionEngine.DEFAULT
    } else {
        // 휴식 모드(#31): 휴식 시작일 이후의 날은 하락 면제 — 일자별로 restMode를 넣어 폴드
        windowDays.withIndex().drop(firstRecordedIdx).fold(ConditionEngine.DEFAULT) { acc, (idx, points) ->
            val epochDay = today.minusDays((CONDITION_WINDOW_DAYS - 1 - idx).toLong()).toEpochDay()
            ConditionEngine.nextDay(acc, points, restMode = restStore.isRestDay(epochDay))
        }
    }
    val todayEpoch = today.toEpochDay()
    HomeLoad.Success(
        HomeUiState(
            condition = condition,
            todayXp = XpExplainer.explainDay(sessions, epochDay = todayEpoch).cappedXp,
            todayActiveMinutes = sessions
                .filter { it.epochDay == todayEpoch && it.type != null }
                .sumOf { it.minutes },
            todaySteps = stepRepo.readDailySteps(days = 1)
                .firstOrNull { it.date == today }?.steps ?: 0L,
        ),
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "home load IO failure", e)
    HomeLoad.Failure
} catch (e: RemoteException) {
    Log.w(TAG, "home load remote failure", e)
    HomeLoad.Failure
} catch (e: SecurityException) {
    Log.w(TAG, "home load permission failure", e)
    HomeLoad.PermissionDenied
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "home load invalid-argument failure", e)
    HomeLoad.Failure
} catch (e: IllegalStateException) {
    Log.w(TAG, "home load state failure", e)
    HomeLoad.Failure
}
