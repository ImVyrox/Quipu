package com.quipu.app

import android.app.Application
import com.quipu.app.data.MoneyRepository
import com.quipu.app.data.QuipuDatabase
import com.quipu.app.data.QuipuRepository
import com.quipu.app.data.SettingsStore
import com.quipu.app.reminders.MotivationEngine
import com.quipu.app.reminders.ReminderEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class QuipuApplication : Application() {
    val repository: QuipuRepository by lazy {
        QuipuRepository(QuipuDatabase.get(this), SettingsStore(this))
    }

    /** Ingresos, gastos y dinero declarado. */
    val money: MoneyRepository by lazy { MoneyRepository(QuipuDatabase.get(this)) }

    /** Vive mientras viva el proceso; para trabajo que no pertenece a una pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Mensajes motivacionales: solo asegura que haya una revisión pendiente (no sortea ni envía).
        try {
            MotivationEngine.ensureScheduled(this)
        } catch (e: Exception) {
            // Si WorkManager no está disponible, la app sigue funcionando sin estos mensajes.
        }
        // Reprograma los avisos cada vez que cambian tareas, registros, pagos recurrentes
        // (o sus pagos, que son movimientos) o ajustes.
        appScope.launch {
            combine(
                repository.observeTasks(),
                repository.observeDebts(),
                repository.settings.reminders,
                money.observeRecurring(),
                money.observeMovements(),
            ) { _, _, _, _, _ -> }.collectLatest {
                delay(500) // agrupa cambios seguidos en una sola sincronización
                try {
                    ReminderEngine.sync(this@QuipuApplication)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Un fallo al programar avisos no debe cerrar la app; se reintenta en el próximo cambio.
                }
            }
        }
    }
}
