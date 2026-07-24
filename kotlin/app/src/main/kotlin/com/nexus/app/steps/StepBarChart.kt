package com.nexus.app.steps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.health.DailySteps
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.VizColors
import com.nexus.core.StepChartScale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 7일 걸음 막대 차트 (#258, E16-8) — 텍스트 행 나열을 걷어내고 '이번 주 리듬'을 한눈에. 최댓값 기준
 * y-스케일([StepChartScale], 순수·테스트됨), 오늘 막대는 [VizColors] 걷기색 강조, 무활동일은 얇은
 * baseline. 걸음 원시값은 화면 표시만 — 분석/크래시 payload 미탑재(occurrence-only, MVP §1).
 *
 * 접근성: 막대별 요일·값 [contentDescription], 오늘은 '오늘' 라벨(색만으로 강조 전달 안 함).
 * 빈 데이터(전부 0)는 '고장' 아닌 '준비 중' 프레이밍([R.string.steps_chart_empty], #213 정합).
 */
@Composable
internal fun StepBarChart(days: List<DailySteps>, modifier: Modifier = Modifier) {
    if (days.isEmpty() || days.all { it.steps <= 0L }) {
        Text(
            stringResource(R.string.steps_chart_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val today = remember { LocalDate.now() }
    val ratios = remember(days) { StepChartScale.barRatios(days.map { it.steps }) }
    val weekdayPattern = stringResource(R.string.steps_weekday_format)
    val cdPattern = stringResource(R.string.steps_date_format)
    val weekdayFmt = remember(weekdayPattern) { DateTimeFormatter.ofPattern(weekdayPattern, Locale.KOREAN) }
    val cdFmt = remember(cdPattern) { DateTimeFormatter.ofPattern(cdPattern, Locale.KOREAN) }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, day ->
            DayBar(
                ratio = ratios[i],
                steps = day.steps,
                date = day.date,
                isToday = day.date == today,
                weekdayFmt = weekdayFmt,
                cdFmt = cdFmt,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.DayBar(
    ratio: Float,
    steps: Long,
    date: LocalDate,
    isToday: Boolean,
    weekdayFmt: DateTimeFormatter,
    cdFmt: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    val viz = VizColors.current
    val label = if (isToday) stringResource(R.string.steps_today_label) else date.format(weekdayFmt)
    val cd = stringResource(R.string.steps_bar_desc, date.format(cdFmt), steps)
    // 오늘은 걷기색 강조, 과거 활동일은 물러난 고정 톤(≥3:1), 무활동일은 recessive baseline
    val barColor = if (isToday) viz.walking else viz.walkingMuted
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        // 막대 하나를 한 포커스 단위로 — CD가 요일·값을 모두 담아 자식 라벨 이중 낭독 방지(#258 리뷰)
        modifier.clearAndSetSemantics { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(BAR_AREA.dp),
        ) {
            val corner = CornerRadius(BAR_CORNER.dp.toPx())
            val minH = BAR_MIN.dp.toPx()
            val active = ratio > 0f
            val barH = if (active) (size.height * ratio).coerceAtLeast(minH) else minH
            drawRoundRect(
                color = if (active) barColor else baselineColor,
                topLeft = Offset(0f, size.height - barH),
                size = Size(size.width, barH),
                cornerRadius = corner,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private const val BAR_AREA = 96
private const val BAR_GAP = 3
private const val BAR_CORNER = 4
private const val BAR_MIN = 3
