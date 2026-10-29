package com.quipu.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.quipu.app.QuipuApplication
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.DebtItem
import com.quipu.app.domain.DebtType
import com.quipu.app.domain.Money
import com.quipu.app.domain.RecurringPayment
import com.quipu.app.domain.Reminder
import com.quipu.app.domain.ReminderKind
import com.quipu.app.domain.ReminderPlanner
import com.quipu.app.domain.TaskItem
import com.quipu.app.ui.Formats
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Programa los avisos con una sola alarma: la del próximo aviso. Cada vez que se
 * sincroniza (al cambiar datos o ajustes, al sonar la alarma, al reiniciar el
 * teléfono o cambiar la hora) publica los avisos que tocaban desde la última
 * revisión y vuelve a programar el siguiente.
 */
object ReminderEngine {
    /** Si el teléfono estuvo apagado, solo se avisa de lo que tocaba en las últimas horas. */
    private const val MAX_CATCH_UP_HOURS = 12L
    private const val REQUEST_CODE = 100

    private val mutex = Mutex()

    suspend fun sync(context: Context) = mutex.withLock {
        val app = context.applicationContext as QuipuApplication
        val repository = app.repository
        val settings = repository.settings
        val config = settings.reminders.value
        val now = LocalDateTime.now().withNano(0)
        val (tasks, debts) = repository.snapshot()
        val (recurring, movements) = app.money.recurringSnapshot()

        val last = settings.lastReminderCheck
        if (last != null) {
            val from = maxOf(last, now.minusHours(MAX_CATCH_UP_HOURS))
            ReminderPlanner.dueBetween(tasks, debts, config, from, now, recurring, movements).forEach { reminder ->
                notify(app, reminder, tasks, debts, recurring)
            }
        }
        settings.lastReminderCheck = now
        schedule(app, ReminderPlanner.nextAfter(tasks, debts, config, now, recurring, movements))
    }

    /** Aviso de prueba desde Ajustes, con el sonido configurado. */
    fun sendTest(context: Context) {
        val settings = (context.applicationContext as QuipuApplication).repository.settings
        QuipuNotifications.post(
            context,
            settings.sound.value,
            QuipuNotifications.TEST_NOTIFICATION_ID,
            "Recordatorio de prueba",
            "Así se verán y sonarán los avisos de Quipu.",
            finance = false,
        )
    }

    /** Si Android permite alarmas exactas a esta app (siempre en Android 11). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun schedule(context: Context, at: LocalDateTime?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarmManager.cancel(pending)
            return
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact(context)) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            // Sin permiso de alarmas exactas Android puede retrasar el aviso unos minutos.
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun notify(
        app: QuipuApplication,
        reminder: Reminder,
        tasks: List<TaskItem>,
        debts: List<DebtItem>,
        recurring: List<RecurringPayment>,
    ) {
        val settings = app.repository.settings
        val today = reminder.at.toLocalDate()
        val (title, text) = when (reminder.kind) {
            ReminderKind.TASK -> {
                val task = tasks.firstOrNull { it.id == reminder.itemId } ?: return
                val day = Formats.relativeDate(task.dueAt.toLocalDate(), today).lowercaseFirst()
                "Tarea por vencer" to "«${task.title}» vence $day a las ${Formats.time(task.dueAt.toLocalTime())}."
            }
            ReminderKind.DAILY_TASK -> {
                val task = tasks.firstOrNull { it.id == reminder.itemId } ?: return
                "Tarea diaria pendiente" to "Aún no completas «${task.title}» hoy."
            }
            ReminderKind.DEBT -> {
                val debt = debts.firstOrNull { it.id == reminder.itemId } ?: return
                val amount = Money.format(debt.remainingMinor, settings.currency.value)
                val due = Formats.relativeDate(debt.dueDate!!, today).lowercaseFirst()
                if (debt.type == DebtType.OWED_TO_ME) {
                    "Cobro por vencer" to "${debt.personName} te debe $amount. Vence $due."
                } else {
                    "Pago por vencer" to "Debes $amount a ${debt.personName}. Vence $due."
                }
            }
            ReminderKind.RECURRING -> {
                val payment = recurring.firstOrNull { it.id == reminder.itemId } ?: return
                // En la moneda original del pago: nunca se convierte.
                val amount = Money.format(payment.amountMinor, AppCurrencies.byCode(payment.currencyCode))
                val dueDate = reminder.dueDate ?: return
                val due = Formats.relativeDate(dueDate, today).lowercaseFirst()
                val noun = if (payment.isSubscription) "Suscripción" else "Pago recurrente"
                "$noun por vencer" to "${payment.name}: $amount. Vence $due."
            }
        }
        QuipuNotifications.post(
            app,
            settings.sound.value,
            id = reminder.key.hashCode(),
            title = title,
            text = text,
            finance = reminder.kind == ReminderKind.DEBT || reminder.kind == ReminderKind.RECURRING,
        )
    }

    /** "Hoy" -> "hoy", "Mañana" -> "mañana"; las fechas ("12 oct 2026") quedan igual. */
    private fun String.lowercaseFirst() = replaceFirstChar { it.lowercase() }
}
