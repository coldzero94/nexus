package com.nexus.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusIcons
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.VizColors
import com.nexus.core.SessionInput
import com.nexus.core.WeeklyGoal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 이번 주 목표 진척 (#215, E14-5) — 온보딩(#73)에서 정한 '주 N일' 약속의 진행을 점 N개로 보여준다.
 * 활동일=그날 XP 대상 세션이 있던 날, 주 시작=월요일(기기 로컬).
 *
 * **무처벌 프레이밍**: 미달을 실패로 규정하지 않는다 — 남은 목표가 이번 주에 가능하면 "N일 더면
 * 균형 보너스", 물리적으로 불가하면 "다음 주에 이어가요"(질책·지난 주 성적 없음). 달성 시 축하 톤.
 * 표시 전용 — 활동일 수는 화면에만, 페이로드에 없다.
 */
@Composable
internal fun WeeklyGoalRow(progress: WeeklyProgress, modifier: Modifier = Modifier) {
    val met = WeeklyGoal.isMet(progress.activeDays, progress.goalDays)
    val remaining = WeeklyGoal.remainingDays(progress.activeDays, progress.goalDays)
    val caption = when {
        met -> stringResource(R.string.weekly_goal_met)

        // 남은 목표가 이번 주에 물리적으로 가능한지 — 불가하면 재촉 대신 다음 주 프레이밍
        remaining <= progress.daysLeft -> stringResource(R.string.weekly_goal_remaining, remaining)

        else -> stringResource(R.string.weekly_goal_next_week)
    }
    NexusCard(
        modifier = modifier,
        emphasis = if (met) CardEmphasis.Celebration else CardEmphasis.Neutral,
        titleIcon = NexusIcons.goal,
        title = stringResource(R.string.weekly_goal_title, progress.activeDays, progress.goalDays),
    ) {
        GoalDots(progress.activeDays, progress.goalDays)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 목표 일수만큼의 점 — 채워진 점 = 활동한 날 수. 개별 날짜에 대응하지 않고 '몇 칸 채웠는지'만
 * 나타낸다(요일 지목은 압박이 되므로). 색만으로 전달하지 않도록 제목의 M/N 텍스트가 값을 말한다.
 */
@Composable
private fun GoalDots(activeDays: Int, goalDays: Int) {
    val viz = VizColors.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            // 점은 제목 "이번 주 M/N일"의 장식 — 값은 제목이 낭독하므로 점 행은 접근성에서 숨긴다
            // (낱개·오해 소지 라벨 대신 무음, #265 장식 아이콘 선례와 동일, #215 리뷰)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(goalDays.coerceAtLeast(0)) { index ->
            if (index < activeDays) {
                Box(Modifier.size(DOT_DP.dp).clip(CircleShape).background(viz.conditionGood))
            } else {
                Box(Modifier.size(DOT_DP.dp).clip(CircleShape).border(DOT_BORDER_DP.dp, outline, CircleShape))
            }
        }
        // 목표 초과 활동일은 보너스 — 목표 칸 뒤에 별도 점으로(초과분도 축하, 축소 표시 금지)
        repeat((activeDays - goalDays).coerceAtLeast(0)) {
            Box(Modifier.size(DOT_DP.dp).clip(CircleShape).background(viz.conditionStable))
        }
    }
}

private const val DOT_DP = 14
private const val DOT_BORDER_DP = 2

/**
 * 이번 주 목표 진척 조립 (#215) — 주 경계(월요일, 기기 로컬=KST, #21 주간 정산과 동일)만 여기서
 * 계산하고, 활동일 판정은 core [WeeklyGoal.activeDaysFromSessions](종목 有 + XP 대상 등급)에 위임.
 */
internal fun resolveWeeklyProgress(sessions: List<SessionInput>, today: LocalDate, goalDays: Int): WeeklyProgress {
    val weekStart = today.with(DayOfWeek.MONDAY).toEpochDay()
    val todayEpoch = today.toEpochDay()
    return WeeklyProgress(
        // 활동일 판정은 core 단일 원천 — 수기(Tier C) 제외라 기세·오늘 XP와 일치(#215 리뷰)
        activeDays = WeeklyGoal.activeDaysFromSessions(sessions, weekStart, todayEpoch),
        goalDays = goalDays,
        daysLeft = WeeklyGoal.daysLeftInWeek(weekStart, todayEpoch),
    )
}
