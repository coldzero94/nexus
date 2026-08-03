package com.nexus.app.steps

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.nexus.app.ui.ActivitySkeleton
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
import com.nexus.app.ui.StaggerItem
import com.nexus.core.FailureCategory
import com.nexus.core.FirstRun
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ActivityScreen"
private const val WINDOW_DAYS = 7

/**
 * 활동 화면 (#7 걸음 + #8 운동 세션·동기화 상태).
 *
 * ## 섹션 구조 (#260)
 *
 * 걸음·운동·동기화를 **각각 카드로 구획**한다([ActivitySections]). 이전에는 셋이 한 Column에 맨
 * 텍스트로 이어져 있고 사이를 수동 `Spacer(28/12/4dp)`가 벌려서, 4탭 중 유일하게 밀도와 리듬이
 * 달랐다 — 어디까지가 한 섹션인지 경계가 없어 전체가 하나의 텍스트 덤프로 읽혔다.
 *
 * 세로 리듬은 홈·성장·설정과 **같은 스케일**(`spacedBy(lg)` + `padding(screen)`)을 쓴다. 수동 Spacer를
 * 없앤 이유가 그것이다: 화면마다 손으로 값을 고르면 같은 앱 안에서 탭을 옮길 때 간격이 튄다.
 *
 * 로드 분기(로딩·미연결·실패·첫 데이터 대기)도 홈·성장과 같이 **화면 Column의 형제**로 둔다 —
 * 이유는 [ActivitySections] KDoc.
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
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg),
    ) {
        when (val current = ui.load) {
            null -> ActivitySkeleton()

            ActivityLoad.PermissionDenied ->
                ConnectNotice(onReconnect, body = stringResource(R.string.activity_demo_body))

            ActivityLoad.Failure -> RetryNotice(stringResource(R.string.steps_error), ui::retry)

            // 첫 데이터 대기 중엔 빈 차트·빈 세션 목록 대신 '준비 중' 하나만 (#213)
            is ActivityLoad.Success if current.data.awaitingFirstData ->
                FirstRunNotice(onSyncFinished = ui::refreshAfterSync)

            is ActivityLoad.Success -> ActivitySections(current.data, store)
        }
    }
}

/**
 * 세 섹션 (#260) — 걸음·운동·동기화. **성공 분기에서만** 그린다.
 *
 * 미연결·실패 안내를 이 카드들 안에 넣지 않는 게 중요하다. `ConnectNotice`·`RetryNotice`가 이미
 * [NexusCard]라서 섹션 카드 안에 넣으면 **같은 색 카드가 두 겹**으로 겹치고 제목도 두 개가 되어
 * 렌더 오류처럼 보인다. 홈·성장도 안내를 화면 Column의 형제로 둔다 — 같은 배치를 쓴다.
 *
 * 데이터가 없을 때 섹션 카드를 그리지 않는 이유도 같다: 제목만 있고 내용이 빈 카드는 빈 상태가
 * 아니라 고장으로 읽힌다.
 */
@Composable
private fun ActivitySections(data: ActivityData, store: TokenStore) {
    StaggerItem(0) {
        NexusCard(title = stringResource(R.string.steps_title)) {
            Text(stringResource(R.string.steps_subtitle), style = MaterialTheme.typography.bodySmall)
            StepBarChart(data.steps)
            if (data.manualSteps > 0L) {
                Text(
                    text = stringResource(R.string.steps_manual_excluded, data.manualSteps),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    StaggerItem(1) {
        NexusCard(title = stringResource(R.string.sessions_title)) {
            SessionsSection(data.sessions)
        }
    }
    StaggerItem(2) {
        NexusCard(title = stringResource(R.string.sync_title)) {
            Text(text = syncFooter(store), style = MaterialTheme.typography.bodySmall)
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
