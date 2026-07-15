package com.nexus.app.health

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ChangesTokenRequest

/**
 * 한 번의 동기화 결과. [tokenReset]=만료로 토큰 재발급됨(변경분 유실 → E3 소급 재계산 대상).
 * [deletedRecordIds]=삭제 감지된 레코드 id — 호출자가 원장 보상 취소(RewardLedger.cancel)로 라우팅.
 */
data class SyncOutcome(
    val tokenReset: Boolean,
    val upserts: Int,
    val deletions: Int,
    val deletedRecordIds: List<String> = emptyList(),
)

/**
 * Changes API 증분 동기화 (#8). 토큰을 저장해두고 다음 주기에 델타만 읽는다.
 * 30일 만료 시 토큰 재발급 폴백. DeletionChange는 보상 이벤트 연결점으로 라우팅(실제 원장은 E3).
 * 폴링 남용 방지: 이 함수는 WorkManager 15분 주기에서만 호출.
 */
class HealthConnectSync(private val client: HealthConnectClient, private val store: TokenStore) {
    private companion object {
        const val TAG = "HealthConnectSync"
    }

    private val recordTypes =
        setOf(
            StepsRecord::class,
            ExerciseSessionRecord::class,
            HeartRateRecord::class,
        )

    suspend fun sync(): SyncOutcome {
        var token = store.changesToken ?: client.getChangesToken(ChangesTokenRequest(recordTypes))
        var upserts = 0
        val deletedIds = mutableListOf<String>()

        while (true) {
            val response = client.getChanges(token)
            if (response.changesTokenExpired) {
                // 30일 만료 폴백: 델타 유실 → 새 토큰 발급 후 종료. 소급 재계산은 E3 파이프라인.
                // 유실을 무흔적으로 두지 않는다(#141): 리셋 지점에서 직접 로그+마커 영속화 —
                // 호출자가 outcome.tokenReset을 안 읽어도 E3이 "언제 유실됐는지"를 알 수 있다.
                val resetAt = System.currentTimeMillis()
                Log.w(
                    TAG,
                    "changes token expired — delta lost since last sync " +
                        "(lastSync=${store.lastSyncEpochMillis}, resetAt=$resetAt); issuing fresh token",
                )
                store.lastTokenResetEpochMillis = resetAt
                // 유실 구간 시작 = 이 시점의 lastSync — Worker가 곧 덮어쓰므로 지금 보존해야 한다
                store.lostDeltaWindowStartEpochMillis = store.lastSyncEpochMillis
                val fresh = client.getChangesToken(ChangesTokenRequest(recordTypes))
                store.changesToken = fresh
                return SyncOutcome(
                    tokenReset = true,
                    upserts = upserts,
                    deletions = deletedIds.size,
                    deletedRecordIds = deletedIds.toList(),
                )
            }
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> upserts++

                    // 삭제 → 보상 취소 대상 id를 수집(드롭 금지). 원장 cancel 라우팅은 호출자.
                    is DeletionChange -> deletedIds.add(change.recordId)
                }
            }
            token = response.nextChangesToken
            if (!response.hasMore) break
        }

        store.changesToken = token
        return SyncOutcome(
            tokenReset = false,
            upserts = upserts,
            deletions = deletedIds.size,
            deletedRecordIds = deletedIds.toList(),
        )
    }
}
