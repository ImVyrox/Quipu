package com.quipu.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quipu.app.QuipuApplication
import com.quipu.app.domain.Motivation
import com.quipu.app.domain.MotivationPlanner
import com.quipu.app.domain.OccurrenceStatus
import com.quipu.app.domain.RecurringPlanner
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mensajes motivacionales, separados de los recordatorios: van con WorkManager (no con
 * alarmas exactas, porque la hora es orientativa), en su propio canal y con su propio tag
 * de notificación, así nunca reemplazan ni retrasan un aviso de vencimiento.
 *
 * Cada día se sortean las horas (dentro del horario, fuera del silencio y hasta el límite
 * diario) y el plan se guarda: abrir la app no vuelve a sortear ni a enviar. Una sola
 * tarea de WorkManager espera hasta el próximo hueco; al terminar, encola la siguiente.
 */
object MotivationEngine {
    const val WORK_NAME = "quipu-motivation"

    /** Los mensajes de cada día usan ids distintos: uno no reemplaza al anterior. */
    private const val ID_BASE = 1_000

    private val mutex = Mutex()

    /**
     * Revisa el plan: envía el mensaje que toca (si está dentro del horario permitido y hay
     * permiso), da por perdidos los que llegaron demasiado tarde y devuelve cuándo volver a revisar.
     */
    suspend fun run(context: Context, now: LocalDateTime = nowMinute(), random: Random = Random.Default): LocalDateTime? =
        mutex.withLock {
            val app = context.applicationContext as QuipuApplication
            val store = app.repository.settings
            val settings = store.motivation.value
            if (!settings.enabled) return@withLock null

            val today = now.toLocalDate()
            // Un día nuevo se sortea desde ahora: no hay huecos ya pasados que dar por perdidos.
            var plan = store.motivationPlan?.takeIf { it.date == today }
                ?: MotivationPlanner.plan(today, settings, random, now)
            val due = MotivationPlanner.due(plan, now)
            due.missed.forEach { plan = plan.markDone(it) }
            due.send?.let { index ->
                if (MotivationPlanner.isAllowed(now.hour * 60 + now.minute, settings) && QuipuNotifications.canNotify(app)) {
                    send(app, today.toEpochDay().toInt() % 100_000 * 10 + index, random)
                }
                plan = plan.markDone(index)
            }
            store.motivationPlan = plan
            MotivationPlanner.nextWake(plan, settings, now)
        }

    /**
     * Tras cambiar los ajustes: vuelve a sortear lo que queda de hoy (sin superar el límite
     * diario con lo ya enviado) y reemplaza la tarea pendiente.
     */
    suspend fun replan(context: Context, now: LocalDateTime = nowMinute(), random: Random = Random.Default) {
        val next = mutex.withLock {
            val store = (context.applicationContext as QuipuApplication).repository.settings
            val settings = store.motivation.value
            val used = store.motivationPlan?.takeIf { it.date == now.toLocalDate() }?.usedCount ?: 0
            val plan = MotivationPlanner.plan(now.toLocalDate(), settings, random, now, used)
            store.motivationPlan = plan
            MotivationPlanner.nextWake(plan, settings, now)
        }
        schedule(context, next, ExistingWorkPolicy.REPLACE, now)
    }

    /**
     * Al abrir la app: si no hay una tarea pendiente, la crea con el plan guardado. No vuelve
     * a sortear y no toca una tarea que ya existe (KEEP), así abrir la app no genera avisos.
     */
    fun ensureScheduled(context: Context, now: LocalDateTime = nowMinute()) {
        val store = (context.applicationContext as QuipuApplication).repository.settings
        val settings = store.motivation.value
        val next = MotivationPlanner.nextWake(store.motivationPlan, settings, now)
            // Sin plan para hoy: revisar ya (el worker sorteará el día).
            ?.let { if (store.motivationPlan?.date != now.toLocalDate()) now.plusMinutes(1) else it }
        schedule(context, next, ExistingWorkPolicy.KEEP, now)
    }

    /**
     * Tras un cambio de hora o zona horaria: reprograma la espera con el plan guardado (los
     * retrasos de WorkManager se calculan al encolar, y la hora de pared cambió).
     */
    fun refresh(context: Context, now: LocalDateTime = nowMinute()) {
        val store = (context.applicationContext as QuipuApplication).repository.settings
        schedule(context, MotivationPlanner.nextWake(store.motivationPlan, store.motivation.value, now), ExistingWorkPolicy.REPLACE, now)
    }

    internal fun schedule(context: Context, at: LocalDateTime?, policy: ExistingWorkPolicy, now: LocalDateTime) {
        val workManager = WorkManager.getInstance(context)
        if (at == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<MotivationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** Mensaje de prueba desde Ajustes: no cuenta para el límite diario. */
    suspend fun sendTest(context: Context) {
        send(context.applicationContext as QuipuApplication, ID_BASE - 1, Random.Default)
    }

    private suspend fun send(app: QuipuApplication, id: Int, random: Random) {
        val store = app.repository.settings
        val today = nowMinute().toLocalDate()
        val movements = app.money.observeMovements().first()
        val goals = app.money.observeGoals().first()
        val recurring = app.money.observeRecurring().first()
        // Pagos vencidos o que vencen en la próxima semana.
        val upcoming = RecurringPlanner.schedules(recurring, movements, today).any { s ->
            s.overdue.isNotEmpty() || s.next?.let { it.status == OccurrenceStatus.PENDING && !it.dueDate.isAfter(today.plusDays(7)) } == true
        }
        val message = MotivationPlanner.pickMessage(
            Motivation.candidates(goals, movements, today, upcoming),
            store.lastMotivationMessage,
            random,
        ) ?: return
        QuipuNotifications.post(
            app,
            store.sound.value,
            id = ID_BASE + id,
            title = "Quipu",
            text = message,
            finance = true,
            tag = QuipuNotifications.TAG_MOTIVATION,
        )
        store.lastMotivationMessage = message
    }

    private fun nowMinute(): LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)
}

/** Ejecuta una revisión del plan y encola la siguiente detrás de esta (APPEND_OR_REPLACE). */
class MotivationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val next = MotivationEngine.run(applicationContext, now)
        MotivationEngine.schedule(applicationContext, next, ExistingWorkPolicy.APPEND_OR_REPLACE, now)
        return Result.success()
    }
}
