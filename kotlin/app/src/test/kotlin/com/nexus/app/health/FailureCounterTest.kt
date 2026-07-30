package com.nexus.app.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.core.FailureCategory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 연속 실패 카운터 (#239) — 디버그 도구(#245)와 운영 판단이 읽는 로컬 신호.
 *
 * "무엇이 **몇 번 연속** 실패했는가"가 핵심이다. 한 번 실패는 도즈·일시 오류로 흔하고, 같은 분류가
 * 반복될 때가 실제 고장이다. 원격 표본이 지연되거나 DSN이 없는 알파 초기에도 이 값은 기기에 있다.
 */
@RunWith(RobolectricTestRunner::class)
class FailureCounterTest {
    private lateinit var store: TokenStore

    @Before
    fun setUp() {
        store = TokenStore(ApplicationProvider.getApplicationContext<Context>())
        store.clearFailure()
    }

    @Test
    fun `같은 분류가 이어지면 횟수가 늘어난다`() {
        repeat(3) { store.recordFailure(FailureCategory.LEDGER_DB.name) }

        assertEquals(FailureCategory.LEDGER_DB.name, store.lastFailureCategory)
        assertEquals(3, store.consecutiveFailures)
    }

    @Test
    fun `분류가 바뀌면 1부터 다시 센다`() {
        repeat(3) { store.recordFailure(FailureCategory.SYNC_IO.name) }
        store.recordFailure(FailureCategory.SYNC_PERMISSION.name)

        assertEquals(FailureCategory.SYNC_PERMISSION.name, store.lastFailureCategory)
        assertEquals(1, store.consecutiveFailures, "다른 고장이 시작된 것이므로 이전 횟수를 물려받지 않는다")
    }

    @Test
    fun `성공하면 카운터가 지워진다`() {
        store.recordFailure(FailureCategory.SYNC_REMOTE.name)
        store.clearFailure()

        assertNull(store.lastFailureCategory, "회복했는데 마지막 실패가 남아 있으면 오진한다")
        assertEquals(0, store.consecutiveFailures)
    }
}
