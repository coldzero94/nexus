package com.nexus.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 로컬 DB (#162) — v1: 원장만. 스키마 변경은 feat! + 마이그레이션(CLAUDE.md). */
@Database(entities = [RewardEventEntity::class], version = 1, exportSchema = true)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun rewardEventDao(): RewardEventDao

    companion object {
        @Volatile private var instance: NexusDatabase? = null

        fun get(context: Context): NexusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, "nexus.db")
                .build()
                .also { instance = it }
        }
    }
}
