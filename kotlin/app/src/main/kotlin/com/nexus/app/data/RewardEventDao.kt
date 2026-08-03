package com.nexus.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RewardEventDao {
    /** 멱등 append — (key, type) 충돌 시 무시하고 -1 반환. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: RewardEventEntity): Long

    @Query("SELECT * FROM reward_events WHERE idempotencyKey = :key AND type = 'GRANT' LIMIT 1")
    suspend fun grantOf(key: String): RewardEventEntity?

    @Query("SELECT epochDay, SUM(xp) AS xp FROM reward_events GROUP BY epochDay")
    suspend fun xpByDay(): List<DayXpRow>

    @Query("SELECT COUNT(*) FROM reward_events")
    suspend fun count(): Long

    /**
     * 평생 활동일 수 (#113) — 일자별 **순** XP가 양수인 날만.
     *
     * 취소 행이 하루를 통째로 상쇄하면 그날은 활동일이 아니다. `COUNT(DISTINCT epochDay)`로 세면
     * 삭제된 기록이 영원히 남아 "함께한 100일"이 거짓이 된다. 읽기 창과 무관한 **전 기간** 집계라
     * 여기가 평생 지표의 유일한 소스다.
     */
    @Query("SELECT COUNT(*) FROM (SELECT epochDay FROM reward_events GROUP BY epochDay HAVING SUM(xp) > 0)")
    suspend fun activeDaysLifetime(): Int

    /** 원장의 가장 이른 활동일 — '언제부터 함께였나'의 하한 (#111). 비어 있으면 null. */
    @Query("SELECT MIN(epochDay) FROM reward_events")
    suspend fun firstEpochDay(): Long?

    /** 백업 내보내기용 전체 원장 (#51) — sequence 순서 유지. */
    @Query("SELECT * FROM reward_events ORDER BY sequence")
    suspend fun all(): List<RewardEventEntity>
}

/** 일자 합산 행 (xpByDay). */
data class DayXpRow(val epochDay: Long, val xp: Double)
