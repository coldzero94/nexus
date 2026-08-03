package com.nexus.app.home

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.ui.NexusTheme
import com.nexus.core.Anniversary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 기념일 카드 렌더·배선 (#111, E5-16).
 *
 * 배선을 소스로 고정하는 이유: `HomeUiController`는 성장 탭과 달리 테스트가 세울 시드 진입점이
 * 없고(#320은 성장 탭에만 있다) 실제 로드는 Health Connect를 타서 로보렉트릭에선 항상 실패한다.
 * 그래서 카드 자체는 컴포저블로 태우고, "홈이 그 카드를 쓴다"는 명제만 소스 가드로 막는다 —
 * 컴포넌트가 있는 것과 화면이 그걸 쓰는 건 다른 명제다(#268은 기능을 통째로 되돌려도 초록이었다).
 */
@RunWith(RobolectricTestRunner::class)
class AnniversaryCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = Anniversary(days = 100, title = "만난 지 백 일", body = "백 일. 세어보니까 좀 웃긴다.")

    private fun render(anniversary: Anniversary?, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            NexusTheme {
                Column { AnniversaryCard(anniversary, visible = true, onDismiss = onDismiss) }
            }
        }
    }

    @Test
    fun `기념일이 있으면 제목과 본문을 그린다`() {
        render(fixture)

        composeRule.onNodeWithText(fixture.title).assertIsDisplayed()
        composeRule.onNodeWithText(fixture.body).assertIsDisplayed()
    }

    /** 양성 대조 — 위 단언이 '항상 뜬다'로 통과하지 않는지. */
    @Test
    fun `기념일이 없으면 아무것도 안 그린다`() {
        render(null)

        composeRule.onNodeWithText(fixture.title).assertDoesNotExist()
    }

    /** 확인을 눌러야 소비된다 — 자동으로 사라지면 놓친 사람이 영영 못 본다. */
    @Test
    fun `확인을 누르면 콜백이 온다`() {
        var dismissed = 0
        render(fixture) { dismissed++ }

        val label = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.celebrate_dismiss)
        composeRule.onNodeWithText(label).performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `홈이 기념일 카드를 그리고 확인을 잇는다`() {
        val home = File(File("..").canonicalFile, "app/src/main/kotlin/com/nexus/app/home/HomeScreen.kt").readText()

        assertTrue("AnniversaryCard(ui.anniversary" in home, "홈이 기념일 카드를 안 그린다")
        assertTrue("ui.dismissAnniversary()" in home, "확인이 소비로 이어지지 않는다 — 매 진입마다 다시 뜬다")
    }
}
