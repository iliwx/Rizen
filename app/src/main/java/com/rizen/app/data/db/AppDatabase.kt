package com.rizen.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun taskStatusToString(v: TaskStatus): String = v.name
    @TypeConverter fun stringToTaskStatus(v: String): TaskStatus =
        runCatching { TaskStatus.valueOf(v) }.getOrDefault(TaskStatus.PENDING)

    @TypeConverter fun logKindToString(v: LogKind): String = v.name
    @TypeConverter fun stringToLogKind(v: String): LogKind =
        runCatching { LogKind.valueOf(v) }.getOrDefault(LogKind.ALARM_FIRED)
}

@Database(
    entities = [
        AlarmEntity::class,
        TaskEntity::class,
        RoutineEntity::class,
        ActivityLogEntity::class,
        WakeSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun tasks(): TaskDao
    abstract fun routines(): RoutineDao
    abstract fun logs(): LogDao
    abstract fun sessions(): WakeSessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "rizen.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
