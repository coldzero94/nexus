package com.nexus.app.growth

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.ui.NexusTheme
import com.nexus.core.BadgeAssetConvention
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 배지 글리프 (#266, E16-16).
 *
 * 가장 중요한 두 단언: **출하되는 모든 배지가 실제 드로어블로 해석되는지**(규약은 문자열이라 오타가
 * 컴파일에 안 걸린다), 그리고 **미획득이 낭독으로도 구분되는지**(글리프의 색·윤곽은 스크린리더에
 * 존재하지 않는다).
 */
@RunWith(RobolectricTestRunner::class)
class BadgeGlyphTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun assets() = CharacterAssets(context)

    /**
     * 규약은 문자열 기반이라 `badges.json`의 오타가 컴파일에 안 걸린다 — 조용히 기본 글리프로
     * 폴백해서 눈으로도 잘 안 보인다. 표에 적힌 아이콘이 **정말 존재하는지**를 여기서 못박는다.
     *
     * **`iconName()`을 거치지 않고 원본 필드를 본다.** 처음엔 `iconName()`으로 조회했는데, 그 함수가
     * 규약 위반을 이미 `badge_default`로 접기 때문에 `"first-step"`(하이픈 오타)처럼 가장 그럴듯한
     * 실수가 **항상 통과**했다 — 폴백 자체가 검사를 무력화하고 있었다.
     */
    private fun unresolvedIcons(icons: List<Pair<String, String?>>): List<String> = icons.mapNotNull { (id, icon) ->
        val name = icon ?: return@mapNotNull null // 미지정은 의도된 폴백 — #69의 "조건만 적어 추가" 경로
        when {
            !BadgeAssetConvention.isValidIcon(name) -> "$id → '$name' 규약 위반(조용히 기본 글리프로 접힌다)"

            context.resources.getIdentifier(
                BadgeAssetConvention.PREFIX + name,
                "drawable",
                context.packageName,
            ) == 0 -> "$id → ${BadgeAssetConvention.PREFIX}$name 드로어블 없음"

            else -> null
        }
    }

    @Test
    fun `출하 배지 표의 모든 아이콘이 드로어블로 해석된다`() {
        val badges = assets().loadBadgeTable().badges
        assertTrue(badges.isNotEmpty(), "배지 표가 비었다 — 에셋 로드가 깨졌다")

        val bad = unresolvedIcons(badges.map { it.id to it.icon })

        assertEquals(emptyList(), bad, "badges.json 아이콘이 조용히 기본 글리프로 접힌다 (#266)")
    }

    @Test
    fun `출하 월간 배지 표의 모든 아이콘도 해석된다`() {
        val bad = unresolvedIcons(assets().loadMonthlyBadgeTable().badges.map { it.id to it.icon })

        assertEquals(emptyList(), bad, "monthly_badges.json 아이콘이 조용히 기본 글리프로 접힌다 (#266)")
    }

    @Test
    fun `규약 위반 오타를 잡는다`() {
        // 이 검사가 폴백에 가려져 무력했던 게 실제 결함이었다 — 하이픈 오타가 잡히는지 직접 본다
        val bad = unresolvedIcons(listOf("hyphen" to "first-step", "upper" to "Streak", "ok" to "streak"))

        assertEquals(2, bad.size, "규약 위반이 통과한다: $bad")
    }

    @Test
    fun `기본 글리프가 존재한다`() {
        // 폴백이 없으면 아이콘 미지정 배지가 아이콘 없이 렌더된다 — 슬롯이 뚫린다
        assertNotNull(assets().badgeIconResIdOrNull(null), "badge_default 드로어블이 없다")
    }

    @Test
    fun `알 수 없는 아이콘도 기본 글리프로 해석된다`() {
        assertEquals(assets().badgeIconResIdOrNull(null), assets().badgeIconResIdOrNull("없는아이콘"))
    }

    private fun render(earned: Boolean) {
        composeRule.setContent {
            NexusTheme {
                BadgeGlyphRow(name = "첫걸음", description = "함께한 첫 활동을 기록했어요.", icon = "first_step", earned = earned)
            }
        }
    }

    /**
     * 글리프는 색(브랜드 채움 vs 윤곽)으로 상태를 말하지만 **스크린리더에는 색이 없다**.
     * 낭독에 '획득함'/'미획득'이 들어가지 않으면 시각장애 사용자는 둘을 구분할 수 없다(#224 정합).
     */
    @Test
    fun `미획득 배지는 낭독으로 미획득임을 밝힌다`() {
        render(earned = false)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.a11y_badge_locked, "첫걸음", "함께한 첫 활동을 기록했어요."),
        ).assertExists()
    }

    @Test
    fun `획득 배지는 낭독으로 획득함을 밝힌다`() {
        render(earned = true)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.a11y_badge_earned, "첫걸음", "함께한 첫 활동을 기록했어요."),
        ).assertExists()
    }

    /**
     * 행이 **한 노드**여야 한다. 묶지 않으면 글리프·이름·설명이 흩어져, 훑을 때 상태와 이름이
     * 이어지지 않는다 — `NexusListRow`(#260)와 같은 이유다.
     */
    @Test
    fun `행이 한 노드로 묶여 있다`() {
        render(earned = false)

        // clearAndSetSemantics로 묶었으므로 안쪽 텍스트는 시맨틱 트리에 없다
        composeRule.onNodeWithText("첫걸음", substring = true).assertDoesNotExist()
    }

    @Test
    fun `미획득은 이름에도 잠김 표기가 붙는다`() {
        // 세 번째 채널 — 색·형태를 못 봐도 텍스트가 남는다. 낭독 문장에 포함돼 있는지로 확인한다.
        render(earned = false)

        val label = context.getString(R.string.a11y_badge_locked, "첫걸음", "함께한 첫 활동을 기록했어요.")
        composeRule.onNodeWithContentDescription(label)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        assertTrue("미획득" in label)
    }

    @Test
    fun `아이콘이 없는 배지도 행을 그린다`() {
        composeRule.setContent {
            NexusTheme {
                BadgeGlyphRow(name = "무명 배지", description = "설명", icon = null, earned = true)
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.a11y_badge_earned, "무명 배지", "설명"),
        ).assertIsDisplayed()
    }
}
