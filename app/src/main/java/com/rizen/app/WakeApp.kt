package com.rizen.app

import android.app.Application
import android.content.Context
import com.rizen.app.alarm.AlarmScheduler
import com.rizen.app.core.i18n.StringsEn
import com.rizen.app.core.util.Channels
import com.rizen.app.data.db.AppDatabase
import com.rizen.app.data.db.RoutineEntity
import com.rizen.app.data.prefs.SettingsRepository
import com.rizen.app.data.repo.LogRepository
import com.rizen.app.data.repo.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hand-rolled service locator. No DI framework on purpose: the object graph is tiny and
 * BroadcastReceivers need to reach it from a cold process without waiting on injection.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val logs: LogRepository by lazy { LogRepository(db.logs()) }
    val plan: PlanRepository by lazy {
        PlanRepository(db.tasks(), db.routines(), logs, appContext)
    }
    val scheduler: AlarmScheduler by lazy { AlarmScheduler(appContext) }
}

class WakeApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.ensure(this, StringsEn)

        appScope.launch {
            seedRoutinesIfEmpty()
            // Re-arm everything: covers app updates and any alarm the OS dropped.
            container.scheduler.rescheduleAll(container.db.alarms())
        }
    }

    private suspend fun seedRoutinesIfEmpty() {
        val dao = container.db.routines()
        if (dao.count() > 0) return
        dao.insertAll(
            listOf(
                RoutineEntity(title = "Wash up & bathroom", minutes = 15, sortIndex = 0, icon = "drop"),
                RoutineEntity(title = "Workout", minutes = 20, sortIndex = 1, icon = "bolt"),
                RoutineEntity(title = "Breakfast & coffee", minutes = 30, sortIndex = 2, icon = "cup"),
            )
        )
    }

    /** Convenience for the UI layer. */
    suspend fun currentSettings() = container.settings.settings.first()

    companion object {
        /**
         * Receivers can be created in a process where [WakeApp.onCreate] has not run yet
         * (or, on some OEMs, in a secondary process). Falling back to a fresh container
         * is safe: Room and DataStore are both process-wide singletons underneath.
         */
        fun container(context: Context): AppContainer =
            (context.applicationContext as? WakeApp)?.container
                ?: AppContainer(context)
    }
}
