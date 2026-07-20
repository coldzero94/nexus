package com.nexus.app.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 백업 스키마 고정 (#51, E8-6) — 라운드트립 + 버전 거부 + **키 allowlist**(원본 건강 수치
 * 필드가 스키마에 슬쩍 들어오면 여기서 걸린다 — BACKEND.md §2 데이터 경계).
 */
class BackupCodecTest {

    private val sample = BackupPayload(
        backupVersion = BackupCodec.VERSION,
        exportedAtEpochMillis = 1_784_500_000_000,
        events = listOf(
            BackupEvent("hc-abc", 120, "EXERCISE", "com.sec.android.app.shealth", "AUTO", 1, 1_784_400_000_000, 20653),
            BackupEvent("hc-abc", -120, "CANCEL", "com.sec.android.app.shealth", "AUTO", 1, 1_784_450_000_000, 20653),
        ),
        snapshot = BackupSnapshot(
            energyTotalSpent = 6,
            expeditionStartedAtMillis = 1_784_490_000_000,
            settlementLastSeenXp = 300,
            morningLastShownEpochDay = 20653,
            journalLastShownEpochDay = 20652,
            weeklyGoalDays = 4,
            restModeEnabled = false,
        ),
    )

    @Test
    fun roundTripPreservesEverything() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun futureVersionIsRejected() {
        val future = BackupCodec.encode(sample.copy(backupVersion = BackupCodec.VERSION + 1))
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode(future) }
    }

    @Test
    fun malformedJsonIsRejectedAsIllegalArgument() {
        // SerializationException은 IAE 하위 타입 — BackupManager catch 계약의 전제를 고정
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode("{not json") }
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode("""{"backupVersion":1}""") }
    }

    @Test
    fun unknownFieldsAreIgnoredOnRead() {
        // 미래 마이너 확장(필드 추가)을 구버전이 읽을 수 있다
        val withExtra = BackupCodec.encode(sample).dropLast(1) + ""","futureField":"x"}"""
        assertEquals(sample, BackupCodec.decode(withExtra))
    }

    @Test
    fun schemaKeysArePinned_noHealthValueFields() {
        // 스키마 변경 = VERSION 증가와 함께 이 목록 갱신 — 원본 수치 필드 유입을 리뷰로 강제
        val root = Json.parseToJsonElement(BackupCodec.encode(sample)).jsonObject
        assertEquals(setOf("backupVersion", "exportedAtEpochMillis", "events", "snapshot"), root.keys)
        assertEquals(
            setOf(
                "idempotencyKey",
                "xp",
                "type",
                "dataOrigin",
                "recordingMethod",
                "formulaVersion",
                "epochMillis",
                "epochDay",
            ),
            root.getValue("events").jsonArray.first().jsonObject.keys,
        )
        assertEquals(
            setOf(
                "energyTotalSpent",
                "expeditionStartedAtMillis",
                "settlementLastSeenXp",
                "morningLastShownEpochDay",
                "journalLastShownEpochDay",
                "weeklyGoalDays",
                "restModeEnabled",
            ),
            root.getValue("snapshot").jsonObject.keys,
        )
    }
}
