package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.XpGauge
import com.nexus.core.EnergyEngine
import com.nexus.core.ExpeditionState
import kotlin.math.roundToInt

/**
 * 오늘의 모험 일지 (#70, E5-9) — 저녁에 하루를 서사로 닫는 카드(Pikmin 일기 패턴).
 * 활동이 있으면 성장 서사, 없으면 쉼 서사(무처벌).
 */
@Composable
internal fun EveningJournalCard(state: HomeUiState, onDismiss: () -> Unit) {
    NexusCard(title = stringResource(R.string.journal_title)) {
        Text(
            if (state.todayXp > 0) {
                stringResource(
                    R.string.journal_body_active,
                    state.todayActiveMinutes,
                    state.todaySteps,
                    state.todayXp,
                )
            } else {
                stringResource(R.string.journal_body_rest)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        DismissRow(R.string.journal_dismiss, onDismiss)
    }
}

/**
 * 아침 요약 카드 (#36, E5-3) — "간밤의 회복 + 어제의 성장", 하루 1회.
 * 무활동이었던 어제는 꾸짖지 않는다(무처벌) — 쉼도 리듬으로 프레이밍.
 */
@Composable
internal fun MorningCard(state: HomeUiState, onDismiss: () -> Unit) {
    NexusCard(emphasis = CardEmphasis.Celebration, title = stringResource(R.string.morning_title)) {
        Text(
            // XP 기준 분기 — 수기(Tier C)만 있던 어제는 "+0 XP" 병치 대신 쉼 프레이밍(#36 리뷰 N2)
            if (state.yesterdayXp > 0) {
                stringResource(R.string.morning_body_active, state.yesterdayXp, state.yesterdayActiveMinutes)
            } else {
                stringResource(R.string.morning_body_rest)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.morning_condition, state.condition.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
        )
        DismissRow(R.string.morning_dismiss, onDismiss)
    }
}

/**
 * 정산 개봉 카드 (#35, E5-2) — "새 활동 반영 시 XP 정산 의식"(코어 루프 3단계).
 * 동기화 지연으로 늦게 도착한 성장을 문제 대신 선물로 프레이밍한다.
 */
@Composable
internal fun SettlementCard(deltaXp: Int, onOpen: () -> Unit) {
    NexusCard(emphasis = CardEmphasis.Highlight, title = stringResource(R.string.settlement_title)) {
        Text(
            stringResource(R.string.settlement_body, deltaXp),
            style = MaterialTheme.typography.bodyMedium,
        )
        DismissRow(R.string.settlement_open, onOpen)
    }
}

/** 오늘 요약 — XP·운동 분·걸음 한 줄씩 (#32). */
@Composable
internal fun TodaySummaryCard(state: HomeUiState) {
    NexusCard(title = stringResource(R.string.home_today_title)) {
        SummaryRow(
            stringResource(R.string.home_today_xp),
            stringResource(R.string.home_today_xp_value, state.todayXp),
        )
        // 오늘 XP 진행 게이지 (#259) — 성장 탭과 동일 컴포넌트 재사용, XP 수치 바로 아래
        XpGauge(state.todayXp)
        SummaryRow(
            stringResource(R.string.home_today_exercise),
            stringResource(R.string.home_today_exercise_value, state.todayActiveMinutes),
        )
        SummaryRow(
            stringResource(R.string.home_today_steps),
            stringResource(R.string.home_today_steps_value, state.todaySteps),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 카드 하단 우측 정렬 액션 버튼 행 — 일지·아침·정산 카드가 공유. */
@Composable
private fun DismissRow(labelRes: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) {
            Text(stringResource(labelRes))
        }
    }
}

/**
 * 원정 카드 (#34·#67) — 출발(에너지 소모)/진행(남은 시간)/개봉. 동기화 지연 흡수 연출의 무대:
 * "모험에서 돌아오는 중" 프레임(MVP §1 — 실시간 약속 금지). 보상·개봉 연출은 E5-7.
 */
@Composable
internal fun ExpeditionCard(expedition: ExpeditionState, energy: Int, onDepart: () -> Unit, onOpen: () -> Unit) {
    NexusCard(
        title = stringResource(R.string.home_expedition_title),
        trailing = {
            Text(
                stringResource(R.string.home_energy_format, energy),
                style = MaterialTheme.typography.titleMedium,
            )
        },
    ) {
        when (expedition) {
            ExpeditionState.Idle -> {
                Text(
                    stringResource(R.string.expedition_idle_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onDepart,
                    enabled = energy >= EnergyEngine.EXPEDITION_COST,
                ) {
                    Text(stringResource(R.string.expedition_depart, EnergyEngine.EXPEDITION_COST))
                }
            }

            is ExpeditionState.InProgress -> Text(
                stringResource(R.string.expedition_in_progress, remainingLabel(expedition.remainingMillis)),
                style = MaterialTheme.typography.bodyMedium,
            )

            ExpeditionState.ReadyToOpen -> {
                Text(
                    stringResource(R.string.expedition_ready_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpen) {
                    Text(stringResource(R.string.expedition_open))
                }
            }
        }
    }
}

@Composable
private fun remainingLabel(remainingMillis: Long): String {
    val totalMinutes = remainingMillis / MILLIS_PER_MINUTE
    return stringResource(
        R.string.expedition_remaining_format,
        totalMinutes / MINUTES_PER_HOUR,
        totalMinutes % MINUTES_PER_HOUR,
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
