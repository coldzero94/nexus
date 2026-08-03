package com.nexus.app.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.character.EquipStore
import com.nexus.app.settings.IdentityStore
import com.nexus.app.ui.NexusTheme
import com.nexus.core.EquipSlot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 캐릭터 만들기 스텝 (#42, E7-1).
 *
 * 계약은 셋이다: ① 이름은 **넘어갈 때** 저장 ② 장비는 **고르는 즉시** 저장 ③ 둘 다 **건너뛸 수 있다**.
 * 셋째가 특히 중요하다 — 필수로 만들면 권한 화면 앞에 관문이 하나 더 생기는 셈이라, 애착을 심으려던
 * 스텝이 오히려 이탈 지점이 된다.
 */
@RunWith(RobolectricTestRunner::class)
class CreateCharacterStepTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    @Before
    fun clearState() {
        IdentityStore(context).clear()
        EquipSlot.entries.forEach { EquipStore(context).setEquipped(it, null) }
    }

    /** 프로덕션 `StepScaffold`와 같은 스크롤 컨테이너 — 없으면 `performScrollTo`가 성립하지 않는다. */
    private fun render(onDone: () -> Unit = {}) {
        composeRule.setContent {
            NexusTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { CreateCharacterContent(onDone = onDone) }
            }
        }
    }

    @Test
    fun `이름을 적고 다음을 누르면 저장된다`() {
        render()

        composeRule.onNodeWithText(
            string(R.string.onboarding_create_name_label),
        ).performScrollTo().performTextInput("모찌")
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performScrollTo().performClick()

        assertEquals("모찌", IdentityStore(context).name)
    }

    /**
     * 입력 중에는 저장하지 않는다 — 매 글자 저장하면 중간 상태("모")가 새고, 넘어가지 않고 나간
     * 사용자에게 의도하지 않은 이름이 남는다.
     */
    @Test
    fun `적기만 하고 넘어가지 않으면 저장되지 않는다`() {
        render()

        composeRule.onNodeWithText(
            string(R.string.onboarding_create_name_label),
        ).performScrollTo().performTextInput("모찌")

        assertNull(IdentityStore(context).name)
    }

    @Test
    fun `빈 이름으로 넘어가도 저장되지 않는다`() {
        render()

        composeRule.onNodeWithText(string(R.string.onboarding_next)).performScrollTo().performClick()

        assertNull(IdentityStore(context).name, "빈 입력이 이름으로 저장됐다")
    }

    /** 건너뛰기는 아무것도 남기지 않고 다음으로 — 이름은 나중에 설정에서 지을 수 있다(#216). */
    @Test
    fun `건너뛰면 이름 없이 다음으로 간다`() {
        var done = 0
        render(onDone = { done++ })

        composeRule.onNodeWithText(string(R.string.onboarding_create_skip)).performScrollTo().performClick()

        assertEquals(1, done)
        assertNull(IdentityStore(context).name)
    }

    @Test
    fun `다음을 누르면 다음 스텝으로 간다`() {
        var done = 0
        render(onDone = { done++ })

        composeRule.onNodeWithText(string(R.string.onboarding_next)).performScrollTo().performClick()

        assertEquals(1, done)
    }

    // ── 꾸미기 ──

    /**
     * 장비는 고르는 즉시 저장한다. 미리보기가 곧 결과라 지연시킬 이유가 없고, 뒤로 갔다 와도
     * 선택이 남아야 한다("방금 고른 걸 다시 고르게" 하지 않는다 — #225가 목표 선택에서 배운 것).
     */
    @Test
    fun `장비를 고르면 즉시 저장된다`() {
        render()

        composeRule.onNodeWithText(FIRST_HEAD).performScrollTo().performClick()

        assertEquals("straw_hat", EquipStore(context).load().equippedId(EquipSlot.HEAD))
    }

    @Test
    fun `같은 장비를 다시 누르면 벗는다`() {
        render()

        composeRule.onNodeWithText(FIRST_HEAD).performScrollTo().performClick()
        composeRule.onNodeWithText(FIRST_HEAD).performScrollTo().performClick()

        assertNull(EquipStore(context).load().equippedId(EquipSlot.HEAD), "다시 눌러도 벗겨지지 않는다")
    }

    @Test
    fun `슬롯이 다르면 함께 착용된다`() {
        render()

        composeRule.onNodeWithText(FIRST_HEAD).performScrollTo().performClick()
        composeRule.onNodeWithText(FIRST_ACCESSORY).performScrollTo().performClick()

        val loadout = EquipStore(context).load()
        assertEquals("straw_hat", loadout.equippedId(EquipSlot.HEAD))
        assertEquals("red_scarf", loadout.equippedId(EquipSlot.ACCESSORY))
    }

    @Test
    fun `두 슬롯의 선택지가 모두 보인다`() {
        render()

        composeRule.onNodeWithText(string(R.string.equip_slot_head)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.equip_slot_accessory)).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val FIRST_HEAD = "밀짚모자"
        const val FIRST_ACCESSORY = "빨간 목도리"
    }
}
