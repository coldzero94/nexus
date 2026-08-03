package com.nexus.app.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.ui.LocalMotionScale
import com.nexus.app.ui.NexusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 첫 만남·부화 (#110, E7-6).
 *
 * 계약 셋: ① 두드리면 진행한다 ② 기다려도 진행한다(탭을 모르는 사용자가 갇히지 않게)
 * ③ 모션 감축이면 **이미 깨어난 상태**로 시작한다 — 연출은 없애되 서사는 남긴다.
 *
 * ③이 특히 중요하다. 흔들리는 알을 없애면서 "깨우기"까지 없애면 명명 단계로 넘어갈 수단이
 * 사라져 온보딩 첫 화면에서 갇힌다.
 */
@RunWith(RobolectricTestRunner::class)
class HatchSceneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    private fun render(motionScale: Float = 1f, onDone: () -> Unit = {}) {
        composeRule.setContent {
            NexusTheme {
                CompositionLocalProvider(LocalMotionScale provides motionScale) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { HatchSceneContent(onDone = onDone) }
                }
            }
        }
    }

    private fun tapEgg() = composeRule.onNodeWithContentDescription(string(R.string.hatch_tap_hint)).performClick()

    @Test
    fun `처음에는 알이고 다음 버튼이 없다`() {
        render()

        composeRule.onNodeWithText(string(R.string.hatch_title)).assertIsDisplayed()
        // 깨어나기 전에 넘어갈 수 있으면 부화가 장식이 된다
        composeRule.onNodeWithText(string(R.string.hatch_meet)).assertDoesNotExist()
    }

    @Test
    fun `세 번 두드리면 깨어난다`() {
        render()

        repeat(TAPS) { tapEgg() }

        composeRule.onNodeWithText(string(R.string.hatch_title_done)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.hatch_meet)).assertIsDisplayed()
    }

    @Test
    fun `두 번만 두드리면 아직 알이다`() {
        render()

        repeat(TAPS - 1) { tapEgg() }

        composeRule.onNodeWithText(string(R.string.hatch_title)).assertIsDisplayed()
    }

    /** 깨어난 뒤에는 더 두드릴 대상이 없다 — 알 상태를 넘어가는 인덱스는 아트가 없다. */
    @Test
    fun `깨어난 뒤에는 두드릴 수 없다`() {
        render()

        repeat(TAPS) { tapEgg() }

        composeRule.onNodeWithContentDescription(string(R.string.hatch_tap_hint)).assertDoesNotExist()
    }

    @Test
    fun `인사하기를 누르면 다음 스텝으로 간다`() {
        var done = 0
        render(onDone = { done++ })

        repeat(TAPS) { tapEgg() }
        composeRule.onNodeWithText(string(R.string.hatch_meet)).performScrollTo().performClick()

        assertEquals(1, done)
    }

    // ── AC: 기다려도 진행 ──

    /**
     * 탭을 모르는 사용자가 알 앞에서 갇히면 안 된다 — 온보딩 **첫 화면**이라 이탈이 곧 설치 손실이다.
     */
    @Test
    fun `두드리지 않고 기다려도 깨어난다`() {
        render()

        composeRule.mainClock.advanceTimeBy(AUTO_ADVANCE_TOTAL_MS)

        composeRule.onNodeWithText(string(R.string.hatch_title_done)).assertIsDisplayed()
    }

    // ── AC ③: 모션 감축 ──

    /**
     * 흔들리는 알을 없애면서 깨우는 수단까지 없애면 넘어갈 방법이 사라진다 —
     * 감축 시에는 **이미 깨어난 상태**로 시작해 바로 인사할 수 있어야 한다.
     */
    @Test
    fun `애니메이션 제거면 이미 깨어난 채로 시작한다`() {
        render(motionScale = 0f)

        composeRule.onNodeWithText(string(R.string.hatch_title_done)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.hatch_meet)).assertIsDisplayed()
    }

    /** 양성 대조 — 평소에는 알에서 시작한다(위 단언이 "늘 깨어 있다"로 참인 게 아니다). */
    @Test
    fun `평소에는 알에서 시작한다`() {
        render(motionScale = 1f)

        composeRule.onNodeWithText(string(R.string.hatch_title)).assertIsDisplayed()
    }

    private companion object {
        const val TAPS = 3

        /** 자동 진행 1.4초 × 3단계 + 여유. */
        const val AUTO_ADVANCE_TOTAL_MS = 6_000L
    }
}
