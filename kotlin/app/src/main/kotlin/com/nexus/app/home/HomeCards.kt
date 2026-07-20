package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.core.ConditionEngine
import com.nexus.core.EnergyEngine
import com.nexus.core.ExpeditionState
import kotlin.math.roundToInt

/**
 * 아침 요약 카드 (#36, E5-3) — "간밤의 회복 + 어제의 성장", 하루 1회.
 * 무활동이었던 어제는 꾸짖지 않는다(무처벌) — 쉼도 리듬으로 프레이밍.
 */
@Composable
internal fun MorningCard(state: HomeUiState, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.morning_title), style = MaterialTheme.typography.titleMedium)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.morning_dismiss))
                }
            }
        }
    }
}

/**
 * 정산 개봉 카드 (#35, E5-2) — "새 활동 반영 시 XP 정산 의식"(코어 루프 3단계).
 * 동기화 지연으로 늦게 도착한 성장을 문제 대신 선물로 프레이밍한다.
 */
@Composable
internal fun SettlementCard(deltaXp: Int, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settlement_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settlement_body, deltaXp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.settlement_open))
                }
            }
        }
    }
}

/** 컨디션 게이지 (#32) — 소프트 손실 게이지(20~100 사이에서 움직임, 소멸 없음). */
@Composable
internal fun ConditionGauge(condition: Double) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.home_condition_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_condition_value, condition.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (condition / ConditionEngine.MAX).toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 오늘 요약 — XP·운동 분·걸음 한 줄씩 (#32). */
@Composable
internal fun TodaySummaryCard(state: HomeUiState) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_today_title), style = MaterialTheme.typography.titleMedium)
            SummaryRow(
                stringResource(R.string.home_today_xp),
                stringResource(R.string.home_today_xp_value, state.todayXp),
            )
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
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 원정 카드 (#34·#67) — 출발(에너지 소모)/진행(남은 시간)/개봉. 동기화 지연 흡수 연출의 무대:
 * "모험에서 돌아오는 중" 프레임(MVP §1 — 실시간 약속 금지). 보상·개봉 연출은 E5-7.
 */
@Composable
internal fun ExpeditionCard(expedition: ExpeditionState, energy: Int, onDepart: () -> Unit, onOpen: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.home_expedition_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_energy_format, energy),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
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
