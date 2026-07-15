package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.core.ConditionEngine
import kotlin.math.roundToInt

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

/** 원정 자리 (#32) — 실데이터는 E5(원정 루프)에서. 동기화 지연 흡수 연출의 무대가 될 카드. */
@Composable
internal fun ExpeditionPlaceholderCard() {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_expedition_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_expedition_placeholder), style = MaterialTheme.typography.bodySmall)
        }
    }
}
