package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Motivo del mensaje elegido, de más a menos importante. */
enum class MotivationReason {
    GOAL_REACHED,
    GOAL_MILESTONE,
    SAVED_THIS_MONTH,
    EARNED_MORE_THAN_SPENT,
    GENERIC,
}

data class MotivationMessage(val reason: MotivationReason, val text: String)

/**
 * Mensajes motivacionales a partir de los datos reales. Son deterministas (el genérico
 * rota según el día), no comparan monedas distintas y no mencionan importes previstos.
 */
object Motivation {
    /** Hitos de progreso de un objetivo, de mayor a menor. */
    val MILESTONES = listOf(75, 50, 25)

    /** Días durante los que se celebra un objetivo cumplido tras su último movimiento. */
    const val GOAL_REACHED_DAYS = 7L

    private val GENERIC = listOf(
        "Cada movimiento que anotas te da una foto más clara de tu dinero.",
        "Pequeños ahorros constantes suman más de lo que parece.",
        "Revisar tus gastos unos minutos hoy evita sorpresas a fin de mes.",
        "Un objetivo de ahorro claro hace más fácil decir que no a un gasto impulsivo.",
    )

    /**
     * Consejos de hábitos financieros para los mensajes motivacionales. No afirman nada
     * sobre los datos del usuario (eso lo hacen los mensajes de [candidates] con condición).
     */
    val HABITS = listOf(
        "Revisa tus gastos de hoy y mantén el control de tu dinero.",
        "Un pequeño ahorro hoy puede ayudarte a cumplir tu próximo objetivo.",
        "Anotar cada gasto, aunque sea pequeño, te ayuda a saber a dónde va tu dinero.",
        "Antes de comprar algo, espera un día: si mañana lo sigues queriendo, decídelo con calma.",
        "Separar primero lo que quieres ahorrar hace más fácil cumplirlo.",
        "Pequeños ahorros constantes suman más de lo que parece.",
        "Revisar tus gastos unos minutos hoy evita sorpresas a fin de mes.",
        "Un objetivo de ahorro claro hace más fácil decir que no a un gasto impulsivo.",
    )

    const val MANAGING_WELL = "Estás gestionando tus gastos correctamente. ¡Sigue así!"
    const val UPCOMING_PAYMENTS = "Recuerda revisar los pagos que vencen próximamente."

    /**
     * Mensajes posibles hoy para una notificación motivacional: los consejos generales y,
     * solo si los datos lo respaldan, los que hablan de su situación (objetivo cumplido o
     * cerca, ahorro del mes, gastar menos de lo que ingresa, pagos que vencen pronto).
     */
    fun candidates(
        goals: List<SavingsGoal>,
        movements: List<Movement>,
        today: LocalDate,
        hasUpcomingPayments: Boolean,
    ): List<String> {
        val personal = pick(goals, movements, today)
        return buildList {
            addAll(HABITS)
            if (personal.reason != MotivationReason.GENERIC) add(personal.text)
            if (personal.reason == MotivationReason.SAVED_THIS_MONTH || personal.reason == MotivationReason.EARNED_MORE_THAN_SPENT) {
                add(MANAGING_WELL)
            }
            if (hasUpcomingPayments) add(UPCOMING_PAYMENTS)
        }.distinct()
    }

    fun pick(
        goals: List<SavingsGoal>,
        movements: List<Movement>,
        today: LocalDate,
    ): MotivationMessage {
        // Los objetivos en pausa no generan mensajes.
        val progress = goals.filter { it.status != GoalStatus.PAUSED }.map { Ledger.goalProgress(it, movements) }

        // Un objetivo cumplido se celebra solo los primeros días tras su último movimiento.
        fun recentlyReached(p: GoalProgress): Boolean {
            if (!p.reached) return false
            val last = Ledger.goalHistory(p.goal.id, movements).firstOrNull()?.date ?: return false
            return !today.isAfter(last.plusDays(GOAL_REACHED_DAYS))
        }
        progress.filter(::recentlyReached).maxByOrNull { it.goal.id }?.let {
            return MotivationMessage(MotivationReason.GOAL_REACHED, "¡Lo lograste! Completaste tu objetivo «${it.goal.name}».")
        }

        progress
            .filter { !it.reached && it.goal.status == GoalStatus.ACTIVE }
            .mapNotNull { p -> MILESTONES.firstOrNull { p.percent >= it }?.let { p to it } }
            .maxWithOrNull(compareBy({ it.second }, { it.first.percent }))
            ?.let { (p, milestone) ->
                return MotivationMessage(
                    MotivationReason.GOAL_MILESTONE,
                    "Ya llevas más del $milestone % de «${p.goal.name}». ¡Sigue así!",
                )
            }

        val totals = Ledger.periodTotals(movements, YearMonth.from(today))
        if (totals.savedNet.byCode.values.any { it > 0L }) {
            return MotivationMessage(MotivationReason.SAVED_THIS_MONTH, "Este mes ya apartaste dinero para tus objetivos. ¡Bien hecho!")
        }
        // Por moneda: basta con que en alguna moneda los ingresos superen a los gastos.
        if (totals.net.byCode.values.any { it > 0L } && !totals.expense.isEmpty) {
            return MotivationMessage(MotivationReason.EARNED_MORE_THAN_SPENT, "Este mes vas gastando menos de lo que ingresas.")
        }

        val index = Math.floorMod(today.toEpochDay(), GENERIC.size.toLong()).toInt()
        return MotivationMessage(MotivationReason.GENERIC, GENERIC[index])
    }
}
