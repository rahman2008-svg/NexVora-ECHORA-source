package com.nexvora.echora.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [AudioEntity::class, TranscriptEntity::class, ModelEntity::class, SubscriptionEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EchoraDatabase : RoomDatabase() {
    abstract fun audio(): AudioDao
    abstract fun transcripts(): TranscriptDao
    abstract fun models(): ModelDao
    abstract fun subscription(): SubscriptionDao
    abstract fun payments(): PaymentDao

    companion object {
        /** Add Migration objects here when bumping [version]; never use fallbackToDestructiveMigration (user data). */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): EchoraDatabase =
            Room.databaseBuilder(context.applicationContext, EchoraDatabase::class.java, "echora.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
