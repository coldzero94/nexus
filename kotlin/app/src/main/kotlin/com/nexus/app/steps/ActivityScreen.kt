package com.nexus.app.steps

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.crash.CrashReporting
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.DailySteps
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.ExerciseSummary
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.StepRepository
import com.nexus.app.health.TokenStore
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
import com.nexus.core.FailureCategory
import com.nexus.core.FirstRun
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ActivityScreen"
private const val WINDOW_DAYS = 7

/**
 * 실데이터 활동 화면 (#7 걸음 + #8 운동 세션·동기화 상태). 실제 홈은 E4에서 대체.
 * HC 미가용/오류 시 에러 문구만 — 크래시 없음.
 */
@Composable
internal fun ActivityScreen(
    manager: HealthConnectManager,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
    // 테스트가 로드 분기를 직접 세우기 위한 이음새 (#320) — 기본값은 프로덕션 조립 그대로.
    // Robolectric엔 Health Connect가 없어 repo가 항상 null이라, 주입 없이는 미연결 분기만 렌더된다.
    controller: ActivityUiController? = null,
) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val ui = controller ?: remember {
        ActivityUiController(
            context = context,
            stepRepo = manager.stepRepositoryOrNull(),
            exerciseRepo = manager.exerciseRepositoryOrNull(),
        )
    }
    LaunchedEffect(ui.reloadKey) { ui.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexusSpacing.screen),
    ) {
        // 첫 데이터 대기 중엔 빈 차트·빈 세션 목록 대신 '준비 중' 하나만 (#213)
        if (ui.data?.awaitingFirstData == true) {
            FirstRunNotice(onSyncFinished = ui::refreshAfterSync)
            return@Column
        }
        StepsSection(ui, onReconnect)
        Spacer(Modifier.height(NexusSpacing.xxl))
        // ── 운동 세션 (#8) ──
        Text(stringResource(R.string.sessions_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(NexusSpacing.md))
        ui.data?.let { SessionsSection(it.sessions) }
        Spacer(Modifier.height(NexusSpacing.xxl))
        // ── 동기화 상태 (#8) ──
        Text(text = syncFooter(store), style = MaterialTheme.typography.bodySmall)
    }
}

/** 걸음 섹션 (#7·#258) — 제목·부제·로드 분기·막대 차트. */
@Composable
private fun StepsSection(ui: ActivityUiController, onReconnect: (() -> Unit)?) {
    Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(NexusSpacing.xs))
    Text(stringResource(R.string.steps_subtitle), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(NexusSpacing.md))
    when (val current = ui.load) {
        null -> Text(stringResource(R.string.steps_loading), style = MaterialTheme.typography.bodyMedium)

        ActivityLoad.PermissionDenied ->
            ConnectNotice(onReconnect, body = stringResource(R.string.activity_demo_body))

        ActivityLoad.Failure -> RetryNotice(stringResource(R.string.steps_error), ui::retry)

        is ActivityLoad.Success -> {
            StepBarChart(current.data.steps)
            if (current.data.manualSteps > 0L) {
                Spacer(Modifier.height(NexusSpacing.sm))
                Text(
                    text = stringResource(R.string.steps_manual_excluded, current.data.manualSteps),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal data class ActivityData(
    val steps: List<DailySteps>,
    val manualSteps: Long,
    val sessions: List<ExerciseSummary>,
    /** 첫 데이터 대기 중 (#213) — 빈 차트·빈 세션 목록 대신 '준비 중'. */
    val awaitingFirstData: Boolean,
)

/** 활동 로드 결과 (#152) — 권한 문제는 미연결 안내, [Failure]만 steps_error (#144 패턴). */
internal sealed interface ActivityLoad {
    data class Success(val data: ActivityData) : ActivityLoad

    data object PermissionDenied : ActivityLoad

    data object Failure : ActivityLoad
}

/**
 * 활동 데이터 로드 — 실패 시 로그 후 Failure(#130 침묵 실패 제거, 구체 예외).
 * SecurityException(권한 회수)만 미연결 안내로 라우팅(#152, #144 패턴).
 */
internal suspend fun loadActivity(
    context: Context,
    stepRepo: StepRepository,
    exerciseRepo: ExerciseRepository,
): ActivityLoad = try {
    val ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao())
    val steps = stepRepo.readDailySteps(WINDOW_DAYS)
    val sessions = exerciseRepo.readRecentSessions(WINDOW_DAYS)
    ActivityLoad.Success(
        ActivityData(
            steps = steps,
            manualSteps = stepRepo.readManualStepCount(WINDOW_DAYS),
            sessions = sessions,
            // 걸음 막대가 하나라도 있으면 보여줄 게 있는 것 — 실데이터를 빈 상태로 가리면 안 된다
            awaitingFirstData = FirstRun.isAwaitingFirstData(
                lifetimeXp = ledger.cappedTotalXp(),
                hasAnyHealthData = steps.any { it.steps > 0L } || sessions.isNotEmpty(),
            ),
        ),
    )
} catch (e: CancellationException) {
    throw e // 코루틴 취소는 전파
} catch (e: IOException) {
    Log.w(TAG, "activity load IO failure", e)
    CrashReporting.recordHandledFailure(FailureCategory.ACTIVITY_LOAD)
    ActivityLoad.Failure
} catch (e: RemoteException) {
    Log.w(TAG, "activity load remote failure", e)
    CrashReporting.recordHandledFailure(FailureCategory.ACTIVITY_LOAD)
    ActivityLoad.Failure
} catch (e: SecurityException) {
    Log.w(TAG, "activity load permission failure", e)
    CrashReporting.recordHandledFailure(FailureCategory.ACTIVITY_LOAD)
    ActivityLoad.PermissionDenied
} catch (e: IllegalArgumentException) {
    // HC ERROR_INVALID_ARGUMENT 또는 서드파티 이상 레코드의 변환 require 실패 (#130 재감사)
    Log.w(TAG, "activity load invalid-argument failure", e)
    CrashReporting.recordHandledFailure(FailureCategory.ACTIVITY_LOAD)
    ActivityLoad.Failure
} catch (e: IllegalStateException) {
    Log.w(TAG, "activity load state failure", e)
    CrashReporting.recordHandledFailure(FailureCategory.ACTIVITY_LOAD)
    ActivityLoad.Failure
}
