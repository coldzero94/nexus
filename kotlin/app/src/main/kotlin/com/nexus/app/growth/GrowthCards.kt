package com.nexus.app.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.animatedGaugeProgress
import com.nexus.app.ui.gaugeSemantics
import com.nexus.core.ActivityType
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.GrowthSummary
import com.nexus.core.StatMapping
import com.nexus.core.XpEngine

/**
 * 성장 탭의 표시 전용 카드들 (#311) — 레벨·성향·능력치.
 *
 * 화면 파일에서 분리한 이유: `GrowthScreen.kt`는 로드 상태 라우팅을 맡고, 여기는 **값을 받아 그리는
 * 일만** 한다. 카드가 늘어도 화면 파일의 함수 수가 늘지 않아 detekt 임계에 밀리지 않는다.
 */
@Composable
internal fun GrowthContent(data: GrowthUiState) {
    TodayXpCard(data.today)
    LevelCard(data.summary)
    AffinityCard(data.summary)
    StatsCard(data.summary)
    Text(
        stringResource(R.string.growth_scope_note, ClassAffinityCalculator.WINDOW_DAYS),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LevelCard(data: GrowthSummary) {
    // 성장 탭의 히어로 — 레벨 수는 titleLarge로 크게, 카드는 Highlight로 강조(형제 카드와 위계 차)
    // 레벨 라벨·바·누적 XP를 한 노드로 (#224) — 따로 읽히면 "40퍼센트"만 들린다
    val levelState = stringResource(R.string.a11y_level_state, data.level, data.totalXp)
    NexusCard(
        emphasis = CardEmphasis.Highlight,
        modifier = Modifier.gaugeSemantics(
            label = stringResource(R.string.a11y_level_gauge),
            stateText = levelState,
        ),
    ) {
        Text(
            stringResource(R.string.growth_level_format, data.level),
            style = MaterialTheme.typography.titleLarge,
        )
        // 레벨 진행은 상방 전용(불퇴행) — 증가는 감속 보간, 레벨업 리셋은 즉시(뒤로 안 빠짐) (#262)
        val levelProgress = animatedGaugeProgress(data.progress.toFloat(), upwardOnly = true, label = "level")
        LinearProgressIndicator(
            progress = { levelProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.growth_xp_format, data.totalXp, XpEngine.FORMULA_VERSION),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AffinityCard(data: GrowthSummary) {
    NexusCard(
        title = stringResource(R.string.growth_affinity_format, stringResource(data.affinity.labelRes())),
    ) {
        ActivityType.entries.forEach { type ->
            ShareRow(stringResource(type.labelRes()), data.axisShares[type] ?: 0.0)
        }
    }
}

@Composable
private fun ShareRow(label: String, share: Double) {
    // 막대에 텍스트 대체가 없어 축 이름조차 안 읽혔다 (#224)
    val state = stringResource(R.string.a11y_axis_state, (share * PERCENT).toInt())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.gaugeSemantics(label = label, stateText = state),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { share.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatsCard(data: GrowthSummary) {
    NexusCard(title = stringResource(R.string.growth_stats_title)) {
        StatMapping.unlockedStats.forEach { stat ->
            StatRow(stringResource(stat.labelRes()), data.stats[stat] ?: 0)
        }
        StatMapping.lockedStats.forEach { stat ->
            Text(
                stringResource(R.string.growth_stat_locked_format, stringResource(stat.labelRes())),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.growth_stat_value_format, value),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 비율 → 퍼센트 낭독 변환 (#224). */
private const val PERCENT = 100
