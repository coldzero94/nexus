package com.nexus.app.health

import android.content.Context
import android.database.SQLException
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 워커 오케스트레이션 계약 (#234, E15-6) — `doWork`의 **예외 → Result 분류**를 못박는다.
 *
 * 이 분류는 백그라운드 성장의 로버스트니스 계약이다: 재시도해도 소용없는 실패(권한·잘못된 인자)를
 * retry로 두면 백오프 크래시 루프가 되고, 일시 실패(IO·원격·DB)를 failure로 두면 워커가 영구히
 * 죽는다. **둘 다 14일 알파 내내 조용히 진행되어 게이트 지표를 무너뜨린다** — 그래서 분기가
 * 뒤집히면 즉시 빨개져야 한다.
 *
 * 협력자는 [HealthSyncWorker.Seam]으로 교체한다(프로덕션 기본값 불변). 에뮬 불요(#232 하네스).
 */
@RunWith(RobolectricTestRunner::class)
class HealthSyncWorkerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun restoreSeam() {
        // 전역 시임이라 반드시 되돌린다 — 안 그러면 다음 테스트가 오염된다
        HealthSyncWorker.seam = HealthSyncWorker.Seam()
    }

    // ── HC 미가용: 재시도 무의미이므로 성공 처리하고 아무것도 건드리지 않는다 ──

    @Test
    fun unavailableHealthConnect_succeeds_withoutSyncingOrLedgerWrite() {
        var syncCalled = false
        var ledgerCalled = false
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { false },
            sync = { _, _ ->
                syncCalled = true
                outcome()
            },
            appendToLedger = { _, _ -> ledgerCalled = true },
        )

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertTrue(!syncCalled, "HC 미가용인데 동기화를 시도했다")
        assertTrue(!ledgerCalled, "HC 미가용인데 원장에 썼다 — 이중 지급 위험")
    }

    // ── 일시 실패 → retry (백오프로 다음 주기에 회복) ──

    @Test
    fun ioException_retries() = assertRetry(IOException("network"))

    @Test
    fun remoteException_retries() = assertRetry(RemoteException("binder"))

    @Test
    fun illegalStateException_retries() = assertRetry(IllegalStateException("bad state"))

    @Test
    fun sqlException_retries_insteadOfCrashLooping() = assertRetry(SQLException("ledger db"))

    // ── 영구 실패 → failure (재시도해도 같은 결과라 백오프만 낭비) ──

    @Test
    fun securityException_fails_withoutRetry() = assertFailure(SecurityException("permission revoked"))

    @Test
    fun illegalArgumentException_fails_withoutRetry() = assertFailure(IllegalArgumentException("bad record"))

    // ── 취소는 삼키지 않고 전파 ──

    @Test
    fun cancellation_isRethrown_notSwallowedAsResult() {
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> throw CancellationException("cancelled") },
        )
        // 취소를 Result로 바꿔 삼키면 WorkManager가 취소를 인지하지 못한다
        assertFailsWith<CancellationException> { runWorker() }
    }

    @Test
    fun cancellationFromLedgerStep_isAlsoRethrown() {
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> outcome() },
            appendToLedger = { _, _ -> throw CancellationException("cancelled") },
        )
        assertFailsWith<CancellationException> { runWorker() }
    }

    // ── 정상 경로: 성공 + 동기화 상태 갱신 + 원장 append ──

    @Test
    fun success_updatesSyncState_andAppendsToLedger() {
        val store = TokenStore(context)
        store.lastSyncEpochMillis = 0L
        store.lastChangeCount = 0
        var ledgerCalled = false
        var receivedDeletedIds: List<String>? = null
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> outcome(upserts = 3, deletions = 2, deletedIds = listOf("gone-1", "gone-2")) },
            appendToLedger = { _, ids ->
                ledgerCalled = true
                receivedDeletedIds = ids
            },
        )

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertNotEquals(0L, store.lastSyncEpochMillis, "마지막 동기화 시각이 갱신돼야 한다")
        assertEquals(5, store.lastChangeCount, "변경 수는 upserts + deletions")
        assertTrue(ledgerCalled, "정상 경로에서 원장 append가 불려야 한다")
        assertEquals(listOf("gone-1", "gone-2"), receivedDeletedIds, "삭제된 레코드 id가 그대로 전달돼야 한다")
    }

    @Test
    fun failedSync_doesNotUpdateSyncState() {
        val store = TokenStore(context)
        store.lastSyncEpochMillis = 0L
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> throw IOException("network") },
        )

        runWorker()
        // 실패했는데 "방금 동기화됨"으로 남으면 신선도 표시(#221)가 거짓말을 한다
        assertEquals(0L, store.lastSyncEpochMillis, "실패 시 마지막 동기화 시각을 갱신하면 안 된다")
    }

    // ── 이중 실행 멱등: 워커가 두 번 돌아도 원장 계약이 지켜진다(#163) ──

    @Test
    fun runningTwice_isIdempotentForLedger() {
        var appendCount = 0
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> outcome(upserts = 1) },
            appendToLedger = { _, _ -> appendCount++ },
        )

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(ListenableWorker.Result.success(), runWorker())
        // 워커는 매번 append를 부른다 — 중복 지급을 막는 건 원장의 멱등키다(RewardEventDaoContractTest).
        // 여기서 고정하는 계약은 "두 번 돌아도 워커가 실패하지 않는다"는 것.
        assertEquals(2, appendCount, "두 번 실행하면 append도 두 번 시도된다(멱등은 원장이 보장)")
    }

    // ── 계측 불변식: 백그라운드 경로가 건강 파생 수치를 흘리지 않는다 (②) ──

    @Test
    fun workerPath_emitsNoTelemetry_soHealthValuesCannotLeak() {
        // 워커는 걸음·운동시간·XP를 모두 손에 쥐고 있어 계측을 붙이기 쉬운 자리다. 지금은 아예
        // 호출하지 않으며, 누가 추가하면 이 테스트가 먼저 잡는다(파라미터는 정책상 봉인돼 있지만
        // 이벤트 이름 자체에 수치를 넣는 실수도 있으므로 "호출 없음"을 계약으로 고정).
        val methods = HealthSyncWorker::class.java.declaredMethods.map { it.name }
        assertTrue(
            methods.none { it.contains("telemetry", ignoreCase = true) },
            "워커에 계측 호출이 생겼다 — 건강 파생 수치가 실리지 않는지 확인하고 이 테스트를 갱신하세요",
        )
    }

    private fun assertRetry(error: Throwable) {
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> throw error },
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            runWorker(),
            "${error::class.simpleName}은 일시 실패라 retry여야 한다 — failure면 워커가 영구히 죽는다",
        )
    }

    private fun assertFailure(error: Throwable) {
        HealthSyncWorker.seam = HealthSyncWorker.Seam(
            isAvailable = { true },
            sync = { _, _ -> throw error },
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            runWorker(),
            "${error::class.simpleName}은 재시도해도 같은 결과라 failure여야 한다 — retry면 백오프 루프",
        )
    }

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<HealthSyncWorker>(context).build().doWork()
    }

    private fun outcome(upserts: Int = 0, deletions: Int = 0, deletedIds: List<String> = emptyList()) = SyncOutcome(
        tokenReset = false,
        upserts = upserts,
        deletions = deletions,
        deletedRecordIds = deletedIds,
    )
}
