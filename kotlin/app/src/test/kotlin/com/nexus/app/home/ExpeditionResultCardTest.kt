package com.nexus.app.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.ui.NexusTheme
import com.nexus.core.ExpeditionReward
import com.nexus.core.ExpeditionRewardPicker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 원정 개봉 연출 (#68, E5-7).
 *
 * 개봉이 상태만 지우고 카운터만 올리고 있어서, 8시간을 기다린 것에 대한 답이 없었다.
 * `docs/MVP.md`가 원정에 준 역할(하루 2~3회 재방문 · 동기화 지연 흡수)은 "돌아와서 뭔가 있었다"는
 * 경험이 있어야 성립한다.
 */
@RunWith(RobolectricTestRunner::class)
class ExpeditionResultCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val sample = ExpeditionReward(id = "quiet_trail", title = "조용한 오솔길", body = "발소리만 들렸대요.")

    private fun render(reward: ExpeditionReward?, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            NexusTheme { ExpeditionResultCard(reward = reward, onDismiss = onDismiss) }
        }
    }

    @Test
    fun `보상이 있으면 제목과 이야기를 보여준다`() {
        render(sample)

        composeRule.onNodeWithText(context.getString(R.string.expedition_result_title)).assertIsDisplayed()
        composeRule.onNodeWithText("조용한 오솔길").assertIsDisplayed()
        composeRule.onNodeWithText("발소리만 들렸대요.").assertIsDisplayed()
    }

    @Test
    fun `보상이 없으면 아무것도 그리지 않는다`() {
        render(null)

        composeRule.onNodeWithText(context.getString(R.string.expedition_result_title)).assertDoesNotExist()
    }

    @Test
    fun `확인을 누르면 콜백이 호출된다`() {
        var dismissed = 0
        render(sample, onDismiss = { dismissed++ })

        composeRule.onNodeWithText(context.getString(R.string.expedition_result_dismiss)).performClick()

        assertEquals(1, dismissed)
    }

    // ── 출하 표 ──

    /**
     * 표가 깨져 있으면 개봉이 보상 없이 끝난다 — 화면에는 아무 차이가 없어 눈으로도 안 보인다.
     * 파싱 검증(빈 표·가중치 0·중복 id)은 `ExpeditionRewardTest`가 덮고, 여기서는 **출하되는
     * 실제 에셋**이 그 검증을 통과하는지 본다.
     */
    @Test
    fun `출하 보상 표가 파싱된다`() {
        val table = CharacterAssets(context).loadExpeditionRewards()

        assertTrue(table.rewards.isNotEmpty())
        assertTrue(table.rewards.all { it.title.isNotBlank() && it.body.isNotBlank() }, "빈 카피가 있다")
    }

    @Test
    fun `출하 표의 모든 보상이 뽑힐 수 있다`() {
        // 가중치가 있는데 절대 안 뽑히는 보상이 있으면 표가 거짓말을 하는 것이다
        val table = CharacterAssets(context).loadExpeditionRewards()
        val total = table.rewards.sumOf { it.weight }

        val picked = (0L until total.toLong()).map { ExpeditionRewardPicker.pick(table, it).id }.toSet()

        assertEquals(table.rewards.map { it.id }.toSet(), picked)
    }
}
