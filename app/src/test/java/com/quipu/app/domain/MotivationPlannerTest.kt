package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotivationPlannerTest {
    /** Miércoles. */
    private val day = LocalDate.of(2026, 10, 28)
    private val settings = MotivationSettings(perDay = 3, windowStartMinute = 9 * 60, windowEndMinute = 21 * 60)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): LocalDateTime = date.atTime(hour, minute)

    // ---- Frecuencia y límite diario ----

    @Test
    fun `sortea tantos mensajes como el limite diario`() {
        (1..5).forEach { n ->
            assertEquals(n, MotivationPlanner.plan(day, settings.copy(perDay = n), Random(n)).slots.size)
        }
    }

    @Test
    fun `dias de la semana`() {
        val saturday = LocalDate.of(2026, 10, 31)
        assertTrue(MotivationDays.WEEKDAYS.includes(day))
        assertTrue(!MotivationDays.WEEKDAYS.includes(saturday))
        assertTrue(MotivationDays.WEEKENDS.includes(saturday))
        assertTrue(MotivationDays.THREE_PER_WEEK.includes(day)) // miércoles
        assertTrue(!MotivationDays.THREE_PER_WEEK.includes(day.plusDays(1))) // jueves
        // Un día sin mensajes no sortea nada.
        assertTrue(MotivationPlanner.plan(saturday, settings.copy(days = MotivationDays.WEEKDAYS), Random(1)).slots.isEmpty())
    }

    @Test
    fun `desactivados no sortean ni programan`() {
        val off = settings.copy(enabled = false)
        assertTrue(MotivationPlanner.plan(day, off, Random(1)).slots.isEmpty())
        assertNull(MotivationPlanner.nextWake(null, off, at(10)))
    }

    // ---- Horarios y periodo de silencio ----

    @Test
    fun `todas las horas caen dentro del horario y separadas`() {
        repeat(50) { seed ->
            val plan = MotivationPlanner.plan(day, settings, Random(seed))
            assertTrue(plan.slots.all { it in 9 * 60 until 21 * 60 })
            assertTrue(plan.slots.zipWithNext().all { (a, b) -> b - a >= MotivationPlanner.MIN_GAP_MINUTES })
        }
    }

    @Test
    fun `respeta el periodo de silencio`() {
        val quiet = settings.copy(quietEnabled = true, quietStartMinute = 13 * 60, quietEndMinute = 15 * 60)
        assertTrue(!MotivationPlanner.isAllowed(14 * 60, quiet))
        assertTrue(MotivationPlanner.isAllowed(15 * 60, quiet))
        repeat(50) { seed ->
            val plan = MotivationPlanner.plan(day, quiet, Random(seed))
            assertTrue(plan.slots.none { it in 13 * 60 until 15 * 60 })
        }
    }

    @Test
    fun `horario y silencio que cruzan la medianoche`() {
        val night = settings.copy(windowStartMinute = 22 * 60, windowEndMinute = 2 * 60)
        assertTrue(MotivationPlanner.isAllowed(23 * 60, night))
        assertTrue(MotivationPlanner.isAllowed(60, night))
        assertTrue(!MotivationPlanner.isAllowed(12 * 60, night))
        val quietNight = settings.copy(quietEnabled = true, quietStartMinute = 20 * 60, quietEndMinute = 10 * 60)
        assertTrue(!MotivationPlanner.isAllowed(9 * 60 + 30, quietNight))
        assertTrue(!MotivationPlanner.isAllowed(20 * 60 + 30, quietNight))
        assertTrue(MotivationPlanner.isAllowed(12 * 60, quietNight))
    }

    @Test
    fun `horario corto junta los mensajes lo necesario`() {
        val short = settings.copy(perDay = 3, windowStartMinute = 9 * 60, windowEndMinute = 9 * 60 + 30)
        val plan = MotivationPlanner.plan(day, short, Random(3))
        assertEquals(3, plan.slots.size)
        assertEquals(3, plan.slots.distinct().size)
        assertTrue(plan.slots.all { it in 9 * 60 until 9 * 60 + 30 })
    }

    @Test
    fun `sin horas posibles no sortea`() {
        val none = settings.copy(windowStartMinute = 9 * 60, windowEndMinute = 9 * 60)
        assertTrue(MotivationPlanner.allowedMinutes(none).isEmpty())
        assertTrue(MotivationPlanner.plan(day, none, Random(1)).slots.isEmpty())
    }

    // ---- Selección aleatoria ----

    @Test
    fun `el sorteo es aleatorio pero reproducible con la misma semilla`() {
        assertEquals(MotivationPlanner.plan(day, settings, Random(42)), MotivationPlanner.plan(day, settings, Random(42)))
        val distinct = (1..20).map { MotivationPlanner.plan(day, settings, Random(it)).slots }.distinct()
        assertTrue(distinct.size > 10)
    }

    @Test
    fun `no repite el mensaje anterior si hay alternativas`() {
        val options = listOf("A", "B", "C")
        repeat(100) { seed ->
            assertNotEquals("B", MotivationPlanner.pickMessage(options, last = "B", random = Random(seed)))
        }
        // Con una sola opción se repite antes que no avisar.
        assertEquals("A", MotivationPlanner.pickMessage(listOf("A"), last = "A", random = Random(1)))
        assertNull(MotivationPlanner.pickMessage(emptyList(), last = null, random = Random(1)))
        // Recorre todas las alternativas, no siempre la misma.
        val seen = (1..60).map { MotivationPlanner.pickMessage(options, "A", Random(it)) }.toSet()
        assertEquals(setOf("B", "C"), seen)
    }

    // ---- Envío y deduplicación ----

    @Test
    fun `envia cada hueco una sola vez`() {
        var plan = MotivationPlan(day, listOf(10 * 60, 15 * 60, 19 * 60))
        assertNull(MotivationPlanner.due(plan, at(9, 59)).send)
        val first = MotivationPlanner.due(plan, at(10, 2))
        assertEquals(0, first.send)
        plan = plan.markDone(0)
        // Abrir la app otra vez a las 10:05 no vuelve a enviar el mismo mensaje.
        assertNull(MotivationPlanner.due(plan, at(10, 5)).send)
        // El siguiente hueco del día sigue siendo legítimo.
        assertEquals(1, MotivationPlanner.due(plan, at(15, 0)).send)
        assertEquals(1, plan.usedCount)
    }

    @Test
    fun `un mensaje muy atrasado se da por perdido y no se acumulan`() {
        val plan = MotivationPlan(day, listOf(10 * 60, 12 * 60, 19 * 60))
        // A las 13:00 (teléfono en reposo): el de 12:00 aún entra en la tolerancia de 60 min; el de 10:00 se pierde.
        val late = MotivationPlanner.due(plan, at(13, 0))
        assertEquals(1, late.send)
        assertEquals(listOf(0), late.missed)
        val tooLate = MotivationPlanner.due(plan, at(14, 0))
        assertNull(tooLate.send)
        assertEquals(listOf(0, 1), tooLate.missed)
    }

    @Test
    fun `un plan de otro dia no envia nada`() {
        val yesterday = MotivationPlan(day.minusDays(1), listOf(10 * 60))
        assertNull(MotivationPlanner.due(yesterday, at(10, 1)).send)
    }

    // ---- Reprogramación y reinicios ----

    @Test
    fun `cambiar los ajustes a media tarde no supera el limite diario`() {
        // Ya se usaron 2 de 3: al volver a sortear a las 15:00 solo queda 1, y después de las 15:00.
        val replan = MotivationPlanner.plan(day, settings, Random(7), now = at(15, 0), alreadyUsed = 2)
        assertEquals(1, replan.slots.size)
        assertTrue(replan.slots.single() > 15 * 60)
        assertEquals(2, replan.usedCount)
        // Si ya se usaron todos, no queda nada hoy y la próxima revisión es mañana.
        val full = MotivationPlanner.plan(day, settings, Random(7), now = at(15, 0), alreadyUsed = 3)
        assertTrue(full.slots.isEmpty())
        assertEquals(at(0, 5, day.plusDays(1)), MotivationPlanner.nextWake(full, settings, at(15, 0)))
    }

    @Test
    fun `proxima revision el siguiente hueco o el proximo dia con mensajes`() {
        val plan = MotivationPlan(day, listOf(10 * 60, 15 * 60), done = setOf(0))
        assertEquals(at(15, 0), MotivationPlanner.nextWake(plan, settings, at(11, 0)))
        // Sin huecos pendientes y con solo días laborables, del viernes salta al lunes.
        val friday = LocalDate.of(2026, 10, 30)
        val done = MotivationPlan(friday, listOf(600), done = setOf(0))
        val weekdays = settings.copy(days = MotivationDays.WEEKDAYS)
        assertEquals(LocalDate.of(2026, 11, 2).atTime(0, 5), MotivationPlanner.nextWake(done, weekdays, friday.atTime(20, 0)))
    }

    @Test
    fun `el plan guardado sobrevive a un reinicio sin volver a sortear`() {
        val plan = MotivationPlanner.plan(day, settings, Random(9)).markDone(0)
        val restored = MotivationPlan.decode(plan.encode())
        assertEquals(plan, restored)
        // Tras reiniciar, la próxima revisión es la misma: no hay un sorteo nuevo.
        val now = day.atTime(9, 0)
        assertEquals(MotivationPlanner.nextWake(plan, settings, now), MotivationPlanner.nextWake(restored, settings, now))
        // Un texto dañado no rompe nada: se sorteará de nuevo.
        assertNull(MotivationPlan.decode("basura"))
        assertNull(MotivationPlan.decode(null))
        // Compatible con un plan guardado sin el campo usedBefore.
        assertEquals(MotivationPlan(day, listOf(540), setOf(0)), MotivationPlan.decode("2026-10-28|540|0"))
    }

    // ---- Mensajes ----

    @Test
    fun `solo afirma cosas que los datos respaldan`() {
        val nothing = Motivation.candidates(emptyList(), emptyList(), day, hasUpcomingPayments = false)
        assertTrue(Motivation.MANAGING_WELL !in nothing)
        assertTrue(Motivation.UPCOMING_PAYMENTS !in nothing)
        assertTrue(nothing.containsAll(Motivation.HABITS))

        val income = Movement(1, MovementType.INCOME, 100_000, "BOB", day)
        val expense = Movement(2, MovementType.EXPENSE, 30_000, "BOB", day)
        val good = Motivation.candidates(emptyList(), listOf(income, expense), day, hasUpcomingPayments = true)
        assertTrue(Motivation.MANAGING_WELL in good)
        assertTrue(Motivation.UPCOMING_PAYMENTS in good)
        assertEquals(good.size, good.distinct().size)
    }
}
