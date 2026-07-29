package com.nexus.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Room 마이그레이션 하네스 (#233, E15-5) — **마이그레이션이 0개인 지금 미리 깔아두는 안전망.**
 *
 * 원장은 crown jewel이고, 알파 테스터는 이미 `RewardEvent`를 쌓고 있다. 첫 스키마 변경 때
 * 마이그레이션이 없으면 기존 테스터 앱은 DB open에서 `IllegalStateException`으로 **하드 크래시**하고
 * 쌓인 진척이 사라진다 — "캐릭터는 퇴행하지 않는다"는 제품 불변식을 기술적으로 위반하는 경로다.
 *
 * 지금은 v1뿐이라 검증할 마이그레이션이 없지만, 하네스가 **먼저 있어야** 버전을 올리는 PR에서
 * 테스트를 쓰는 게 자연스러워진다(규약: `.claude/rules/kotlin.md`). 나중에 깔면 이미 늦다.
 *
 * 에뮬 불요(#232 하네스). 스키마는 `app/schemas/`를 테스트 assets로 노출해 읽는다(build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
class NexusDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        NexusDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * export된 스키마로 v1 DB를 만들고 데이터를 넣은 뒤 다시 열 수 있는지 — 마이그레이션이 생기기
     * 전의 **기준선**이다. 이게 깨지면 스키마 export 자체나 헬퍼 배선이 망가진 것이다.
     */
    @Test
    fun v1_createsAndReopens_withDataIntact() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO reward_events
                  (idempotencyKey, xp, type, dataOrigin, recordingMethod, formulaVersion, epochMillis, epochDay)
                VALUES ('migration-seed', 42, 'GRANT', 'com.sec.android.app.shealth', 'AUTO_RECORDED', 1, 1700000000000, 20000)
                """.trimIndent(),
            )
        }

        // 같은 버전으로 다시 열기 — 데이터가 살아 있어야 한다
        helper.runMigrationsAndValidate(TEST_DB, 1, true).use { db ->
            db.query("SELECT xp, idempotencyKey FROM reward_events").use { cursor ->
                assertTrue(cursor.moveToFirst(), "v1에 넣은 행이 사라졌다")
                assertEquals(42, cursor.getInt(0))
                assertEquals("migration-seed", cursor.getString(1))
            }
        }
    }

    /**
     * 현재 코드의 `@Database(version)`이 export된 스키마와 일치하는지 — 버전을 올리면서 스키마를
     * 커밋하지 않으면(또는 그 반대면) 여기서 잡힌다.
     */
    @Test
    fun currentVersion_hasExportedSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).build()
        val currentVersion = try {
            runBlocking { db.rewardEventDao().count() } // 실제 오픈 강제
            db.openHelper.readableDatabase.version
        } finally {
            db.close()
        }

        val schema = javaClass.classLoader
            ?.getResourceAsStream("assets/com.nexus.app.data.NexusDatabase/$currentVersion.json")
            ?: javaClass.classLoader?.getResourceAsStream("com.nexus.app.data.NexusDatabase/$currentVersion.json")

        assertTrue(
            schema != null,
            "v$currentVersion 스키마 파일이 없다 — @Database version을 올렸다면 schemas/$currentVersion.json을 " +
                "커밋하고 Migration(${currentVersion - 1}→$currentVersion)과 이 클래스의 테스트를 함께 추가하세요",
        )
    }

    /**
     * 마이그레이션 목록이 비어 있는 동안에도 **정상 오픈**되는지 — 지금 상태(v1, 마이그레이션 0개)의
     * 계약이다. 버전이 올라가면 이 테스트는 실패하고, 그때 실제 마이그레이션 테스트를 추가해야 한다.
     */
    @Test
    fun productionBuilder_opensWithoutMigrations_whileOnV1() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NexusDatabase.get(context)
        val version = db.openHelper.readableDatabase.version
        assertEquals(
            1,
            version,
            "DB 버전이 올라갔다 — Migration을 addMigrations로 등록하고 이 테스트를 마이그레이션 검증으로 교체하세요",
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
