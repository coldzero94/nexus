package com.nexus.app.steps

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.health.DailySteps
import com.nexus.app.health.HealthConnectManager
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 최근 7일 일별 걸음 (#7). 완료 기준: 삼성헬스 수치와 대조 가능.
 * HC 미가용/오류 시 에러 문구만 — 크래시 없음.
 */
@Composable
fun DailyStepsScreen(manager: HealthConnectManager, modifier: Modifier = Modifier) {
    val repo = remember { manager.stepRepositoryOrNull() }
    var days by remember { mutableStateOf<List<DailySteps>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(repo) {
        if (repo == null) {
            failed = true
            loading = false
            return@LaunchedEffect
        }
        try {
            days = repo.readDailySteps(7)
        } catch (e: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.steps_subtitle), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Text(stringResource(R.string.steps_loading), style = MaterialTheme.typography.bodyMedium)
            failed -> Text(stringResource(R.string.steps_error), style = MaterialTheme.typography.bodyMedium)
            else -> {
                val pattern = stringResource(R.string.steps_date_format)
                val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
                // 최신(오늘)이 위로 오도록 역순 표시
                days?.asReversed()?.forEach { day ->
                    StepRow(dateLabel = day.date.format(formatter), steps = day.steps)
                }
            }
        }
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
