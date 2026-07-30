package com.nexus.app.growth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusTheme
import com.nexus.core.Badge
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 배지 획득 축하 (#218, E14-8).
 *
 * 핵심 계약은 **감지와 소비의 분리**다. `newlyUnlocked`는 원래 `currently - earned`로 계산되고
 * 바로 다음 줄에서 `addEarned`가 돌아 다음 로드에는 빈 집합이 됐다 — 즉 신호가 한 번의 컴포지션
 * 동안만 살아서, 회전하거나 프로세스가 죽으면 축하가 영영 사라진다. #61 리뷰가 레벨업 카드에서
 * 같은 함정을 지적했고 여기서도 같은 답을 쓴다: 소비는 '확인'을 눌렀을 때.
 */
@RunWith(RobolectricTestRunner::class)
class BadgeCelebrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** 테스트 전용 prefs — 앱 부팅 경로가 프로덕션 저장소를 건드려도 영향받지 않게. */
    private val testPrefs = "nexus_badge_celebration_test"

    private fun store() = BadgeCelebrationStore(context, testPrefs)

    @Before
    fun clearPending() {
        context.getSharedPreferences(testPrefs, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun badge(id: String, name: String) =
        Badge(id = id, name = name, description = "$name 설명", whenExpr = "level >= 1", icon = "first_step")

    // ── 저장소: 감지와 소비의 분리 ──

    @Test
    fun `기록한 배지가 대기 집합에 남는다`() {
        store().record(setOf("a", "b"))

        assertEquals(setOf("a", "b"), store().pending)
    }

    @Test
    fun `여러 번 기록하면 합쳐진다`() {
        // 동시 다수는 묶음 1회 — 배지마다 카드를 띄우면 첫 동기화에서 다섯 장이 연달아 뜬다
        store().record(setOf("a"))
        store().record(setOf("b", "c"))

        assertEquals(setOf("a", "b", "c"), store().pending)
    }

    @Test
    fun `빈 입력은 무시한다`() {
        store().record(setOf("a"))
        store().record(emptySet())

        assertEquals(setOf("a"), store().pending)
    }

    /**
     * 이게 이 저장소의 존재 이유다 — 새 인스턴스에서도 읽혀야 프로세스 사망을 견딘다.
     */
    @Test
    fun `대기 집합은 새 인스턴스에서도 읽힌다`() {
        store().record(setOf("a"))

        assertEquals(setOf("a"), BadgeCelebrationStore(context, testPrefs).pending)
    }

    @Test
    fun `확인하면 대기 집합이 비워진다`() {
        store().record(setOf("a", "b"))

        store().clear()

        assertTrue(BadgeCelebrationStore(context, testPrefs).pending.isEmpty(), "같은 배지를 다시 축하한다")
    }

    // ── 로더 배선: 순서가 계약이다 ──

    private fun earnedStore() = BadgeProgressStore(context, "nexus_badge_progress_test")

    @Before
    fun clearEarned() {
        context.getSharedPreferences("nexus_badge_progress_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /**
     * `addEarned`가 먼저 돌면 다음 로드의 차집합이 비어 **축하 신호가 사라진다.** 이 순서가
     * 이 기능의 전부라, 배선이 뒤집히면 여기서 깨져야 한다.
     */
    @Test
    fun `획득 확정보다 축하 기록이 먼저다`() {
        val pending = commitBadgeProgress(
            context = context,
            earned = earnedStore(),
            newly = setOf("first_step"),
            currently = setOf("first_step"),
            celebration = store(),
        )

        assertEquals(setOf("first_step"), pending, "축하 대상이 사라졌다 — 순서가 뒤집혔다")
        assertEquals(setOf("first_step"), earnedStore().earned, "획득이 확정되지 않았다")
    }

    @Test
    fun `이전 실행에서 못 본 축하도 함께 온다`() {
        // 프로세스가 죽어 못 본 배지가 다음 진입에 합쳐져 나와야 한다
        store().record(setOf("old"))

        val pending = commitBadgeProgress(
            context = context,
            earned = earnedStore(),
            newly = setOf("new"),
            currently = setOf("old", "new"),
            celebration = store(),
        )

        assertEquals(setOf("old", "new"), pending)
    }

    @Test
    fun `새로 열린 게 없으면 대기 집합이 그대로다`() {
        val pending = commitBadgeProgress(
            context = context,
            earned = earnedStore(),
            newly = emptySet(),
            currently = setOf("first_step"),
            celebration = store(),
        )

        assertTrue(pending.isEmpty(), "없는 축하를 만들어냈다")
        assertEquals(setOf("first_step"), earnedStore().earned)
    }

    // ── 컨트롤러: '확인하면 다시 안 뜬다' (AC ①) ──

    private fun controller() = GrowthUiController(
        context = context,
        manager = HealthConnectManager(context),
        exerciseRepo = null,
        ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao()),
        stateStore = GrowthStateStore(context),
        seed = GrowthSeed(load = GrowthLoad.Failure, badgeCelebration = store()),
    )

    /**
     * AC ①의 '확인 후 재노출 없음'. 저장소 단위(`clear()`)와 카드 단위(`onDismiss` 콜백)는 각각
     * 테스트돼 있었지만, **버튼을 누르면 로더가 읽는 집합이 실제로 비는지**는 검증되지 않았다 —
     * 컨트롤러가 저장소를 인라인 생성해 테스트가 관찰할 수 없었다.
     */
    @Test
    fun `확인을 누르면 로더가 읽는 대기 집합이 비워진다`() {
        store().record(setOf("first_step"))
        val ui = controller()

        ui.dismissBadgeCelebration()

        assertTrue(store().pending.isEmpty(), "확인해도 대기 집합이 남아 다음 진입에 다시 축하한다")
        assertTrue(!ui.badgeCelebrationVisible)
    }

    // ── 카드 ──

    private fun render(badges: List<Badge>, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            NexusTheme { BadgeUnlockCard(badges = badges, visible = true, onDismiss = onDismiss) }
        }
    }

    @Test
    fun `한 개면 단수 제목과 배지 이름을 보여준다`() {
        render(listOf(badge("first_step", "첫걸음")))

        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_title)).assertIsDisplayed()
        // 이름·설명은 표에서 온다 — 목록과 같은 행 컴포넌트라 낭독도 같은 형태다 (#266)
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.a11y_badge_earned, "첫걸음", "첫걸음 설명"),
        ).assertExists()
    }

    @Test
    fun `여러 개면 묶어서 한 장으로 보여준다`() {
        render(listOf(badge("a", "첫걸음"), badge("b", "꾸준함"), badge("c", "탐험가")))

        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_title_multi, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_title)).assertDoesNotExist()
    }

    @Test
    fun `축하할 배지가 없으면 아무것도 그리지 않는다`() {
        render(emptyList())

        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_dismiss)).assertDoesNotExist()
    }

    @Test
    fun `확인을 누르면 콜백이 호출된다`() {
        var dismissed = 0
        render(listOf(badge("a", "첫걸음")), onDismiss = { dismissed++ })

        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_dismiss)).performClick()

        assertEquals(1, dismissed)
    }

    /**
     * 회전·프로세스 사망을 넘겨도 카드가 남아야 한다. 대기 집합이 영속이므로 **카드를 다시 세우면
     * 같은 배지가 그대로 온다** — 감지 시점에 소비했다면 여기서 사라진다.
     */
    @Test
    fun `복원 후에도 축하가 남는다`() {
        store().record(setOf("first_step"))
        val restorer = StateRestorationTester(composeRule)
        restorer.setContent {
            val pending = BadgeCelebrationStore(context, testPrefs).pending
            NexusTheme {
                BadgeUnlockCard(
                    badges = listOf(badge("first_step", "첫걸음")).filter { it.id in pending },
                    visible = true,
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_title)).assertIsDisplayed()

        restorer.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(context.getString(R.string.badge_unlock_title)).assertIsDisplayed()
    }
}
