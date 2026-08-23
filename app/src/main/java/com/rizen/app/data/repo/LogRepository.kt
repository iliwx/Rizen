package com.rizen.app.data.repo

import com.rizen.app.data.db.ActivityLogEntity
import com.rizen.app.data.db.LogDao
import com.rizen.app.data.db.LogKind
import kotlinx.coroutines.flow.Flow

class LogRepository(private val dao: LogDao) {

    fun observeBetween(from: Long, to: Long): Flow<List<ActivityLogEntity>> =
        dao.observeBetween(from, to)

    fun observeRecent(limit: Int = 100): Flow<List<ActivityLogEntity>> = dao.observeRecent(limit)

    suspend fun log(
        kind: LogKind,
        label: String = "",
        durationMs: Long = 0,
        refId: Long? = null,
        meta: String = "",
        at: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            ActivityLogEntity(
                kind = kind,
                label = label,
                timestamp = at,
                durationMs = durationMs,
                refId = refId,
                meta = meta,
            )
        )
    }

    suspend fun ofKindSince(kind: LogKind, since: Long) = dao.ofKindSince(kind, since)

    suspend fun wipe() = dao.wipe()
}
