package com.quipu.app.domain

import java.time.LocalDate

enum class DebtTypeFilter { ALL, OWED_TO_ME, I_OWE }

enum class DebtStatusFilter { ALL, PENDING, SETTLED }

/** Saldo pendiente de una persona. Solo cuenta registros pendientes. */
data class PersonBalance(
    val personKey: String,
    val displayName: String,
    val owedToMe: Long,
    val iOwe: Long,
    val pendingCount: Int,
) {
    /** Positivo: la persona me debe en neto. Negativo: yo le debo en neto. */
    val net: Long get() = owedToMe - iOwe
}

data class FinanceSummary(
    val totalOwedToMe: Long,
    val totalIOwe: Long,
    val peopleCount: Int,
    val pendingCount: Int,
    val perPerson: List<PersonBalance>,
) {
    val net: Long get() = totalOwedToMe - totalIOwe
}

/**
 * "Carpeta" de una persona en Finanzas. [balance] solo cuenta registros pendientes
 * (igual que el Resumen); [recordCount] incluye también los pagados/cobrados.
 */
data class PersonGroup(
    val personKey: String,
    val displayName: String,
    val balance: PersonBalance,
    val recordCount: Int,
    val hasOverdue: Boolean,
)

/** Registros de una persona separados por tipo («Me debe» / «Yo debo») y estado. */
data class PersonRecords(
    val owedToMePending: List<DebtItem>,
    val owedToMeSettled: List<DebtItem>,
    val iOwePending: List<DebtItem>,
    val iOweSettled: List<DebtItem>,
) {
    val isEmpty: Boolean
        get() = owedToMePending.isEmpty() && owedToMeSettled.isEmpty() &&
            iOwePending.isEmpty() && iOweSettled.isEmpty()
}

object DebtLogic {

    fun isOverdue(item: DebtItem, today: LocalDate): Boolean =
        item.status == DebtStatus.PENDING && item.dueDate != null && item.dueDate.isBefore(today)

    /**
     * Busca por nombre (ignora mayúsculas, tildes y espacios sobrantes), filtra por
     * tipo y estado, y ordena por fecha de registro.
     */
    fun filterAndSort(
        items: List<DebtItem>,
        query: String,
        typeFilter: DebtTypeFilter,
        statusFilter: DebtStatusFilter,
        newestFirst: Boolean,
    ): List<DebtItem> {
        val queryKey = PersonKey.keyOf(query)
        val filtered = items.filter { item ->
            (queryKey.isEmpty() || item.personKey.contains(queryKey)) &&
                when (typeFilter) {
                    DebtTypeFilter.ALL -> true
                    DebtTypeFilter.OWED_TO_ME -> item.type == DebtType.OWED_TO_ME
                    DebtTypeFilter.I_OWE -> item.type == DebtType.I_OWE
                } &&
                when (statusFilter) {
                    DebtStatusFilter.ALL -> true
                    DebtStatusFilter.PENDING -> item.status == DebtStatus.PENDING
                    DebtStatusFilter.SETTLED -> item.status == DebtStatus.SETTLED
                }
        }
        val comparator = compareBy<DebtItem>({ it.recordDate }, { it.id })
        return filtered.sortedWith(if (newestFirst) comparator.reversed() else comparator)
    }

    /**
     * Agrupa los registros por persona usando su clave normalizada ([PersonKey]), así
     * "Ana", " ana " y "Ána" son una sola persona. Búsqueda y filtros deciden qué
     * personas aparecen (las que tienen algún registro que coincide); el saldo y el
     * recuento de cada persona se calculan con todos sus registros. El orden sigue al
     * de [filterAndSort]: cada persona se ubica según su registro coincidente más
     * reciente (o más antiguo si [newestFirst] es false).
     */
    fun groupByPerson(
        items: List<DebtItem>,
        query: String,
        typeFilter: DebtTypeFilter,
        statusFilter: DebtStatusFilter,
        newestFirst: Boolean,
        today: LocalDate,
    ): List<PersonGroup> {
        val byPerson = items.groupBy { it.personKey }
        return filterAndSort(items, query, typeFilter, statusFilter, newestFirst)
            .map { it.personKey }
            .distinct()
            .map { key ->
                val records = byPerson.getValue(key)
                PersonGroup(
                    personKey = key,
                    displayName = FinanceCalculator.latestName(records),
                    balance = FinanceCalculator.balanceOf(key, records),
                    recordCount = records.size,
                    hasOverdue = records.any { isOverdue(it, today) },
                )
            }
    }

    /** Todos los registros de una persona, separados por tipo y estado y ordenados por fecha. */
    fun recordsOf(items: List<DebtItem>, personKey: String, newestFirst: Boolean): PersonRecords {
        val comparator = compareBy<DebtItem>({ it.recordDate }, { it.id })
        val mine = items
            .filter { it.personKey == personKey }
            .sortedWith(if (newestFirst) comparator.reversed() else comparator)
        fun pick(type: DebtType, status: DebtStatus) = mine.filter { it.type == type && it.status == status }
        return PersonRecords(
            owedToMePending = pick(DebtType.OWED_TO_ME, DebtStatus.PENDING),
            owedToMeSettled = pick(DebtType.OWED_TO_ME, DebtStatus.SETTLED),
            iOwePending = pick(DebtType.I_OWE, DebtStatus.PENDING),
            iOweSettled = pick(DebtType.I_OWE, DebtStatus.SETTLED),
        )
    }
}

object FinanceCalculator {

    /** Calcula todo a partir de los registros guardados; los finalizados no cuentan. */
    fun summarize(items: List<DebtItem>): FinanceSummary {
        val pending = items.filter { it.status == DebtStatus.PENDING }

        val perPerson = pending
            .groupBy { it.personKey }
            .map { (key, records) -> balanceOf(key, records) }
            .sortedWith(
                compareByDescending<PersonBalance> { if (it.net < 0) -it.net else it.net }
                    .thenBy { it.displayName.lowercase() },
            )

        return FinanceSummary(
            totalOwedToMe = pending.filter { it.type == DebtType.OWED_TO_ME }.sumOf { it.amountMinor },
            totalIOwe = pending.filter { it.type == DebtType.I_OWE }.sumOf { it.amountMinor },
            peopleCount = perPerson.size,
            pendingCount = pending.size,
            perPerson = perPerson,
        )
    }

    /**
     * Saldo pendiente de una persona a partir de sus registros (los pagados/cobrados
     * no cuentan). El nombre mostrado es el del registro más reciente entre [records].
     */
    fun balanceOf(personKey: String, records: List<DebtItem>): PersonBalance {
        val pending = records.filter { it.status == DebtStatus.PENDING }
        return PersonBalance(
            personKey = personKey,
            displayName = latestName(records),
            owedToMe = pending.filter { it.type == DebtType.OWED_TO_ME }.sumOf { it.amountMinor },
            iOwe = pending.filter { it.type == DebtType.I_OWE }.sumOf { it.amountMinor },
            pendingCount = pending.size,
        )
    }

    /** Nombre del registro más reciente (por fecha de registro y luego por id). */
    fun latestName(records: List<DebtItem>): String =
        records.maxWithOrNull(compareBy<DebtItem>({ it.recordDate }, { it.id }))!!.personName
}
