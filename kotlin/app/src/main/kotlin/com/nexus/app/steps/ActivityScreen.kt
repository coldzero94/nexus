package com.nexus.app.steps

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.health.DailySteps
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.ExerciseSummary
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.StepRepository
import com.nexus.app.health.TokenStore
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.ActivityType
import com.nexus.core.TrustTier
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ActivityScreen"
private const val WINDOW_DAYS = 7

/**
 * 실데이터 활동 화면 (#7 걸음 + #8 운동 세션·동기화 상태). 실제 홈은 E4에서 대체.
 * HC 미가용/오류 시 에러 문구만 — 크래시 없음.
 */
@Composable
fun ActivityScreen(manager: HealthConnectManager, modifier: Modifier = Modifier, onReconnect: (() -> Unit)? = null) {
    val context = LocalContext.current
    val stepRepo = remember { manager.stepRepositoryOrNull() }
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val store = remember { TokenStore(context) }

    var load by remember { mutableStateOf<ActivityLoad?>(null) }

    LaunchedEffect(stepRepo, exerciseRepo) {
        // 권한 문제는 실패가 아닌 미연결 안내로 (#152, #144 패턴)
        load = if (stepRepo == null || exerciseRepo == null) {
            ActivityLoad.PermissionDenied
        } else {
            loadActivity(stepRepo, exerciseRepo)
        }
    }
    val current = load
    val data = (current as? ActivityLoad.Success)?.data

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexusSpacing.screen),
    ) {
        // ── 걸음 (#7) ──
        Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.steps_subtitle), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        when (current) {
            null -> Text(stringResource(R.string.steps_loading), style = MaterialTheme.typography.bodyMedium)

            ActivityLoad.PermissionDenied ->
                ConnectNotice(onReconnect, body = stringResource(R.string.activity_demo_body))

            ActivityLoad.Failure ->
                Text(stringResource(R.string.steps_error), style = MaterialTheme.typography.bodyMedium)

            is ActivityLoad.Success -> {
                val pattern = stringResource(R.string.steps_date_format)
                val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
                current.data.steps.asReversed().forEach { day ->
                    StepRow(dateLabel = day.date.format(formatter), steps = day.steps)
                }
                if (current.data.manualSteps > 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.steps_manual_excluded, current.data.manualSteps),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── 운동 세션 (#8) ──
        Text(stringResource(R.string.sessions_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (data != null) SessionsSection(data.sessions)

        Spacer(Modifier.height(28.dp))

        // ── 동기화 상태 (#8) ──
        Text(text = syncFooter(store), style = MaterialTheme.typography.bodySmall)
    }
}

private data class ActivityData(
    val steps: List<DailySteps>,
    val manualSteps: Long,
    val sessions: List<ExerciseSummary>,
)

/** 활동 로드 결과 (#152) — 권한 문제는 미연결 안내, [Failure]만 steps_error (#144 패턴). */
private sealed interface ActivityLoad {
    data class Success(val data: ActivityData) : ActivityLoad

    data object PermissionDenied : ActivityLoad

    data object Failure : ActivityLoad
}

/**
 * 활동 데이터 로드 — 실패 시 로그 후 Failure(#130 침묵 실패 제거, 구체 예외).
 * SecurityException(권한 회수)만 미연결 안내로 라우팅(#152, #144 패턴).
 */
private suspend fun loadActivity(stepRepo: StepRepository, exerciseRepo: ExerciseRepository): ActivityLoad = try {
    ActivityLoad.Success(
        ActivityData(
            steps = stepRepo.readDailySteps(WINDOW_DAYS),
            manualSteps = stepRepo.readManualStepCount(WINDOW_DAYS),
            sessions = exerciseRepo.readRecentSessions(WINDOW_DAYS),
        ),
    )
} catch (e: CancellationException) {
    throw e // 코루틴 취소는 전파
} catch (e: IOException) {
    Log.w(TAG, "activity load IO failure", e)
    ActivityLoad.Failure
} catch (e: RemoteException) {
    Log.w(TAG, "activity load remote failure", e)
    ActivityLoad.Failure
} catch (e: SecurityException) {
    Log.w(TAG, "activity load permission failure", e)
    ActivityLoad.PermissionDenied
} catch (e: IllegalArgumentException) {
    // HC ERROR_INVALID_ARGUMENT 또는 서드파티 이상 레코드의 변환 require 실패 (#130 재감사)
    Log.w(TAG, "activity load invalid-argument failure", e)
    ActivityLoad.Failure
} catch (e: IllegalStateException) {
    Log.w(TAG, "activity load state failure", e)
    ActivityLoad.Failure
}

@Composable
private fun SessionsSection(sessions: List<ExerciseSummary>) {
    if (sessions.isEmpty()) {
        Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.bodyMedium)
    } else {
        val dtPattern = stringResource(R.string.session_datetime_format)
        val dtFormatter = remember(dtPattern) { DateTimeFormatter.ofPattern(dtPattern, Locale.KOREAN) }
        sessions.forEach { session -> SessionRow(session, dtFormatter) }
    }
}

@Composable
private fun StepRow(dateLabel: String, steps: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dateLabel, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.steps_count_format, steps),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SessionRow(session: ExerciseSummary, dtFormatter: DateTimeFormatter) {
    val zone = remember { ZoneId.systemDefault() }
    val whenLabel = remember(session.start) { session.start.atZone(zone).format(dtFormatter) }
    val typeLabel = typeLabel(session.type)
    val hrLabel = session.avgHeartRate?.let { stringResource(R.string.session_hr_format, it) }
        ?: stringResource(R.string.session_no_hr)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(whenLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.session_meta_format, typeLabel, session.durationMinutes) + " · " + hrLabel,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.session_trust_source_format,
                tierLabel(session.trustTier),
                sourceLabel(session.dataOrigin),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun sourceLabel(packageName: String): String = when (packageName) {
    "com.sec.android.app.shealth" -> stringResource(R.string.source_samsung_health)
    "com.samsung.android.wear.shealth" -> stringResource(R.string.source_samsung_watch)
    else -> packageName
}

@Composable
private fun typeLabel(type: ActivityType?): String = stringResource(
    when (type) {
        ActivityType.WALKING -> R.string.session_type_walking
        ActivityType.RUNNING -> R.string.session_type_running
        ActivityType.STRENGTH -> R.string.session_type_strength
        null -> R.string.session_type_other
    },
)

@Composable
private fun tierLabel(tier: TrustTier): String = stringResource(
    when (tier) {
        TrustTier.A -> R.string.trust_tier_a
        TrustTier.B -> R.string.trust_tier_b
        TrustTier.C -> R.string.trust_tier_c
    },
)

@Composable
private fun syncFooter(store: TokenStore): String {
    val millis = store.lastSyncEpochMillis
    return if (millis <= 0L) {
        stringResource(R.string.sync_never)
    } else {
        val pattern = stringResource(R.string.sync_time_format)
        val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
        val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
        stringResource(R.string.sync_footer_format, time, store.lastChangeCount)
    }
}
