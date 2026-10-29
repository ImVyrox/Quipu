package com.quipu.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/** Qué días se envían mensajes motivacionales. Se guarda por nombre: no renombres las constantes. */
enum class MotivationDays(val label: String) {
    EVERY_DAY("Todos los días"),
    WEEKDAYS("De lunes a viernes"),
    WEEKENDS("Fines de semana"),

    /** Lunes, miércoles y viernes. */
    THREE_PER_WEEK("3 veces por semana"),
    ;

    fun includes(day: LocalDate): Boolean = when (this) {
        EVERY_DAY -> true
        WEEKDAYS -> day.dayOfWeek !in WEEKEND
        WEEKENDS -> day.dayOfWeek in WEEKEND
        THREE_PER_WEEK -> day.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    }

    private companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/**
 * Ajustes de los mensajes motivacionales. Son independientes de los recordatorios: no
 * los sustituyen ni los cancelan. Los minutos del día van de 0 (00:00) a 1439 (23:59).
 */
data class MotivationSettings(
    val enabled: Boolean = true,
    /** Límite diario: como mucho tantos mensajes en un día. */
    val perDay: Int = 2,
    val days: MotivationDays = MotivationDays.EVERY_DAY,
    /** Horario permitido [desde, hasta). Si desde > hasta, cruza la medianoche. */
    val windowStartMinute: Int = 9 * 60,
    val windowEndMinute: Int = 21 * 60,
    /** Periodo de silencio dentro del día (por ejemplo, la siesta o la noche). */
    val quietEnabled: Boolean = false,
    val quietStartMinute: Int = 13 * 60,
    val quietEndMinute: Int = 15 * 60,
)

/**
 * Plan de un día: las horas elegidas al azar ([slots], minutos del día, ordenados) y
 * cuáles ya se enviaron o se dieron por perdidas ([done]). Se guarda para que abrir la
 * app no vuelva a sortear ni a enviar mensajes.
 */
data class MotivationPlan(
    val date: LocalDate,
    val slots: List<Int>,
    val done: Set<Int> = emptySet(),
    /** Mensajes de ese día usados en un plan anterior (al cambiar los ajustes se vuelve a sortear). */
    val usedBefore: Int = 0,
) {
    /** Cuántos mensajes se enviaron o se descartaron ya ese día: cuentan para el límite diario. */
    val usedCount: Int get() = usedBefore + done.size

    fun markDone(index: Int) = copy(done = done + index)

    /** Texto compacto para guardar en preferencias: "2026-10-28|540,800|0|1". */
    fun encode(): String = "$date|${slots.joinToString(",")}|${done.sorted().joinToString(",")}|$usedBefore"

    companion object {
        fun decode(text: String?): MotivationPlan? = runCatching {
            val parts = text!!.split("|")
            MotivationPlan(
                date = LocalDate.parse(parts[0]),
                slots = parts[1].split(",").filter { it.isNotBlank() }.map { it.toInt() },
                done = parts[2].split(",").filter { it.isNotBlank() }.map { it.toInt() }.toSet(),
                usedBefore = parts.getOrNull(3)?.toInt() ?: 0,
            )
        }.getOrNull()
    }
}

/**
 * Programación de los mensajes motivacionales: horas al azar dentro del horario, fuera
 * del periodo de silencio y respetando el límite diario. No depende de Android ni del
 * reloj: recibe la fecha, la hora y el generador aleatorio, así se puede probar.
 */
object MotivationPlanner {
    /** Separación mínima deseada entre dos mensajes del mismo día. */
    const val MIN_GAP_MINUTES = 90

    /** Un mensaje que llega tarde (teléfono en reposo) solo se envía dentro de este margen. */
    const val LATE_TOLERANCE_MINUTES = 60

    /** Valores para el límite diario. */
    val PER_DAY_OPTIONS = listOf(1, 2, 3, 4, 5)

    /** Si ese minuto del día está permitido: dentro del horario y fuera del silencio. */
    fun isAllowed(minuteOfDay: Int, settings: MotivationSettings): Boolean {
        val inWindow = inRange(minuteOfDay, settings.windowStartMinute, settings.windowEndMinute)
        val inQuiet = settings.quietEnabled && inRange(minuteOfDay, settings.quietStartMinute, settings.quietEndMinute)
        return inWindow && !inQuiet
    }

    /** [start, end) con cruce de medianoche si start > end; start == end es un rango vacío. */
    private fun inRange(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    /** Minutos permitidos de un día (0..1439) desde [fromMinute]. */
    fun allowedMinutes(settings: MotivationSettings, fromMinute: Int = 0): List<Int> =
        (fromMinute.coerceAtLeast(0)..1439).filter { isAllowed(it, settings) }

    /**
     * Sortea las horas del día [date]. Si [now] es de ese mismo día, solo se eligen horas
     * posteriores y se descuentan los mensajes ya usados ([alreadyUsed]), así cambiar los
     * ajustes a media tarde no supera el límite diario. Intenta separar los mensajes al
     * menos [MIN_GAP_MINUTES]; si el horario es corto, los separa lo que pueda.
     */
    fun plan(
        date: LocalDate,
        settings: MotivationSettings,
        random: Random,
        now: LocalDateTime? = null,
        alreadyUsed: Int = 0,
    ): MotivationPlan {
        val remaining = (settings.perDay - alreadyUsed).coerceAtLeast(0)
        val empty = MotivationPlan(date, emptyList(), usedBefore = alreadyUsed)
        if (!settings.enabled || remaining == 0 || !settings.days.includes(date)) return empty
        val from = if (now != null && now.toLocalDate() == date) now.hour * 60 + now.minute + 1 else 0
        val candidates = allowedMinutes(settings, from)
        if (candidates.isEmpty()) return empty

        // Primero con la separación ideal; si no caben, con menos separación.
        for (gap in listOf(MIN_GAP_MINUTES, 45, 15, 1)) {
            val picked = mutableListOf<Int>()
            val pool = candidates.shuffled(random).toMutableList()
            for (minute in pool) {
                if (picked.size == remaining) break
                if (picked.all { kotlin.math.abs(it - minute) >= gap }) picked += minute
            }
            if (picked.size == remaining || gap == 1) return MotivationPlan(date, picked.sorted(), usedBefore = alreadyUsed)
        }
        return empty
    }

    /**
     * Mensaje que toca enviar ahora: el último hueco pendiente con hora <= [now] y como mucho
     * [LATE_TOLERANCE_MINUTES] de retraso. Los huecos pendientes más antiguos se dan por
     * perdidos ([Due.missed]) en vez de mandarlos todos de golpe.
     */
    fun due(plan: MotivationPlan, now: LocalDateTime): Due {
        if (plan.date != now.toLocalDate()) return Due(null, emptyList())
        val nowMinute = now.hour * 60 + now.minute
        val pending = plan.slots.indices.filter { it !in plan.done && plan.slots[it] <= nowMinute }
        val send = pending.lastOrNull()?.takeIf { nowMinute - plan.slots[it] <= LATE_TOLERANCE_MINUTES }
        return Due(send, pending - setOfNotNull(send))
    }

    data class Due(val send: Int?, val missed: List<Int>)

    /** Próximo hueco pendiente del plan, posterior a [now], o null si no queda ninguno hoy. */
    fun nextSlot(plan: MotivationPlan, now: LocalDateTime): LocalDateTime? {
        val nowMinute = if (plan.date == now.toLocalDate()) now.hour * 60 + now.minute else -1
        if (plan.date.isBefore(now.toLocalDate())) return null
        return plan.slots.indices
            .filter { it !in plan.done && plan.slots[it] > nowMinute }
            .minOfOrNull { plan.slots[it] }
            ?.let { plan.date.atTime(LocalTime.of(it / 60, it % 60)) }
    }

    /**
     * Cuándo volver a revisar: el próximo hueco de hoy o, si no queda ninguno, el inicio del
     * siguiente día con mensajes (allí se sortea su plan). Null si están desactivados.
     */
    fun nextWake(plan: MotivationPlan?, settings: MotivationSettings, now: LocalDateTime): LocalDateTime? {
        if (!settings.enabled || settings.perDay <= 0) return null
        plan?.let { p -> nextSlot(p, now)?.let { return it } }
        val day = generateSequence(now.toLocalDate().plusDays(1)) { it.plusDays(1) }
            .take(8)
            .firstOrNull { settings.days.includes(it) }
            ?: return null
        return day.atStartOfDay().plusMinutes(5)
    }

    /**
     * Elige un mensaje al azar sin repetir el anterior ([last]) si hay alternativas. Con una
     * sola opción se repite: es mejor que no avisar.
     */
    fun pickMessage(candidates: List<String>, last: String?, random: Random): String? {
        if (candidates.isEmpty()) return null
        val fresh = candidates.filter { it != last }
        return (fresh.ifEmpty { candidates }).random(random)
    }
}
