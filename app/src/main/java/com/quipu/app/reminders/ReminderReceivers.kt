package com.quipu.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ejecuta una sincronización de avisos sin bloquear el hilo principal. */
private fun BroadcastReceiver.syncAsync(context: Context) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            ReminderEngine.sync(context)
        } finally {
            pending.finish()
        }
    }
}

/** Suena la alarma del próximo aviso. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = syncAsync(context)
}

/**
 * Eventos del sistema que borran o descuadran las alarmas: reinicio, actualización de
 * la app, cambio de hora o zona horaria y permiso de alarmas exactas concedido.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = syncAsync(context)
}
