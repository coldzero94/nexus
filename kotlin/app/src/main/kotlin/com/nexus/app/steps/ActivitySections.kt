package com.nexus.app.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.health.ExerciseSummary
import com.nexus.app.health.TokenStore
import com.nexus.app.ui.NexusDividedList
import com.nexus.app.ui.NexusListRow
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.TrustTierChip
import com.nexus.core.ActivityType
import com.nexus.core.TrustExplainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 활동 탭의 표시 전용 렌더 (#311·#260) — 세션 목록·행·라벨·동기화 푸터.
 *
 * `ActivityScreen.kt`는 로드 상태 라우팅과 섹션 구획을 맡고, 여기는 값을 받아 그리는 일만 한다.
 */
@Composable
internal fun SessionsSection(sessions: List<ExerciseSummary>) {
    if (sessions.isEmpty()) {
        Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val dtPattern = stringResource(R.string.session_datetime_format)
    val dtFormatter = remember(dtPattern) { DateTimeFormatter.ofPattern(dtPattern, Locale.KOREAN) }

    // 행 사이에만 구분선 — 마지막 뒤에 넣으면 카드 아래 여백과 겹쳐 두 겹 경계로 보인다 (#260)
    NexusDividedList(sessions) { session -> SessionRow(session, dtFormatter) }
}

/**
 * 세션 한 줄 (#260) — 공용 [NexusListRow] 규격.
 *
 * 값 슬롯에 **운동 시간**을 놓는다. 이전에는 종류·분·심박이 한 문장으로 이어져 있어서 목록을
 * 훑을 때 시간을 세로로 비교할 수 없었다 — 목록에서 눈이 찾는 건 "얼마나 했나"다.
 * 종류·심박은 보조 줄로 내리고, 신뢰 등급 칩은 라벨 아래 본문 슬롯에 남긴다(탭 대상이라 값 자리에
 * 두면 우측 정렬 열을 깨뜨린다).
 */
@Composable
private fun SessionRow(session: ExerciseSummary, dtFormatter: DateTimeFormatter) {
    val zone = remember { ZoneId.systemDefault() }
    val whenLabel = remember(session.start) { session.start.atZone(zone).format(dtFormatter) }
    val hrLabel = session.avgHeartRate?.let { stringResource(R.string.session_hr_format, it) }
        ?: stringResource(R.string.session_no_hr)

    NexusListRow(
        label = whenLabel,
        supporting = stringResource(R.string.session_meta_format, typeLabel(session.type), hrLabel),
        value = stringResource(R.string.session_duration_format, session.durationMinutes),
    ) {
        // 등급은 탭하면 '왜 이 등급인지' 설명 (#222) — 근거는 이 세션의 실제 판정 입력에서 도출.
        // 간격은 레이아웃이 준다(리소스 앞뒤 공백은 aapt2가 제거해 라벨이 붙어버린다, #222 리뷰)
        Row(
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrustTierChip(
                tier = session.trustTier,
                reason = TrustExplainer.reasonFor(
                    recordingMethod = session.recordingMethod,
                    dataOrigin = session.dataOrigin,
                    hasHeartRate = session.avgHeartRate != null,
                ),
            )
            Text(
                text = stringResource(R.string.session_source_suffix, sourceLabel(session.dataOrigin)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
internal fun syncFooter(store: TokenStore): String {
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
