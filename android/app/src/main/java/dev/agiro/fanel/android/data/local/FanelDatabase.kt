package dev.agiro.fanel.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CalendarEventEntity::class,
        OutboxEntity::class,
        CachedSnapshotEntity::class,
        PendingOperationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FanelDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun outboxDao(): OutboxDao
    abstract fun cachedSnapshotDao(): CachedSnapshotDao
    abstract fun pendingOperationDao(): PendingOperationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_snapshots` (" +
                        "`householdId` TEXT NOT NULL, `domain` TEXT NOT NULL, `key` TEXT NOT NULL, " +
                        "`json` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`householdId`, `domain`, `key`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_operations` (" +
                        "`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `domain` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `targetId` TEXT, `payloadJson` TEXT, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN durationMinutes INTEGER")
            }
        }
    }
}
