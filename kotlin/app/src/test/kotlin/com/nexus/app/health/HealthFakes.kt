package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

/**
 * 인메모리 [SyncStateStore] (#146). [events]에 호출 순서를 남겨
 * "마커 기록이 새 토큰 발급보다 먼저"(#141 순서 불변식)를 단언할 수 있게 한다.
 */
class FakeSyncStateStore(val events: MutableList<String> = mutableListOf()) : SyncStateStore {
    override var changesToken: String? = null
        set(value) {
            // 크래시 안전 불변식은 "마커가 토큰 '영속화'보다 먼저"(#141) — 쓰기 자체를 로깅해 단언 가능하게
            events += "setToken($value)"
            field = value
        }
    override var lastSyncEpochMillis: Long = 0L
    override var lastChangeCount: Int = 0
    override var lastTokenResetEpochMillis: Long = 0L
        private set
    override var lostDeltaWindowStartEpochMillis: Long = 0L
        private set

    override fun recordTokenReset(resetAtEpochMillis: Long) {
        events += EVENT_RECORD_RESET
        lastTokenResetEpochMillis = resetAtEpochMillis
        lostDeltaWindowStartEpochMillis = lastSyncEpochMillis
    }

    companion object {
        const val EVENT_RECORD_RESET = "recordTokenReset"
    }
}

/**
 * 스크립트형 [HealthConnectClient] 페이크 (#146). 동기화·읽기 경로에 필요한 메서드만
 * 구현하고 나머지는 사용 시 즉시 실패 — 테스트가 의도치 않은 경로를 타면 드러난다.
 */
class FakeHealthConnectClient(val events: MutableList<String> = mutableListOf()) : HealthConnectClient {
    /** getChangesToken이 순서대로 돌려줄 토큰. */
    val tokensToIssue = ArrayDeque<String>()

    /** 토큰 → getChanges 응답 스크립트. */
    val changesByToken = mutableMapOf<String, ChangesResponse>()

    /**
     * recordType별, **요청 pageToken별** readRecords 응답(null = 첫 페이지). FIFO가 아니라 키 매칭 —
     * 페이지 토큰을 실제로 전달하지 않는 회귀(실클라이언트에선 1페이지 무한 재조회)가 여기서 잡힌다.
     */
    val readPagesByType = mutableMapOf<KClass<out Record>, Map<String?, ReadRecordsResponse<out Record>>>()

    /** 마지막 토큰 요청 — 재발급 시 recordTypes 축소 회귀 단언용. */
    var lastChangesTokenRequest: ChangesTokenRequest? = null

    override suspend fun getChangesToken(request: ChangesTokenRequest): String {
        lastChangesTokenRequest = request
        events += EVENT_ISSUE_TOKEN
        return tokensToIssue.removeFirst()
    }

    override suspend fun getChanges(changesToken: String): ChangesResponse {
        events += "getChanges($changesToken)"
        return checkNotNull(changesByToken[changesToken]) { "unscripted token: $changesToken" }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
        val pages = checkNotNull(readPagesByType[request.recordType]) { "unscripted type: ${request.recordType}" }
        val page = checkNotNull(pages[request.pageToken]) { "unscripted pageToken: ${request.pageToken}" }
        return page as ReadRecordsResponse<T>
    }

    override val permissionController: PermissionController
        get() = error("not used in tests")

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse = error("not used")

    override suspend fun updateRecords(records: List<Record>) = error("not used")

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        recordIdsList: List<String>,
        clientRecordIdsList: List<String>,
    ) = error("not used")

    override suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter) =
        error("not used")

    override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> =
        error("not used")

    override suspend fun aggregate(request: AggregateRequest): AggregationResult = error("not used")

    override suspend fun aggregateGroupByDuration(
        request: AggregateGroupByDurationRequest,
    ): List<AggregationResultGroupedByDuration> = error("not used")

    override suspend fun aggregateGroupByPeriod(
        request: AggregateGroupByPeriodRequest,
    ): List<AggregationResultGroupedByPeriod> = error("not used")

    companion object {
        const val EVENT_ISSUE_TOKEN = "getChangesToken"
    }
}

/**
 * 자동 기록 메타데이터 헬퍼. Metadata 생성자는 internal이라 공개 팩토리를 쓴다 —
 * dataOrigin은 플랫폼이 채우는 필드라 테스트에선 빈 값(신뢰 등급 단언에는 부적합).
 */
fun autoMetadata(id: String): Metadata = Metadata.autoRecordedWithId(id, Device(type = Device.TYPE_UNKNOWN))
