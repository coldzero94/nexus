package com.nexus.app.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nexus.app.notify.NotificationSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 자가복구 배관 (#237) — 반복 호출이 중복을 만들지 않고, 오래 멈춘 경우에만 1회성을 넣는지.
 *
 * 이 배관이 조용히 고장나면 알파 14일 내내 데이터가 멈춘 채 게이트 판정이 내려간다. 로컬 온리라
 * 오프디바이스로 알아챌 방법이 없으므로 테스트가 유일한 안전망이다.
 */
@RunWith(RobolectricTestRunner::class)
class SyncSelfHealTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** 워커를 실행하지 않는 실행기 — 큐 상태만 보므로 실행이 방해된다(ManualSyncEnqueueTest와 동일 이유). */
    private val neverRuns = Executor { }

    @Before
    fun prepareWorkManager() {
        runCatching { WorkManager.getInstance(context) }.onFailure {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(neverRuns).build(),
            )
        }
        WorkManager.getInstance(context).run {
            cancelAllWork().result.get()
            pruneWork().result.get()
        }
        NotificationSettings(context).enabled = false
    }

    private fun infos(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    @Test
    fun `미연결이면 아무것도 넣지 않는다`() {
        SyncSelfHeal.onForeground(context, connected = false)

        assertTrue(infos(PERIODIC).isEmpty(), "미연결인데 주기 워커를 등록했다")
        assertTrue(infos(MANUAL).isEmpty())
    }

    @Test
    fun `포그라운드 반복 진입에도 주기 워커는 정확히 하나이고 교체되지 않는다`() {
        SyncSelfHeal.onForeground(context, connected = true)
        val first = infos(PERIODIC).single().id

        repeat(4) { SyncSelfHeal.onForeground(context, connected = true) }

        val after = infos(PERIODIC)
        assertEquals(1, after.size, "진입마다 워커가 쌓인다")
        // 기존 작업을 교체하면 진행 중 동기화가 취소되고 스케줄이 매 진입마다 뒤로 밀린다.
        // (KEEP과 UPDATE는 둘 다 id를 유지하므로 이 단언이 가려내는 건 REPLACE다 — 그게 위험한 쪽이다.)
        assertEquals(first, after.single().id, "기존 주기 워커가 새 요청으로 교체됐다")
    }

    @Test
    fun `주기 워커가 사라져도 다음 진입에 되살아난다`() {
        SyncSelfHeal.onForeground(context, connected = true)
        // WM DB 가지치기·강제 종료로 작업이 사라진 상황을 취소로 재현
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC).result.get()
        WorkManager.getInstance(context).pruneWork().result.get()

        SyncSelfHeal.onForeground(context, connected = true)

        assertEquals(1, infos(PERIODIC).count { !it.state.isFinished }, "무흔적 사망에서 복구되지 않았다")
    }

    @Test
    fun `알림이 꺼져 있으면 리마인더는 건드리지 않는다`() {
        NotificationSettings(context).enabled = false

        SyncSelfHeal.onForeground(context, connected = true)

        assertTrue(infos(REMINDER).isEmpty(), "알림을 끈 사용자에게 리마인더 워커를 되살렸다")
    }

    @Test
    fun `알림이 켜져 있으면 리마인더도 함께 되살린다`() {
        NotificationSettings(context).enabled = true

        SyncSelfHeal.onForeground(context, connected = true)

        assertEquals(1, infos(REMINDER).size)
    }

    @Test
    fun `한 번도 동기화 안 했으면 워치독이 발동한다`() {
        // 연결됐는데 비어 있는 상태 — 다음 주기(최대 15분)를 기다릴 이유가 없다
        assertTrue(SyncSelfHeal.isStale(lastSyncEpochMillis = 0L, nowEpochMillis = NOW))
    }

    @Test
    fun `신선하면 워치독은 조용하다`() {
        val recent = NOW - 10 * MINUTE
        assertFalse(SyncSelfHeal.isStale(recent, NOW))
    }

    @Test
    fun `임계 경계 — 도즈로 한두 번 밀리는 정도는 건드리지 않는다`() {
        val threshold = SyncSelfHeal.STALE_THRESHOLD_MINUTES
        assertFalse(SyncSelfHeal.isStale(NOW - (threshold - 1) * MINUTE, NOW))
        assertTrue(SyncSelfHeal.isStale(NOW - threshold * MINUTE, NOW))
    }

    @Test
    fun `오래 멈췄으면 1회성 동기화가 한 건 들어간다`() {
        TokenStore(context).lastSyncEpochMillis = System.currentTimeMillis() -
            (SyncSelfHeal.STALE_THRESHOLD_MINUTES + 30) * MINUTE

        SyncSelfHeal.onForeground(context, connected = true)

        assertEquals(1, infos(MANUAL).size)
    }

    @Test
    fun `신선하면 1회성은 넣지 않는다`() {
        TokenStore(context).lastSyncEpochMillis = System.currentTimeMillis() - 5 * MINUTE

        SyncSelfHeal.onForeground(context, connected = true)

        assertTrue(infos(MANUAL).isEmpty(), "신선한데 1회성을 넣으면 HC를 불필요하게 읽는다")
    }

    private companion object {
        const val PERIODIC = "nexus_health_sync"
        const val MANUAL = "nexus_health_sync_now"
        const val REMINDER = "nexus_evening_reminder"
        const val MINUTE = 60_000L
        const val NOW = 1_700_000_000_000L
    }
}
