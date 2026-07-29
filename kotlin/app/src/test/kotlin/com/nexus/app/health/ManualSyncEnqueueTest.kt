package com.nexus.app.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * '지금 확인' 중복 실행 가드 (#221) — 완료 기준에 명시된 계약이라 테스트로 고정한다.
 *
 * 사용자는 반응이 없어 보이면 연타한다. 누를 때마다 워크가 쌓이면 Health Connect 읽기가 중복
 * 실행되고(레이트리밋 대상), 백오프 재시도까지 겹치면 배터리로 되돌아온다. 가드는
 * `ExistingWorkPolicy.KEEP` 하나지만, 정책 상수는 조용히 바뀔 수 있어 동작으로 못 박는다.
 *
 * 에뮬 불요(#232 하네스) — WorkManagerTestInitHelper로 인메모리 WorkManager를 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
class ManualSyncEnqueueTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * 워커를 **실행하지 않는** 실행기 — 큐에 넣은 워크가 ENQUEUED로 남는다.
     *
     * `SynchronousExecutor`를 쓰면 각 요청이 다음 요청 전에 끝나버려 KEEP의 "진행 중이면 버린다"
     * 분기를 한 번도 타지 않는다. 그 상태에선 정책을 REPLACE로 바꿔도 테스트가 통과해(같은 이름의
     * 완료된 워크가 정리될 뿐) 가드를 전혀 고정하지 못한다.
     */
    private val neverRuns = Executor { /* 실행하지 않는다 — 워크를 대기 상태로 붙잡아 둔다 */ }

    @Before
    fun initWorkManager() {
        val config = Configuration.Builder().setExecutor(neverRuns).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun manualWorkInfos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(MANUAL_WORK_NAME).get()

    @Test
    fun `진행 중에 연타해도 같은 워크 하나만 남는다`() {
        HealthSyncWorker.enqueueNow(context)
        val firstId = manualWorkInfos().single().id
        assertTrue(
            manualWorkInfos().none { it.state.isFinished },
            "선행 조건: 첫 워크가 아직 끝나지 않아야 KEEP 분기를 탄다",
        )

        HealthSyncWorker.enqueueNow(context)
        HealthSyncWorker.enqueueNow(context)

        val infos = manualWorkInfos()
        assertEquals(1, infos.size, "중복 가드가 없으면 누른 만큼 워크가 쌓인다")
        assertEquals(firstId, infos.single().id, "진행 중인 요청이 새 요청으로 대체됐다 — 동기화가 중간에 끊긴다")
    }

    @Test
    fun `주기 워커와는 별개 이름이라 서로를 취소하지 않는다`() {
        HealthSyncWorker.enqueuePeriodic(context)
        HealthSyncWorker.enqueueNow(context)

        val periodic = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PERIODIC_WORK_NAME).get()
        assertEquals(1, periodic.size, "수동 실행이 주기 워커를 대체해버리면 백그라운드 동기화가 죽는다")
        assertEquals(1, manualWorkInfos().size)
    }

    @Test
    fun `완료된 뒤에는 새 워크가 만들어진다`() {
        // 이 검증만 워커가 실제로 끝나야 한다 — 즉시 실행기로 갈아끼운다(HC 미가용 → success)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        HealthSyncWorker.enqueueNow(context)
        assertTrue(manualWorkInfos().all { it.state.isFinished }, "선행 조건: 첫 워크가 끝나 있어야 한다")
        val first = manualWorkInfos().map { it.id }.toSet()

        HealthSyncWorker.enqueueNow(context)

        // KEEP은 '진행 중'만 막는다 — 끝난 뒤 재요청까지 무시하면 새로고침이 평생 한 번만 먹는다
        val added = manualWorkInfos().map { it.id }.toSet() - first
        assertTrue(added.isNotEmpty(), "완료 후 다시 눌렀는데 새 워크가 생기지 않았다")
    }

    private companion object {
        const val MANUAL_WORK_NAME = "nexus_health_sync_now"
        const val PERIODIC_WORK_NAME = "nexus_health_sync"
    }
}
