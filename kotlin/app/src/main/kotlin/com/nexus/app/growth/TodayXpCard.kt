package com.nexus.app.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.XpGauge
import com.nexus.core.ActivityType
import com.nexus.core.DayXpExplanation
import com.nexus.core.XpLine

/**
 * 오늘 XP + 환산 설명 진입점 (#24, E3-12 — MVP §2 성장 탭의 "환산 투명성").
 * 접힌 상태는 합계만, 펼치면 세션별 근거와 상한 적용 내역까지 — 제외된 세션도
 * 숨기지 않고 이유와 함께 보여준다.
 */
@Composable
internal fun TodayXpCard(explanation: DayXpExplanation) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // NexusCard로 통일(#259 리뷰) — 게이지 토큰이 검증된 surfaceContainerLow 위에서 렌더되게.
    // 헤더 값(ink) + 우측 펼치기 버튼은 title·trailing 슬롯으로.
    NexusCard(
        title = stringResource(R.string.xp_explain_today_format, explanation.cappedXp),
        trailing = {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.xp_explain_hide else R.string.xp_explain_show))
            }
        },
    ) {
        // 오늘 XP → 소프트 니 진행 게이지 (#259) — 값·라벨은 헤더가 ink 제공
        XpGauge(explanation.cappedXp)
        if (expanded) ExplanationDetail(explanation)
    }
}

@Composable
private fun ExplanationDetail(explanation: DayXpExplanation) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
        if (explanation.lines.isEmpty()) {
            Text(stringResource(R.string.xp_explain_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            explanation.lines.forEach { XpLineRow(it) }
            CapNotes(explanation)
        }
        Spacer(Modifier.height(NexusSpacing.xs))
        Text(
            stringResource(
                R.string.xp_explain_formula_note,
                ActivityType.WALKING.pointsPerMinute.trimmedRate(),
                ActivityType.RUNNING.pointsPerMinute.trimmedRate(),
                ActivityType.STRENGTH.pointsPerMinute.trimmedRate(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun XpLineRow(line: XpLine) {
    val typeLabel = line.type?.let { stringResource(it.labelRes()) }
        ?: stringResource(R.string.activity_other)
    val points = if (line.countsForXp) {
        stringResource(R.string.xp_explain_points_format, line.basePoints)
    } else {
        stringResource(R.string.xp_explain_excluded)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.xp_explain_line_format, typeLabel, line.minutes, line.tier.name),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(points, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CapNotes(explanation: DayXpExplanation) {
    if (explanation.kneeApplied) {
        Text(
            stringResource(
                R.string.xp_explain_knee_note,
                explanation.rawPoints,
                explanation.kneeReducedPoints,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (explanation.hardCapped) {
        Text(stringResource(R.string.xp_explain_hardcap_note), style = MaterialTheme.typography.bodySmall)
    }
}

/** 분당 포인트 표시용 — 정수 배율은 소수점 없이 (1.0 → "1", 1.5 → "1.5"). */
private fun Double.trimmedRate(): String = if (this == toLong().toDouble()) toLong().toString() else toString()
