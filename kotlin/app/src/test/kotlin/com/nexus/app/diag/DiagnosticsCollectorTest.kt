package com.nexus.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardEventEntity
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.TokenStore
import com.nexus.core.DiagnosticsKey
import com.nexus.core.DiagnosticsValue
import com.nexus.core.FailureCategory
import com.nexus.core.FailureStreak
import com.nexus.core.RecordingMethod
import com.nexus.core.RewardEventType
import com.nexus.core.SyncFreshnessBucket
import com.nexus.core.XpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #245 — 진단 스냅샷 수집. Robolectric에는 Health Connect가 없으므로 가용성은 항상 `Unavailable`이고
 * 권한은 빈 집합이다. 그 상태에서도 **스냅샷이 조립되고 건강 수치가 없다**는 것이 검증 대상이다.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsCollectorTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = 1_700_000_000_000L

    @Before
    fun clearState() {
        context.getSharedPreferences(TokenStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        // clearAllTables는 블로킹이라 메인 스레드에서 던진다 — 앱의 DevResets.clearLedger와 같은 이유
        runBlocking(Dispatchers.IO) { NexusDatabase.get(context).clearAllTables() }
    }

    private suspend fun collect() = DiagnosticsCollector.collect(context, HealthConnectManager(context), now)

    private suspend fun insert(
        key: String,
        xp: Int = 40,
        day: Long = 20_000L,
        type: RewardEventType = RewardEventType.GRANT,
        version: Int = XpEngine.FORMULA_VERSION,
    ) = NexusDatabase.get(context).rewardEventDao().insert(
        RewardEventEntity(
            idempotencyKey = key,
            xp = xp,
            type = type.name,
            dataOrigin = "test",
            recordingMethod = RecordingMethod.MANUAL_ENTRY.name,
            formulaVersion = version,
            epochMillis = day * 86_400_000L,
            epochDay = day,
        ),
    )

    @Test
    fun `빈 상태에서도 스냅샷이 조립된다`() = runTest {
        val fields = collect()

        assertEquals(DiagnosticsValue.Flag(true), fields[DiagnosticsKey.LEDGER_EMPTY])
        assertEquals(DiagnosticsValue.Flag(true), fields[DiagnosticsKey.LEDGER_INTEGRITY_OK])
        assertEquals(DiagnosticsValue.Flag(false), fields[DiagnosticsKey.HAS_CHANGES_TOKEN])
        assertEquals(
            DiagnosticsValue.Choice(SyncFreshnessBucket.NEVER.name),
            fields[DiagnosticsKey.SYNC_FRESHNESS],
        )
    }

    @Test
    fun `실패 기록이 없으면 연속 실패는 NONE이고 마지막 분류 키가 빠진다`() = runTest {
        val fields = collect()

        // 없는 실패를 빈 문자열로 채우면 "무언가 실패했다"로 오독된다 — 키를 아예 넣지 않는다
        assertTrue(DiagnosticsKey.LAST_FAILURE !in fields)
        assertEquals(DiagnosticsValue.Choice(FailureStreak.NONE.name), fields[DiagnosticsKey.FAILURE_STREAK])
    }

    @Test
    fun `실패를 기록하면 분류와 구간이 실린다`() = runTest {
        val store = TokenStore(context)
        repeat(FailureStreak.PERSISTENT_THRESHOLD) { store.recordFailure(FailureCategory.SYNC_IO.name) }

        val fields = collect()

        assertEquals(DiagnosticsValue.Choice(FailureCategory.SYNC_IO.name), fields[DiagnosticsKey.LAST_FAILURE])
        assertEquals(DiagnosticsValue.Choice(FailureStreak.PERSISTENT.name), fields[DiagnosticsKey.FAILURE_STREAK])
    }

    @Test
    fun `동기화 시각이 있으면 구간으로 접힌다`() = runTest {
        TokenStore(context).lastSyncEpochMillis = now - 10 * 60_000L

        assertEquals(
            DiagnosticsValue.Choice(SyncFreshnessBucket.FRESH.name),
            collect()[DiagnosticsKey.SYNC_FRESHNESS],
        )
    }

    @Test
    fun `원장이 깨지면 무결성 플래그가 내려간다`() = runTest {
        insert("orphan", xp = -40, type = RewardEventType.CANCELLATION)

        val fields = collect()

        assertEquals(DiagnosticsValue.Flag(false), fields[DiagnosticsKey.LEDGER_INTEGRITY_OK])
        assertEquals(DiagnosticsValue.Flag(false), fields[DiagnosticsKey.LEDGER_EMPTY])
    }

    @Test
    fun `산식 버전이 섞이면 단일 버전 플래그가 내려간다`() = runTest {
        insert("a", version = 1, day = 20_000L)
        insert("b", version = 2, day = 20_001L)

        assertEquals(DiagnosticsValue.Flag(false), collect()[DiagnosticsKey.SINGLE_FORMULA_VERSION])
    }

    /**
     * 이 테스트가 불변식 ②·③의 실제 방어다. 스냅샷 어디에도 원장 XP가 나타나지 않아야 한다 —
     * 값을 비교하지 않고 **자릿수 자체를 찾는다**. 어떤 키로 실리든 걸리게.
     */
    @Test
    fun `평문 스냅샷에 원장 XP 수치가 나타나지 않는다`() = runTest {
        insert("a", xp = 12_345, day = 20_000L)

        val text = DiagnosticsCollector.renderText(context, HealthConnectManager(context), now)

        assertTrue("12345" !in text, "원장 XP가 스냅샷에 실렸다: $text")
        assertTrue("12,345" !in text)
    }

    @Test
    fun `평문 스냅샷의 값은 플래그와 enum 이름과 식별자뿐이다`() = runTest {
        insert("a", xp = 12_345)
        TokenStore(context).lastSyncEpochMillis = now - 500 * 60_000L

        val lines = DiagnosticsCollector.renderText(context, HealthConnectManager(context), now)
            .lines()
            .filter { it.isNotBlank() }
        val idKeys = setOf(DiagnosticsKey.BUILD_ID.name, DiagnosticsKey.INSTALL_ID.name)

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            val (key, value) = line.split("=", limit = 2)
            // 식별자만 숫자를 담을 수 있다(버전·UUID). 나머지 값에 숫자가 있으면 수치가 새어 나간 것이다
            if (key !in idKeys) {
                assertTrue(value.none(Char::isDigit), "진단 값에 수치가 있다: $line")
            }
        }
    }

    @Test
    fun `원장 XP가 커도 스냅샷 키 집합은 변하지 않는다`() = runTest {
        val small = collect().keys
        insert("a", xp = 999_999)

        assertEquals(small, collect().keys)
    }
}
