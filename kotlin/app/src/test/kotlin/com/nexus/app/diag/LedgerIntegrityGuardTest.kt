package com.nexus.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.health.TokenStore
import com.nexus.core.FailureCategory
import com.nexus.core.LedgerRow
import com.nexus.core.LedgerViolation
import com.nexus.core.RewardEventType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #245 — 무결성 가드의 **대응**. 판정 자체는 `LedgerIntegrityTest`(core)가 본다.
 *
 * 여기서 고정하는 계약은 셋이다:
 * 1. 자동 경로는 던지지 않는다 — 던지면 동기화가 조용한 무한 재시도가 되고, 시작 경로에선
 *    디버그 시더로 심은 위반을 되돌릴 화면에 닿을 수 없다.
 * 2. 무결성 신호는 **동기화 실패 슬롯을 건드리지 않는다** — 그 슬롯은 하나뿐이라 회복되지 않는
 *    신호가 들어오면 `SYNC_PERMISSION`(사용자가 고칠 수 있는 유일한 분류)을 영구히 가린다.
 * 3. 같은 위반을 반복 보고하지 않는다 — 무결성 위반은 회복되지 않으므로 매번 보내면
 *    한 대의 기기가 Sentry 무료 티어를 혼자 태운다.
 */
@RunWith(RobolectricTestRunner::class)
class LedgerIntegrityGuardTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearState() {
        context.getSharedPreferences(TokenStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(IntegrityMarkerStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun grant(sequence: Long, key: String) = LedgerRow(sequence, key, 100, RewardEventType.GRANT, 1, 20_000L)

    private fun orphanCancel() = LedgerRow(1, "없는키", -100, RewardEventType.CANCELLATION, 1, 20_000L)

    private fun duplicateGrant() = listOf(grant(1, "a"), grant(2, "a"))

    @Test
    fun `정상 원장이면 아무 흔적도 남기지 않는다`() {
        val violations = LedgerIntegrityGuard.report(context, listOf(grant(1, "a")))

        assertTrue(violations.isEmpty())
        assertTrue(IntegrityMarkerStore(context).lastViolations.isEmpty())
    }

    @Test
    fun `위반이면 던지지 않고 전용 마커에 남긴다`() {
        val violations = LedgerIntegrityGuard.report(context, listOf(orphanCancel()))

        assertEquals(setOf(LedgerViolation.ORPHAN_CANCELLATION), violations)
        assertEquals(setOf("ORPHAN_CANCELLATION"), IntegrityMarkerStore(context).lastViolations)
    }

    /**
     * 이게 계약 2다. 무결성 위반을 `TokenStore`에 쓰면 매 실행마다 `SYNC_PERMISSION`을 덮고
     * 연속 횟수를 1로 되돌린다 — 진단에서 가장 먼저 보는 값이 영구히 가려진다.
     */
    @Test
    fun `무결성 위반은 동기화 실패 분류를 덮지 않는다`() {
        val store = TokenStore(context)
        store.recordFailure(FailureCategory.SYNC_PERMISSION.name)
        store.recordFailure(FailureCategory.SYNC_PERMISSION.name)

        LedgerIntegrityGuard.report(context, listOf(orphanCancel()))

        assertEquals(FailureCategory.SYNC_PERMISSION.name, store.lastFailureCategory)
        assertEquals(2, store.consecutiveFailures, "연속 횟수도 초기화되면 안 된다")
    }

    /**
     * 계약 2의 반대 방향. 동기화가 성공하면 워커가 `clearFailure()`를 부르는데, 무결성 신호가 같은
     * 저장소에 있으면 방금 기록한 것까지 함께 지워진다 — 원장이 깨진 채로 신호만 사라진다.
     */
    @Test
    fun `동기화 성공이 무결성 마커를 지우지 않는다`() {
        LedgerIntegrityGuard.report(context, listOf(orphanCancel()))

        TokenStore(context).clearFailure()

        assertEquals(setOf("ORPHAN_CANCELLATION"), IntegrityMarkerStore(context).lastViolations)
    }

    /** 계약 3 — 같은 위반이 그대로면 원격 보고는 한 번만. */
    @Test
    fun `같은 위반 집합은 한 번만 원격 보고 대상이 된다`() {
        val marker = IntegrityMarkerStore(context)
        val violations = setOf(LedgerViolation.ORPHAN_CANCELLATION)

        assertTrue(marker.record(violations), "처음이면 보고 대상")
        assertFalse(marker.record(violations), "같은 집합이면 보고하지 않는다")
        assertFalse(marker.record(violations))
    }

    @Test
    fun `위반 집합이 늘면 다시 보고 대상이 된다`() {
        val marker = IntegrityMarkerStore(context)
        marker.record(setOf(LedgerViolation.ORPHAN_CANCELLATION))

        val grown = setOf(LedgerViolation.ORPHAN_CANCELLATION, LedgerViolation.DUPLICATE_GRANT)

        assertTrue(marker.record(grown), "새 위반이 생겼으면 알아야 한다")
    }

    @Test
    fun `정상으로 돌아오면 보고 대상이 아니고 마커도 비워진다`() {
        val marker = IntegrityMarkerStore(context)
        marker.record(setOf(LedgerViolation.ORPHAN_CANCELLATION))

        // '회복'을 원격으로 알릴 필요는 없다 — 우리가 뭔가 한 결과이고, 안 왔다고 나쁜 일도 없다
        assertFalse(marker.record(emptySet()))
        assertTrue(marker.lastViolations.isEmpty())
    }

    @Test
    fun `빈 원장은 위반이 아니다`() {
        // 신규 설치가 매 실행 신호를 남기면 그 신호는 무의미해진다
        assertTrue(LedgerIntegrityGuard.report(context, emptyList()).isEmpty())
        assertTrue(IntegrityMarkerStore(context).lastViolations.isEmpty())
    }

    @Test
    fun `명시적 검사는 디버그에서 던진다`() {
        // 이 테스트는 debug 변형으로 돌므로 BuildConfig.DEBUG = true
        assertFailsWith<IllegalStateException> {
            LedgerIntegrityGuard.verifyOrCrash(context, duplicateGrant())
        }
    }

    @Test
    fun `명시적 검사도 정상 원장이면 던지지 않는다`() {
        assertTrue(LedgerIntegrityGuard.verifyOrCrash(context, listOf(grant(1, "a"))).isEmpty())
    }

    @Test
    fun `명시적 검사도 마커를 남긴다`() {
        // 크래시 뒤 재시작했을 때 무엇이었는지 알 수 있어야 한다
        runCatching { LedgerIntegrityGuard.verifyOrCrash(context, listOf(orphanCancel())) }

        assertEquals(setOf("ORPHAN_CANCELLATION"), IntegrityMarkerStore(context).lastViolations)
    }
}
