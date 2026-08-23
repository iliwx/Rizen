package com.rizen.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun allEnabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observeById(id: Long): Flow<AlarmEntity?>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("DELETE FROM alarms")
    suspend fun wipe()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt, sortIndex")
    fun observeBetween(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY scheduledAt")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query(
        "SELECT * FROM tasks WHERE status IN ('PENDING','RUNNING') " +
            "AND scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt, sortIndex"
    )
    suspend fun pendingBetween(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status IN ('PENDING','RUNNING')")
    suspend fun allOpen(): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE routineId IS NOT NULL AND scheduledAt < :before")
    suspend fun purgeOldRoutineInstances(before: Long)

    @Query("DELETE FROM tasks")
    suspend fun wipe()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY sortIndex")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE enabled = 1 ORDER BY sortIndex")
    suspend fun enabled(): List<RoutineEntity>

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(r: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<RoutineEntity>)

    @Delete
    suspend fun delete(r: RoutineEntity)

    @Query("DELETE FROM routines")
    suspend fun wipe()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM activity_log WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE kind = :kind AND timestamp >= :since ORDER BY timestamp")
    suspend fun ofKindSince(kind: LogKind, since: Long): List<ActivityLogEntity>

    @Insert
    suspend fun insert(log: ActivityLogEntity): Long

    @Query("DELETE FROM activity_log")
    suspend fun wipe()
}

@Dao
interface WakeSessionDao {
    @Query("SELECT * FROM wake_sessions WHERE active = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSession(): WakeSessionEntity?

    @Query("SELECT * FROM wake_sessions WHERE id = :id")
    suspend fun byId(id: Long): WakeSessionEntity?

    @Query("SELECT * FROM wake_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WakeSessionEntity>>

    @Query("SELECT * FROM wake_sessions WHERE completedAt IS NOT NULL AND startedAt >= :since ORDER BY startedAt")
    suspend fun completedSince(since: Long): List<WakeSessionEntity>

    @Upsert
    suspend fun upsert(s: WakeSessionEntity): Long

    @Query("UPDATE wake_sessions SET active = 0 WHERE active = 1")
    suspend fun closeAll()

    @Query("DELETE FROM wake_sessions")
    suspend fun wipe()
}
