package com.nexus.app.growth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 배지 영속화 계약 (#206) — 상시/월 한정 저장소 분리와 **불퇴행**(합집합만, 회수 없음)을 고정한다.
 *
 * 월 배지는 이번 달 신호를 매번 재평가해 표시하는데(세션 취소로 활동일이 줄거나 권한 회수로 걸음이
 * 0이 되는 일이 실제로 일어난다), 저장 없이 평가값만 쓰면 이미 얻은 배지가 다시 잠긴다. 그건
 * "캐릭터는 퇴행하지 않는다"는 제품 불변식 위반이라 저장소 쪽에 테스트를 건다.
 */
@RunWith(RobolectricTestRunner::class)
class BadgeProgressStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun monthlyStore() = BadgeProgressStore(context, BadgeProgressStore.MONTHLY_PREFS)

    @Test
    fun `획득은 합집합으로만 늘어난다 - 조건이 거짓이 돼도 회수되지 않는다`() {
        val store = monthlyStore()
        store.addEarned(setOf("jul_2026_passion"))
        // 다음 진입에서 조건이 거짓이라 빈 집합이 들어와도 기존 획득분은 남아야 한다
        store.addEarned(emptySet())
        assertTrue("jul_2026_passion" in monthlyStore().earned, "획득한 월 배지가 다시 잠겼다")
    }

    @Test
    fun `달이 바뀌어도 지난 달 획득분은 남는다`() {
        val store = monthlyStore()
        store.addEarned(setOf("jul_2026_passion"))
        store.addEarned(setOf("aug_2026_steps"))
        assertEquals(setOf("jul_2026_passion", "aug_2026_steps"), monthlyStore().earned)
    }

    @Test
    fun `상시 배지와 월 배지 저장소는 섞이지 않는다`() {
        BadgeProgressStore(context).addEarned(setOf("first_step"))
        monthlyStore().addEarned(setOf("jul_2026_passion"))

        assertEquals(setOf("first_step"), BadgeProgressStore(context).earned)
        assertEquals(setOf("jul_2026_passion"), monthlyStore().earned)
    }
}
