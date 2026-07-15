package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ChangesTokenRequest

/** 한 번의 동기화 결과. [tokenReset]=만료로 토큰 재발급됨(변경분 유실 → E3 소급 재계산 대상). */
data class SyncOutcome(val tokenReset: Boolean, val upserts: Int, val deletions: Int)

/**
 * Changes API 증분 동기화 (#8). 토큰을 저장해두고 다음 주기에 델타만 읽는다.
 * 30일 만료 시 토큰 재발급 폴백. DeletionChange는 보상 이벤트 연결점으로 라우팅(실제 원장은 E3).
 * 폴링 남용 방지: 이 함수는 WorkManager 15분 주기에서만 호출.
 */
class HealthConnectSync(
    private val client: HealthConnectClient,
    private val store: TokenStore,
) {
    private val recordTypes = setOf(
        StepsRecord::class,
        ExerciseSessionRecord::class,
        HeartRateRecord::class,
    )

    suspend fun sync(): SyncOutcome {
        var token = store.changesToken ?: client.getChangesToken(ChangesTokenRequest(recordTypes))
        var upserts = 0
        var deletions = 0

        while (true) {
            val response = client.getChanges(token)
            if (response.changesTokenExpired) {
                // 30일 만료 폴백: 델타 유실 → 새 토큰 발급 후 종료. 소급 재계산은 E3 파이프라인.
                val fresh = client.getChangesToken(ChangesTokenRequest(recordTypes))
                store.changesToken = fresh
                return SyncOutcome(tokenReset = true, upserts = upserts, deletions = deletions)
            }
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> upserts++
                    is DeletionChange -> {
                        deletions++
                        onDeletion(change.recordId)
                    }
                }
            }
            token = response.nextChangesToken
            if (!response.hasMore) break
        }

        store.changesToken = token
        return SyncOutcome(tokenReset = false, upserts = upserts, deletions = deletions)
    }

    /** DeletionChange → 보상 이벤트 트리거 연결점. 실제 RewardEvent 원장 append는 E3. */
    private fun onDeletion(recordId: String) {
        // TODO(E3): 삭제 감지 시 취소 보상 이벤트를 원장에 append (수정 아닌 append — BACKEND.md §1)
    }
}
