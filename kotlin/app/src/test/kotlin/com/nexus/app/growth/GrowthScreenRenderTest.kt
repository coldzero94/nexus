package com.nexus.app.growth

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nexus.app.R
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.FIRST_RUN_NOTICE_TAG
import com.nexus.app.ui.NexusTheme
import com.nexus.core.ActivityType
import com.nexus.core.Badge
import com.nexus.core.BadgeTable
import com.nexus.core.ClassAffinity
import com.nexus.core.DayXpExplanation
import com.nexus.core.GrowthSummary
import com.nexus.core.Stat
import com.nexus.core.StoryFragment
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor
import kotlin.test.assertEquals

/**
 * 성장 화면 로드 분기 전수 렌더 (#320) — 활동과 같은 방식. 실제 화면 컴포저블을 태운다.
 */
@RunWith(RobolectricTestRunner::class)
class GrowthScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        runCatching { WorkManager.getInstance(context) }.onFailure {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(Executor { }).build(),
            )
        }
    }

    private fun render(
        load: GrowthLoad,
        change: GrowthChange? = null,
        badges: BadgeSectionsState = BadgeSectionsState(),
    ) {
        composeRule.setContent {
            NexusTheme {
                GrowthScreen(
                    manager = HealthConnectManager(context),
                    controller = GrowthUiController(
                        context = context,
                        manager = HealthConnectManager(context),
                        exerciseRepo = null,
                        ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao()),
                        stateStore = GrowthStateStore(context),
                        seed = GrowthSeed(load = load, change = change, badgeSections = badges),
                    ),
                )
            }
        }
    }

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun successState(awaiting: Boolean) = GrowthLoad.Success(
        GrowthUiState(
            summary = GrowthSummary(
                level = 3,
                totalXp = 520,
                progress = 0.4,
                affinity = ClassAffinity.BALANCED,
                axisShares = emptyMap(),
                stats = emptyMap(),
            ),
            today = DayXpExplanation(
                lines = emptyList(),
                rawPoints = 0,
                cappedXp = 0,
                kneeApplied = false,
                kneeReducedPoints = 0,
                hardCapped = false,
            ),
            awaitingFirstData = awaiting,
        ),
    )

    @Test
    fun `제목은 어느 분기에서나 보인다`() {
        render(GrowthLoad.Failure)

        composeRule.onNodeWithText(string(R.string.growth_title)).assertIsDisplayed()
    }

    @Test
    fun `미연결이면 연결 안내를 그린다`() {
        render(GrowthLoad.PermissionDenied)

        composeRule.onNodeWithText(string(R.string.growth_demo_body, 28)).assertIsDisplayed()
    }

    @Test
    fun `실패면 다시 시도를 그린다`() {
        render(GrowthLoad.Failure)

        composeRule.onNodeWithText(string(R.string.growth_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
    }

    private fun summaryWith(shares: Map<ActivityType, Double>, stats: Map<Stat, Int> = emptyMap()) = GrowthLoad.Success(
        GrowthUiState(
            summary = GrowthSummary(
                level = 3,
                totalXp = 520,
                progress = 0.4,
                affinity = ClassAffinity.BALANCED,
                axisShares = shares,
                stats = stats,
            ),
            today = DayXpExplanation(
                lines = emptyList(),
                rawPoints = 0,
                cappedXp = 0,
                kneeApplied = false,
                kneeReducedPoints = 0,
                hardCapped = false,
            ),
            awaitingFirstData = false,
        ),
    )

    /**
     * 성향 범례는 **색만으로 구분하지 않는다**(#263 AC②) — 라벨과 퍼센트가 항상 함께 나온다.
     * 비중이 0인 축도 남아야 "이 축은 0"과 "이 축이 없다"가 구분된다.
     */
    @Test
    fun `성향 범례가 세 축을 라벨과 퍼센트로 모두 보여준다`() {
        render(
            summaryWith(
                mapOf(
                    ActivityType.WALKING to 0.6,
                    ActivityType.RUNNING to 0.4,
                    ActivityType.STRENGTH to 0.0,
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.growth_axis_legend_format, string(R.string.activity_walking), 60))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.growth_axis_legend_format, string(R.string.activity_running), 40))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.growth_axis_legend_format, string(R.string.activity_strength), 0))
            .assertIsDisplayed()
    }

    /**
     * 스탯 바는 **서로 비교**가 유일한 의미다(절대 상한이 없다). 전부 0이면 비교 대상이 없으므로
     * 바를 그리지 않는다 — 트랙만 보이는 줄은 '꽉 찬 바'로 오독된다.
     */
    private fun assertStateDescription(label: Int, expected: String) {
        composeRule.onNodeWithContentDescription(string(label))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
    }

    @Test
    fun `스탯이 전부 0이면 만점을 말하지 않는다`() {
        render(summaryWith(emptyMap()))

        // "0 중 0"으로 읽히면 만점이 0인 것처럼 들린다 — 이 분기가 바를 생략하는 분기와 같다
        assertStateDescription(R.string.stat_endurance, string(R.string.a11y_stat_state_alone, 0))
    }

    /**
     * 능력치에는 **절대 상한이 없다**. "30 중 30"처럼 읽으면 만점을 찍은 것처럼 들리는데 사실이 아니다 —
     * 최상위는 그 사실을, 나머지는 기준값을 읽는다.
     */
    @Test
    fun `스탯 낭독이 존재하지 않는 만점을 말하지 않는다`() {
        render(summaryWith(emptyMap(), stats = mapOf(Stat.ENDURANCE to 30, Stat.AGILITY to 12)))

        assertStateDescription(R.string.stat_endurance, string(R.string.a11y_stat_state_top, 30))
        assertStateDescription(R.string.stat_agility, string(R.string.a11y_stat_state_relative, 12, 30))
    }

    /**
     * 히어로가 TalkBack **표제 단위 이동**으로 도달 가능해야 한다 (A11Y-TALKBACK 8번).
     * `clearAndSetSemantics`가 하위를 지우므로 표제를 명시하지 않으면 이 탭에서 가장 중요한
     * 카드가 통째로 건너뛰어진다.
     */
    @Test
    fun `히어로가 표제로 표시된다`() {
        render(summaryWith(emptyMap()))

        // Heading은 Unit 값 프로퍼티라 expectValue로는 '없음'도 통과한다 — 키 존재로 단언해야 한다
        composeRule.onNodeWithContentDescription(string(R.string.a11y_level_gauge))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /**
     * 성향 구성이 **한 번만** 들려야 한다 (#258에서 정한 규칙). 바에 구성 문장을 붙이고 범례가
     * 같은 값을 또 내면 같은 값이 두 번 낭독된다.
     */
    @Test
    fun `성향 구성은 범례에서 한 번만 낭독된다`() {
        render(summaryWith(mapOf(ActivityType.WALKING to 0.6, ActivityType.RUNNING to 0.4)))

        val walkLabel = string(R.string.growth_axis_legend_format, string(R.string.activity_walking), 60)
        assertEquals(
            1,
            composeRule.onAllNodesWithText(walkLabel, substring = true).fetchSemanticsNodes().size,
            "같은 값이 바와 범례에서 두 번 낭독된다",
        )
    }

    @Test
    fun `비중이 전부 0이면 바 대신 준비 중 문구를 그린다`() {
        // 빈 바를 그리면 '0%인 상태'가 아니라 '깨진 바'로 보인다 (#213 정합)
        render(summaryWith(emptyMap()))

        composeRule.onNodeWithText(string(R.string.growth_affinity_empty)).assertIsDisplayed()
    }

    @Test
    fun `합이 1이 아닌 비중도 정규화해 보여준다`() {
        // 독립 바 3개일 때는 안 보였던 문제 — 한 바에 이어 붙이면 합이 1이 아닌 게 눈에 보인다
        render(summaryWith(mapOf(ActivityType.WALKING to 0.2, ActivityType.RUNNING to 0.2)))

        composeRule.onNodeWithText(string(R.string.growth_axis_legend_format, string(R.string.activity_walking), 50))
            .assertIsDisplayed()
    }

    /**
     * 레벨 카드는 #224에서 [androidx.compose.ui.semantics.clearAndSetSemantics]로 묶었다 — 안쪽
     * "레벨 3" 텍스트는 시맨틱 트리에 없다(스크린리더가 한 문장으로 듣게 하려는 의도).
     * 그래서 단언도 **스크린리더가 보는 노드**로 한다: 게이지 라벨.
     */
    @Test
    fun `성공이면 레벨 게이지를 그리고 빈 상태는 없다`() {
        render(successState(awaiting = false))

        composeRule.onNodeWithContentDescription(string(R.string.a11y_level_gauge)).assertExists()
        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertDoesNotExist()
    }

    /**
     * 축하 카드(#61)와 히어로의 **공존** (AC⑤). 히어로를 Highlight로 올리면서 축하 카드가 쓰던
     * primaryContainer와 같은 색이 됐던 게 여기서 걸렸다 — 색 구분은 [CardEmphasisTest]가 지키고,
     * 이 테스트는 둘이 함께 그려지는지를 본다.
     */
    @Test
    fun `축하 카드와 히어로가 함께 그려진다`() {
        render(summaryWith(emptyMap()), change = GrowthChange(levelUpTo = 3, affinityChangedTo = null))

        composeRule.onNodeWithText(string(R.string.celebrate_level_up, 3)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.a11y_level_gauge)).assertExists()
    }

    /** 축하 대기 배지가 실제로 있는 상태 — 없으면 아래 단언들이 아무것도 검증하지 않는다. */
    private fun withPendingBadge() = BadgeSectionsState(
        standard = BadgeState(
            table = BadgeTable(
                version = "test",
                badges = listOf(
                    Badge(
                        id = "first_step",
                        name = "첫걸음",
                        description = "함께한 첫 활동을 기록했어요.",
                        whenExpr = "level >= 1",
                        icon = "first_step",
                    ),
                ),
            ),
            unlocked = setOf("first_step"),
            newlyUnlocked = setOf("first_step"),
        ),
    )

    /**
     * 축하는 한 번에 하나만 (#218). 레벨업이 우선이고, 배지 축하는 대기 집합에 남아 **다음 진입에서**
     * 뜬다 — 겹치면 각각의 무게가 반씩 깎이고 화면 상단이 카드 두 장으로 막힌다.
     */
    @Test
    fun `레벨업 축하가 있으면 배지 축하는 뜨지 않는다`() {
        render(
            summaryWith(emptyMap()),
            change = GrowthChange(levelUpTo = 3, affinityChangedTo = null),
            badges = withPendingBadge(),
        )

        composeRule.onNodeWithText(string(R.string.celebrate_level_up, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.badge_unlock_title)).assertDoesNotExist()
    }

    @Test
    fun `레벨업이 없으면 배지 축하가 뜬다`() {
        // 위 단언이 '축하할 배지가 없어서' 통과하는 게 아님을 보인다
        render(summaryWith(emptyMap()), badges = withPendingBadge())

        composeRule.onNodeWithText(string(R.string.badge_unlock_title)).assertIsDisplayed()
    }

    /**
     * 배선 가드 (#112) — 카드 컴포저블이 있는 것과 화면이 그걸 쓰는 건 다른 명제다.
     * #268에서 기능 전체를 되돌려도 테스트가 초록이었던 게 이 차이 때문이다.
     */
    @Test
    fun `모은 이야기 조각이 있으면 도감을 그린다`() {
        val fragment = StoryFragment(id = "f1", title = "첫 길", body = "처음 걸어본 길이었다.")

        render(
            successState(awaiting = false),
            badges = BadgeSectionsState(
                codex = StoryCodexState(collected = listOf(fragment), total = 8, newlyFound = emptyList()),
            ),
        )

        // 도감은 화면 맨 아래라 470px 뷰포트 밖이다 — 스크롤해서 실제로 그려졌는지 본다
        composeRule.onNodeWithText(string(R.string.growth_codex_title, 1, 8)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(fragment.body).performScrollTo().assertIsDisplayed()
    }

    /** 조각이 없으면 카드 자체가 없어야 한다 — 성장 탭은 이미 잠긴 목록이 셋이다. */
    @Test
    fun `도감 상태가 없으면 카드를 안 그린다`() {
        render(successState(awaiting = false))

        composeRule.onNodeWithText(string(R.string.growth_codex_title, 1, 8)).assertDoesNotExist()
    }

    @Test
    fun `첫 데이터 대기면 빈 상태만 그리고 레벨 게이지는 숨긴다`() {
        render(successState(awaiting = true))

        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.a11y_level_gauge)).assertDoesNotExist()
    }
}
