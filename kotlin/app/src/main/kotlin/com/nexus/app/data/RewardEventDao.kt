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
}

/** 일자 합산 행 (xpByDay). */
data class DayXpRow(val epochDay: Long, val xp: Double)
