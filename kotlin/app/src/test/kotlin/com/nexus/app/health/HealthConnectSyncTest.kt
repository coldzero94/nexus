package com.nexus.app.health

import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.response.ChangesResponse
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthConnectSyncTest {

    private fun upsert(): UpsertionChange = UpsertionChange(
        StepsRecord(
            startTime = Instant.parse("2026-07-15T10:00:00Z"),
            startZoneOffset = null,
            endTime = Instant.parse("2026-07-15T10:10:00Z"),
            endZoneOffset = null,
            count = 100,
            metadata = autoMetadata(id = "s1"),
        ),
    )

    private fun response(
        changes: List<androidx.health.connect.client.changes.Change> = emptyList(),
        next: String,
        hasMore: Boolean = false,
        expired: Boolean = false,
    ) = ChangesResponse(changes, next, hasMore, expired)

    @Test
    fun firstSync_issuesInitialToken_thenConsumesChanges() = runTest {
        val client = FakeHealthConnectClient()
        val store = FakeSyncStateStore()
        client.tokensToIssue += "T1"
        client.changesByToken["T1"] = response(changes = listOf(upsert()), next = "T2")

        val outcome = HealthConnectSync(client, store).sync()

        assertEquals(1, outcome.upserts)
        assertFalse(outcome.tokenReset)
        assertEquals("T2", store.changesToken) // 다음 주기는 델타만
    }

    @Test
    fun multiPage_accumulatesUpserts_andRoutesDeletions() = runTest {
        val client = FakeHealthConnectClient()
        val store = FakeSyncStateStore().apply { changesToken = "A" }
        client.changesByToken["A"] = response(changes = listOf(upsert()), next = "B", hasMore = true)
        client.changesByToken["B"] = response(changes = listOf(DeletionChange("rec-1")), next = "C")

        val outcome = HealthConnectSync(client, store).sync()

        assertEquals(1, outcome.upserts)
        assertEquals(1, outcome.deletions)
        assertEquals(listOf("rec-1"), outcome.deletedRecordIds) // 보상 취소 라우팅 대상 (#133)
        assertEquals("C", store.changesToken)
    }

    @Test
    fun tokenExpired_persistsLossMarker_beforeIssuingFreshToken() = runTest {
        val events = mutableListOf<String>()
        val client = FakeHealthConnectClient(events)
        val store = FakeSyncStateStore(events).apply {
            changesToken = "OLD"
            lastSyncEpochMillis = 12_345L
        }
        client.changesByToken["OLD"] = response(next = "ignored", expired = true)
        client.tokensToIssue += "FRESH"

        val outcome = HealthConnectSync(client, store).sync()

        assertTrue(outcome.tokenReset)
        assertEquals("FRESH", store.changesToken)
        // 유실 구간 [시작=리셋 시점의 lastSync, 리셋 시각] 영속화 (#141)
        assertTrue(store.lastTokenResetEpochMillis > 0L)
        assertEquals(12_345L, store.lostDeltaWindowStartEpochMillis)
        // 순서 불변식 (#141): 마커 먼저, 토큰 발급은 그다음 — 뒤집히면 크래시 시 무흔적 유실 재발
        assertEquals(
            listOf(FakeSyncStateStore.EVENT_RECORD_RESET, FakeHealthConnectClient.EVENT_ISSUE_TOKEN),
            events.filter {
                it == FakeSyncStateStore.EVENT_RECORD_RESET ||
                    it == FakeHealthConnectClient.EVENT_ISSUE_TOKEN
            },
        )
    }
}
