package com.nexus.app.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nexus.app.R
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/**
 * 설정 화면 섹션 구조 (#264, E16-14).
 *
 * Robolectric에는 Health Connect가 없어 연동 카드는 미연결 상태로 렌더되지만, 이 티켓의 검증
 * 대상은 **그룹 구조와 파괴적 액션 분리**라 로드 상태와 무관하다.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenRenderTest {
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

    private fun render() {
        composeRule.setContent {
            NexusTheme { SettingsScreen(manager = HealthConnectManager(context)) }
        }
    }

    private fun string(id: Int) = context.getString(id)

    private val sectionLabels = listOf(
        R.string.settings_section_connection,
        R.string.settings_section_activity,
        R.string.settings_section_data,
        R.string.settings_section_danger,
    )

    @Test
    fun `섹션 라벨이 모두 보인다`() {
        render()

        sectionLabels.forEach { label ->
            composeRule.onNodeWithText(string(label)).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * 섹션 라벨이 **표제**여야 한다 — `A11Y-TALKBACK` 6번의 표제 단위 이동에서 그룹 경계로 잡히지
     * 않으면, 화면에 보이는 구조가 스크린리더 사용자에게는 존재하지 않는다.
     */
    @Test
    fun `섹션 라벨이 표제로 표시된다`() {
        render()

        sectionLabels.forEach { label ->
            composeRule.onNodeWithText(string(label))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }

    /**
     * 삭제 카드의 제목과 버튼은 **같은 문구**("모든 데이터 삭제")라 텍스트만으로는 노드가 둘이다.
     * 클릭 가능 여부로 구분한다 — 눌리는 쪽이 버튼이다.
     */
    private fun deleteButton() =
        composeRule.onNode(hasText(string(R.string.settings_delete_button)) and hasClickAction())

    @Test
    fun `파괴적 액션이 구분선 아래 별 섹션으로 떼어져 있다`() {
        render()

        composeRule.onNodeWithTag(DANGER_DIVIDER_TAG).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_section_danger)).performScrollTo().assertIsDisplayed()
        // 제목이 아니라 설명으로 카드 존재를 본다(제목은 버튼과 문구가 겹친다)
        composeRule.onNodeWithText(string(R.string.settings_delete_desc)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `모든 설정 카드가 여전히 렌더된다`() {
        // 그룹화하면서 카드를 빠뜨리는 게 이 리팩터의 유일한 실질 위험이다
        render()

        // 위젯 카드는 제외한다 — 핀 미지원 런처에서 스스로 숨는 설계이고(#40 리뷰 N1)
        // Robolectric은 미지원으로 보고한다. 문구가 버튼과 겹치는 삭제 카드는 설명으로 본다.
        listOf(
            R.string.settings_health_title,
            R.string.settings_name,
            R.string.settings_rest_mode,
            R.string.settings_reminder,
            R.string.settings_goal,
            R.string.settings_backup_title,
            R.string.settings_open_days,
            R.string.settings_delete_desc,
        ).forEach { title ->
            composeRule.onNodeWithText(string(title)).performScrollTo().assertIsDisplayed()
        }
    }

    /** 삭제는 반드시 확인 다이얼로그를 지나야 한다 — 분리 작업이 그 경로를 건드리지 않았는지. */
    @Test
    fun `삭제 버튼은 확인 다이얼로그를 띄운다`() {
        render()

        deleteButton().performScrollTo().performClick()

        composeRule.onNodeWithText(string(R.string.delete_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.delete_confirm_no)).assertIsDisplayed()
    }

    @Test
    fun `확인 다이얼로그를 취소하면 닫힌다`() {
        render()
        deleteButton().performScrollTo().performClick()

        composeRule.onNodeWithText(string(R.string.delete_confirm_no)).performClick()

        composeRule.onNodeWithText(string(R.string.delete_confirm_title)).assertDoesNotExist()
    }
}
