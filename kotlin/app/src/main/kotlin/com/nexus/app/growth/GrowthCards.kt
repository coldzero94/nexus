package com.nexus.app.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nexus.app.R
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.animatedGaugeProgress
import com.nexus.app.ui.gaugeSemantics
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.GrowthSummary
import com.nexus.core.StatMapping
import com.nexus.core.XpEngine

/**
 * 성장 탭의 표시 전용 카드들 (#311·#263) — 히어로 요약 + 종속 상세.
 *
 * 화면 파일에서 분리한 이유: `GrowthScreen.kt`는 로드 상태 라우팅을 맡고, 여기는 **값을 받아 그리는
 * 일만** 한다. 카드가 늘어도 화면 파일의 함수 수가 늘지 않아 detekt 임계에 밀리지 않는다.
 *
 * ## 위계 (#263)
 *
 * 이전에는 오늘XP·레벨·성향·능력치가 **동일 강조 회색 카드 4장**으로 평평히 나열돼, 정체성의 핵심인
 * 레벨이 오늘 걸음 수와 같은 무게로 보였다. 지금은 [GrowthHeroCard]가 레벨·진척·성향을 하나로 묶어
 * 상단 히어로가 되고, 오늘 XP와 능력치는 그 아래 **Neutral 종속 상세**로 내려간다.
 */
@Composable
internal fun GrowthContent(data: GrowthUiState) {
    GrowthHeroCard(data.summary)
    TodayXpCard(data.today)
    StatsCard(data.summary)
    Text(
        stringResource(R.string.growth_scope_note, ClassAffinityCalculator.WINDOW_DAYS),
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * 요약 히어로 (#263) — 레벨 + 진척바 + 성향 구성.
 *
 * 레벨 카드와 성향 카드를 **합친** 이유: 둘은 같은 질문("내 캐릭터가 어떤 상태인가")의 답이고,
 * 따로 두면 그 답이 두 카드에 흩어진 채 각각 형제 카드들과 같은 무게로 보인다. 하나로 묶으면
 * 카드 하나가 곧 정체성 요약이 된다.
 *
 * 접근성: 레벨 라벨·바·누적 XP를 한 노드로 (#224) — 따로 읽히면 "40퍼센트"만 들린다.
 * 성향 바는 자체 시맨틱을 갖고([AxisShareStackBar]) 이 묶음 밖에 있다.
 */
@Composable
private fun GrowthHeroCard(data: GrowthSummary) {
    NexusCard(emphasis = CardEmphasis.Highlight) {
        Column(
            // heading을 켜지 않으면 clearAndSetSemantics 때문에 히어로가 TalkBack 표제 이동에서
            // 통째로 건너뛰어진다 — 이 탭에서 가장 중요한 카드가 도달 불가가 된다
            modifier = Modifier.fillMaxWidth().gaugeSemantics(
                label = stringResource(R.string.a11y_level_gauge),
                stateText = stringResource(R.string.a11y_level_state, data.level, data.totalXp),
                heading = true,
            ),
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        ) {
            // 히어로의 시선 고정점 — 형제 카드의 titleMedium 제목보다 확실히 커야 위계가 생긴다
            Text(
                stringResource(R.string.growth_level_format, data.level),
                style = MaterialTheme.typography.headlineMedium,
            )
            // 레벨 진행은 상방 전용(불퇴행) — 증가는 감속 보간, 레벨업 리셋은 즉시(뒤로 안 빠짐) (#262)
            val levelProgress = animatedGaugeProgress(data.progress.toFloat(), upwardOnly = true, label = "level")
            LinearProgressIndicator(progress = { levelProgress }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.growth_xp_format, data.totalXp, XpEngine.FORMULA_VERSION),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(R.string.growth_affinity_format, stringResource(data.affinity.labelRes())),
            style = MaterialTheme.typography.titleSmall,
        )
        AxisShareStackBar(data.axisShares)
    }
}

@Composable
private fun StatsCard(data: GrowthSummary) {
    NexusCard(title = stringResource(R.string.growth_stats_title)) {
        val unlocked = StatMapping.unlockedStats.associateWith { data.stats[it] ?: 0 }
        // 바 길이는 해금 스탯 중 최댓값 기준 — 절대 상한이 없는 값이라 **서로 비교**되는 게 유일한 의미다.
        // 그래서 전부 0이면 바를 아예 그리지 않는다: 비교할 대상이 없는데 빈 바를 늘어놓으면
        // 정보가 0인 장식이 되고, 트랙만 보이는 줄이 '꽉 찬 바'로 오독된다.
        val peak = unlocked.values.max()
        unlocked.forEach { (stat, value) ->
            StatRow(label = stringResource(stat.labelRes()), value = value, peak = peak)
        }
        StatMapping.lockedStats.forEach { stat ->
            // 잠금은 바 없이 텍스트 유지 — 값이 없는데 빈 바를 그리면 '0'으로 오독된다
            Text(
                stringResource(R.string.growth_stat_locked_format, stringResource(stat.labelRes())),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 해금 스탯 한 줄 (#263) — 라벨 + 값 + 경량 바.
 *
 * 바를 붙인 이유는 숫자만으로는 **스탯 간 상대 크기**가 안 읽히기 때문이다. 절대 상한이 없는
 * 값이라 최댓값 기준 상대 길이로 그린다 — 그래서 바는 "얼마나 찼나"가 아니라 "서로 비교"다.
 *
 * @param peak 해금 스탯 중 최댓값. 0이면 바를 생략한다 — 비교 대상이 없다.
 */
@Composable
private fun StatRow(label: String, value: Int, peak: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().gaugeSemantics(
            label = label,
            // 절대 상한이 없는 값이라 "30 중 30"처럼 만점을 말하면 거짓이 된다 — 최상위는 그 사실을,
            // 나머지는 기준값을 읽는다. 비교 대상이 없으면(전부 0) 값만.
            stateText = when {
                peak <= 0 -> stringResource(R.string.a11y_stat_state_alone, value)
                value >= peak -> stringResource(R.string.a11y_stat_state_top, value)
                else -> stringResource(R.string.a11y_stat_state_relative, value, peak)
            },
        ),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.growth_stat_value_format, value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // 행별로 가드한다 — RECOVERY는 S4까지 어떤 활동에도 매핑되지 않아 항상 0이고,
        // 전역 peak만 보면 그 한 줄이 영구히 '트랙만 보이는 빈 바'로 남는다(꽉 찬 바로 오독된다)
        if (peak > 0 && value > 0) {
            LinearProgressIndicator(
                progress = { value.toFloat() / peak },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
