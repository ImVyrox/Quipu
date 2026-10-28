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

    private val GENERIC = listOf(
        "Cada movimiento que anotas te da una foto más clara de tu dinero.",
        "Pequeños ahorros constantes suman más de lo que parece.",
        "Revisar tus gastos unos minutos hoy evita sorpresas a fin de mes.",
        "Un objetivo de ahorro claro hace más fácil decir que no a un gasto impulsivo.",
    )

    fun pick(
        goals: List<SavingsGoal>,
        movements: List<Movement>,
        today: LocalDate,
    ): MotivationMessage {
        val progress = goals.map { Ledger.goalProgress(it, movements) }

        progress.filter { it.reached }.maxByOrNull { it.goal.id }?.let {
            return MotivationMessage(MotivationReason.GOAL_REACHED, "¡Lo lograste! Completaste tu objetivo «${it.goal.name}».")
        }

        progress
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
