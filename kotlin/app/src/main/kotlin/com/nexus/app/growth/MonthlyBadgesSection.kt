package com.nexus.app.growth

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusIcons
import com.nexus.core.MonthlyBadge
import com.nexus.core.MonthlyBadgeCalendar
import com.nexus.core.MonthlyBadgeContext
import com.nexus.core.MonthlySignals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val TAG = "MonthlyBadges"

/**
 * 이달 배지 노출 상태 (#206) — 이번 달에 열려 있는 배지와 그중 획득분.
 * [unlocked]는 이번 달 배지로 한정한다(영속 저장소에는 지난 달 획득분도 남지만 여기 세면 개수가 틀어진다).
 */
internal data class MonthlyBadgeState(val monthValue: Int, val badges: List<MonthlyBadge>, val unlocked: Set<String>)

/**
 * 월 한정 배지 로드 (#206, #38 후속) — `monthly_badges.json`과 `MonthlyBadgeCalendar`는 있는데
 * **호출처가 0개**라 사용자에게 한 번도 보이지 않던 것을 배선한다(#204와 같은 '죽은 배선' 계열).
 *
 * 신호는 이번 달 범위로 집계한다: 활동일·XP는 원장(최종 진실 — 취소로 상쇄된 날은 활동일이 아니다),
 * 걸음은 Health Connect. 월별 원정 수는 카운터가 누적뿐이라(#204) 0으로 두며, 현재 배지 표는
 * 그 변수를 쓰지 않는다.
 *
 * 획득분은 상시 배지와 같이 **영속 합집합**으로 표시한다([BadgeProgressStore]) — 세션 취소로
 * 활동일이 줄거나 권한이 회수돼 걸음이 0이 되면 이미 얻은 배지가 다시 잠기는데, 그건 "캐릭터는
 * 퇴행하지 않는다"는 제품 불변식 위반이다(#177 리뷰 Critical과 같은 결함).
 *
 * 부가 정보라 실패는 null — 성장 화면은 그대로 뜬다(#130 catch 계약).
 */
internal suspend fun loadMonthlyBadges(
    context: Context,
    manager: HealthConnectManager,
    ledger: RewardLedgerRepository,
): MonthlyBadgeState? = try {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    val monthStart = month.atDay(1).toEpochDay()
    // 이달 말이 아니라 '오늘'까지 — 미래 날짜는 애초에 데이터가 없지만 의미를 분명히 한다
    val monthEnd = today.toEpochDay()

    val table = withContext(Dispatchers.IO) { CharacterAssets(context).loadMonthlyBadgeTable() }
    val period = month.toString() // YYYY-MM
    val active = MonthlyBadgeCalendar.activeBadges(table, period)

    if (active.isEmpty()) {
        // 이번 달 배지가 없으면 카드를 아예 띄우지 않는다 — 빈 카드는 노이즈
        null
    } else {
        val dailyXp = ledger.dailyXpMap()
        val dailySteps = manager
            .stepRepositoryOrNull()
            ?.readDailySteps(days = today.dayOfMonth)
            ?.associate { it.date.toEpochDay() to it.steps }
            .orEmpty()

        val signals = MonthlyBadgeContext(
            monthActiveDays = MonthlySignals.activeDays(dailyXp, monthStart, monthEnd),
            monthSteps = MonthlySignals.totalSteps(dailySteps, monthStart, monthEnd),
            monthExpeditions = 0, // 월별 원정 카운터 없음(#204는 누적) — 현재 표는 미사용
            monthXp = MonthlySignals.totalXp(dailyXp, monthStart, monthEnd),
        )
        val store = BadgeProgressStore(context, BadgeProgressStore.MONTHLY_PREFS)
        val currently = MonthlyBadgeCalendar.unlocked(table, period, signals)
        // 월 한정 배지도 축하한다 (#218) — **시간 제한 수집물**이라 오히려 축하가 더 급하다.
        // 상시 배지와 같은 대기 집합을 쓴다: id 공간이 겹치지 않고, 한 집합이면 같은 달에 상시·월간이
        // 함께 열려도 카드 하나로 묶인다(폭주 금지).
        val newly = MonthlyBadgeCalendar.newlyUnlocked(table, period, signals, store.earned)
        withContext(Dispatchers.IO) { commitBadgeProgress(context, store, newly, currently) }
        // 표시는 영속 합집합을 이번 달 배지로 좁힌 것 — 지난 달 획득분은 저장소에 남되 개수엔 안 센다
        val activeIds = active.mapTo(mutableSetOf()) { it.id }
        MonthlyBadgeState(
            monthValue = month.monthValue,
            badges = active,
            unlocked = (store.earned + currently) intersect activeIds,
        )
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "monthly badge load IO failure", e)
    null
} catch (e: RemoteException) {
    // HC 서비스 사망·업데이트 중 — 부가 정보라 화면을 죽이지 않는다(loadBadges와 같은 계약)
    Log.w(TAG, "monthly badge load remote failure", e)
    null
} catch (e: android.database.SQLException) {
    // 원장 DB 문제(디스크·손상)도 배지 카드만 생략 (#163)
    Log.w(TAG, "monthly badge ledger db failure", e)
    null
} catch (e: SecurityException) {
    Log.w(TAG, "monthly badge load permission failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "monthly badge table parse failure", e)
    null
} catch (e: IllegalStateException) {
    Log.w(TAG, "monthly badge load state failure", e)
    null
}

/** 이달의 배지 카드 (#206) — 이번 달에만 열리는 배지라 제목에 기간을 밝힌다. */
@Composable
internal fun MonthlyBadgesCard(state: MonthlyBadgeState, modifier: Modifier = Modifier) {
    NexusCard(
        modifier = modifier,
        titleIcon = NexusIcons.goal,
        title = stringResource(
            R.string.growth_monthly_badges_title,
            state.monthValue,
            state.unlocked.size,
            state.badges.size,
        ),
    ) {
        Text(
            stringResource(R.string.growth_monthly_badges_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.badges.forEach { badge ->
            // 상시 배지와 **같은 행 컴포넌트** — 획득/미획득 표현이 화면 안에서 갈리면 위계가 무의미해진다 (#266)
            BadgeGlyphRow(
                name = badge.name,
                description = badge.description,
                icon = badge.icon,
                earned = badge.id in state.unlocked,
            )
        }
    }
}
