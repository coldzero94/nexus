package com.nexus.app.harness

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.nexus.app.data.NexusDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 호스트측 테스트 하네스 캐너리 (#232, E15-4) — **에뮬레이터 없이** 실 Room·WorkManager·Compose가
 * JVM에서 도는지 확인한다. 이 셋이 살아 있어야 후속 데이터 무결성 테스트(#233 마이그레이션,
 * #234 워커 오케스트레이션, #235 원장 계약)를 쓸 수 있다.
 *
 * 지금까지 app 테스트는 전부 손수 만든 페이크였다(예: `RewardLedgerRepositoryTest`가 유니크 제약을
 * 수동 재현) — 진짜 구현과 어긋나면 테스트가 통과하면서 회귀를 놓친다. CI는 이미 ubuntu에서
 * `:app:testDebugUnitTest`를 에뮬 없이 돌리므로(STACK §5) Robolectric이 그대로 들어맞는다.
 *
 * **캐너리의 목적은 배관 확인**이다 — 여기서 실패하면 하네스가 깨진 것이지 앱 로직 문제가 아니다.
 */
@RunWith(RobolectricTestRunner::class)
class HostHarnessCanaryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun room_opensInMemory_andRoundTrips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 실 Room 스키마로 인메모리 DB — 페이크 DAO가 아니라 진짜 쿼리·제약이 돈다
        val db = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).build()
        try {
            val dao = db.rewardEventDao()
            runBlocking {
                // 빈 DB 조회가 예외 없이 도는지 — 스키마·DAO 배선 확인.
                // Room은 lazy라 첫 쿼리 시점에 연결이 열린다(빌더만으론 isOpen=false).
                assertEquals(0L, dao.count(), "빈 DB의 이벤트 수는 0이어야 한다")
            }
            assertTrue(db.isOpen, "쿼리 후에도 DB 연결이 열려 있지 않다")
        } finally {
            db.close()
        }
    }

    @Test
    fun worker_runsOnce_withTestBuilder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<CanaryWorker>(context).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result, "테스트 워커가 성공을 돌려주지 않았다")
    }

    @Test
    fun compose_rendersAndFindsNode() {
        composeRule.setContent { Text(CANARY_TEXT) }
        composeRule.onNodeWithText(CANARY_TEXT).assertIsDisplayed()
    }

    /** 하네스 확인 전용 워커 — 프로덕션 워커를 끌어들이지 않고 WorkManager 배선만 검증한다. */
    class CanaryWorker(context: Context, params: WorkerParameters) :
        androidx.work.CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = Result.success()
    }

    private companion object {
        const val CANARY_TEXT = "harness-canary"
    }
}
