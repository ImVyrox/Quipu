package com.quipu.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ejecuta una sincronización de avisos sin bloquear el hilo principal y, si se indica,
 * algo más (por ejemplo, reprogramar los mensajes motivacionales).
 */
private fun BroadcastReceiver.syncAsync(context: Context, also: (() -> Unit)? = null) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            ReminderEngine.sync(context)
            also?.invoke()
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
 * WorkManager ya restaura sus tareas tras reiniciar; aquí solo se corrige la espera de
 * los mensajes motivacionales si cambió la hora, o se asegura tras actualizar la app.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = syncAsync(context) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> MotivationEngine.refresh(context)
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_BOOT_COMPLETED -> MotivationEngine.ensureScheduled(context)
        }
    }
}
