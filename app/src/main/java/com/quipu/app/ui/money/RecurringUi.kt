@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.FinanceValidators
import com.quipu.app.domain.Limits
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.OccurrenceStatus
import com.quipu.app.domain.RecurringFormErrors
import com.quipu.app.domain.RecurringFrequency
import com.quipu.app.domain.RecurringOccurrence
import com.quipu.app.domain.RecurringPayment
import com.quipu.app.domain.RecurringPlanner
import com.quipu.app.domain.RecurringReminder
import com.quipu.app.domain.RecurringSchedule
import com.quipu.app.domain.ReminderOptions
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.ConfirmDeleteDialog
import com.quipu.app.ui.components.DateField
import com.quipu.app.ui.components.FormActions
import com.quipu.app.ui.components.FormSheet
import com.quipu.app.ui.components.StatusPill
import com.quipu.app.ui.components.TimeField
import com.quipu.app.ui.theme.QuipuColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Etiqueta de estado con los colores semánticos de la app. */
@Composable
private fun OccurrencePill(status: OccurrenceStatus) {
    val scheme = MaterialTheme.colorScheme
    val extra = QuipuColors.extra
    when (status) {
        OccurrenceStatus.OVERDUE -> StatusPill(status.label, container = scheme.errorContainer, content = scheme.onErrorContainer)
        OccurrenceStatus.PENDING -> StatusPill(status.label, container = extra.soonContainer, content = extra.soon)
        OccurrenceStatus.PAID -> StatusPill(status.label, container = extra.positiveContainer, content = extra.positive)
        OccurrenceStatus.PAUSED -> StatusPill(status.label, container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
    }
}

/** "Vence hoy", "Venció el 5 oct 2026", "Vence el 5 nov 2026". */
private fun dueText(occurrence: RecurringOccurrence, today: LocalDate): String {
    val day = Formats.relativeDate(occurrence.dueDate, today)
    val isRelative = day != Formats.date(occurrence.dueDate)
    val verb = if (occurrence.dueDate.isBefore(today)) "Venció" else "Vence"
    return if (isRelative) "$verb ${day.lowercase()}" else "$verb el $day"
}

/** Tarjeta de la lista de próximos pagos. */
@Composable
internal fun RecurringCard(
    schedule: RecurringSchedule,
    today: LocalDate,
    onOpen: () -> Unit,
    onPay: (RecurringOccurrence) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val payment = schedule.payment
    val first = schedule.first
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
                Text(payment.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    formatAmount(payment.amountMinor, payment.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    !payment.active -> OccurrencePill(OccurrenceStatus.PAUSED)
                    first != null -> OccurrencePill(first.status)
                }
                StatusPill(payment.frequency.label, container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
                if (payment.isSubscription) {
                    StatusPill("Suscripción", container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        !payment.active -> "En pausa: no genera pendientes ni avisos"
                        schedule.overdue.size > 1 -> "${schedule.overdue.size} vencimientos sin pagar · el primero ${dueText(schedule.overdue.first(), today).lowercase()}"
                        first != null -> dueText(first, today)
                        else -> "Sin próximos vencimientos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (schedule.overdue.isNotEmpty()) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (first != null) {
                    TextButton(onClick = { onPay(first) }) { Text("Pagar") }
                }
            }
        }
    }
}

/** Formulario de alta y edición. Corto: lo opcional (descripción, aviso) va al final. */
@Composable
internal fun RecurringFormSheet(
    initial: RecurringPayment?,
    defaultCurrency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (RecurringPayment) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var amountText by rememberSaveable { mutableStateOf(initial?.let { Money.toInputText(it.amountMinor) } ?: "") }
    var currencyCode by rememberSaveable { mutableStateOf(initial?.currencyCode ?: defaultCurrency.code) }
    var frequency by rememberSaveable { mutableStateOf(initial?.frequency ?: RecurringFrequency.MONTHLY) }
    var startEpoch by rememberSaveable { mutableStateOf((initial?.startDate ?: LocalDate.now()).toEpochDay()) }
    var dayText by rememberSaveable { mutableStateOf((initial?.dayOfMonth ?: LocalDate.now().dayOfMonth).toString()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: ExpenseCategory.SUBSCRIPTIONS) }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var remind by rememberSaveable { mutableStateOf(initial?.reminder?.enabled ?: true) }
    var leadDays by rememberSaveable { mutableStateOf(initial?.reminder?.leadDays ?: 1) }
    var remindMinute by rememberSaveable { mutableStateOf(initial?.reminder?.minuteOfDay ?: (9 * 60)) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val startDate = LocalDate.ofEpochDay(startEpoch)
    val day = dayText.toIntOrNull()
    val errors = if (submitted) FinanceValidators.validateRecurring(name, amountText, day, startDate, null) else RecurringFormErrors()
    // Vista previa del primer vencimiento con la regla actual.
    val preview = if (day != null && day in 1..31) {
        val draft = RecurringPayment(0, name, 1, currencyCode, day, startDate, frequency = frequency)
        generateSequence(YearMonth.from(startDate)) { it.plusMonths(1) }.take(frequency.months + 1)
            .firstOrNull { RecurringPlanner.isDueIn(draft, it) }?.let { RecurringPlanner.dueDate(draft, it) }
    } else {
        null
    }

    FormSheet(title = if (initial == null) "Nuevo pago recurrente" else "Editar pago recurrente", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= Limits.TITLE_MAX) name = it },
            label = { Text("Nombre") },
            placeholder = { Text("Por ejemplo, Claude o Internet") },
            singleLine = true,
            isError = errors.name != null,
            supportingText = errors.name?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField("Importe", amountText, { amountText = it }, currencyCode, { currencyCode = it }, errors.amount)
        Text("Frecuencia", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurringFrequency.entries.forEach { f ->
                FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(f.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            DateField(
                label = "Desde",
                value = startDate,
                onValueChange = { date ->
                    date?.let {
                        // Si el día seguía al inicio anterior, lo acompaña: elegir "5 nov" deja el día 5.
                        if (dayText == LocalDate.ofEpochDay(startEpoch).dayOfMonth.toString()) dayText = it.dayOfMonth.toString()
                        startEpoch = it.toEpochDay()
                    }
                },
                modifier = Modifier.weight(1.4f),
            )
            OutlinedTextField(
                value = dayText,
                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) dayText = it },
                label = { Text("Día") },
                singleLine = true,
                isError = errors.day != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = errors.day ?: preview?.let {
                "Primer vencimiento: ${Formats.date(it)}. En meses más cortos vence el último día."
            } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = if (errors.day != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Categoría", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
            }
        }
        DescriptionField(description, { description = it }, null)

        SwitchRow("Recordarme antes de cada vencimiento", remind) { remind = it }
        if (remind) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderOptions.debtLeadDays.forEach { d ->
                    FilterChip(
                        selected = leadDays == d,
                        onClick = { leadDays = d },
                        label = { Text(if (d == 0) "El mismo día" else ReminderOptions.debtLeadLabel(d)) },
                    )
                }
            }
            TimeField(
                label = "Hora del aviso",
                value = LocalTime.of(remindMinute / 60, remindMinute % 60),
                onValueChange = { remindMinute = it.hour * 60 + it.minute },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                if (!FinanceValidators.validateRecurring(name, amountText, day, startDate, null).hasErrors) {
                    onSave(
                        RecurringPayment(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            amountMinor = Money.parseToMinor(amountText)!!,
                            currencyCode = currencyCode,
                            dayOfMonth = day!!,
                            startDate = startDate,
                            endDate = initial?.endDate,
                            frequency = frequency,
                            category = category,
                            description = description.trim(),
                            reminder = RecurringReminder(remind, leadDays, remindMinute),
                            active = initial?.active ?: true,
                        ),
                    )
                }
            },
        )
    }
}

/** Detalle: vencimientos, historial de pagos y acciones (editar, pausar, eliminar). */
@Composable
internal fun RecurringDetailSheet(
    schedule: RecurringSchedule,
    movements: List<Movement>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPay: (RecurringOccurrence) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onUndoPayment: (Movement) -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val payment = schedule.payment
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Próximos vencimientos sin pagar después del inmediato (como mucho tres en total).
    val upcoming = RecurringPlanner.upcoming(payment, movements, today).take(3)

    FormSheet(title = payment.name, onDismiss = onDismiss) {
        Text(
            "${formatAmount(payment.amountMinor, payment.currencyCode)} · ${payment.frequency.label.lowercase()} · día ${payment.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            buildString {
                append(payment.category.label).append(" · desde el ").append(Formats.date(payment.startDate))
                append(" · ")
                append(
                    if (payment.reminder.enabled) {
                        "aviso ${ReminderOptions.debtLeadLabel(payment.reminder.leadDays).lowercase()} a las " +
                            Formats.time(LocalTime.of(payment.reminder.minuteOfDay / 60, payment.reminder.minuteOfDay % 60))
                    } else {
                        "sin aviso"
                    },
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        if (payment.description.isNotBlank()) {
            Text(payment.description, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Un pago pendiente no reduce tu disponible. Al confirmarlo se registra un solo gasto en ${payment.currencyCode}.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = { onSetActive(!payment.active) }) { Text(if (payment.active) "Pausar" else "Reanudar") }
        }

        HorizontalDivider(color = scheme.outlineVariant)
        Text("Próximos vencimientos", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
        val dues = schedule.overdue + upcoming
        if (!payment.active) {
            Text("En pausa: no genera vencimientos. Reanúdalo para volver a verlos.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else if (dues.isEmpty()) {
            Text("No hay vencimientos próximos.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        dues.forEach { occurrence ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Formats.month(occurrence.period), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OccurrencePill(occurrence.status)
                        Text(dueText(occurrence, today), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { onPay(occurrence) }) { Text("Pagar") }
            }
        }

        HorizontalDivider(color = scheme.outlineVariant)
        Text("Pagos realizados", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
        if (schedule.paid.isEmpty()) {
            Text("Aún no hay pagos.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        schedule.paid.forEach { movement ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(movement.period?.let { Formats.month(it) } ?: "Pago", style = MaterialTheme.typography.bodyMedium)
                    Text("Pagado el ${Formats.date(movement.date)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text(
                    "-" + formatAmount(movement.amountMinor, movement.currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = QuipuColors.extra.negative,
                )
                IconButton(onClick = { onUndoPayment(movement) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Deshacer el pago de ${movement.period?.let { Formats.month(it) } ?: ""}")
                }
            }
        }

        HorizontalDivider(color = scheme.outlineVariant)
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Eliminar pago recurrente", color = scheme.error)
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "¿Eliminar «${payment.name}»?",
            message = "Dejará de generar vencimientos y avisos. Los pagos ya hechos se conservan como gastos en tu historial.",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Confirmar un vencimiento: importe (en la moneda del pago) y fecha efectiva. Si parece
 * que ya se anotó a mano como gasto, ofrece usar ese gasto en vez de registrar otro.
 */
@Composable
internal fun ConfirmRecurringDialog(
    occurrence: RecurringOccurrence,
    matches: List<Movement>,
    onDismiss: () -> Unit,
    onConfirm: (Long, LocalDate) -> Unit,
    onLink: (Movement) -> Unit,
) {
    val payment = occurrence.payment
    var amountText by rememberSaveable { mutableStateOf(Money.toInputText(payment.amountMinor)) }
    var dayEpoch by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val date = dayEpoch?.let { LocalDate.ofEpochDay(it) }
    val errors = if (submitted) FinanceValidators.validateMovement(amountText, date, "") else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagar ${payment.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${Formats.month(occurrence.period)} · vencimiento el ${Formats.date(occurrence.dueDate)}. " +
                        "Se registrará un gasto en ${payment.currencyCode}, sin conversión.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AmountField(
                    label = "Importe pagado",
                    value = amountText,
                    onValueChange = { amountText = it },
                    currencyCode = payment.currencyCode,
                    onCurrencyChange = {},
                    error = errors?.amount,
                    currencyEditable = false,
                )
                DateField(label = "Fecha del pago", value = date, onValueChange = { dayEpoch = it?.toEpochDay() }, error = errors?.date)
                if (matches.isNotEmpty()) {
                    HorizontalDivider()
                    Text("¿Ya lo anotaste como gasto?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Usa ese gasto para no contar el pago dos veces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    matches.take(3).forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    m.description.ifBlank { m.category?.label ?: "Gasto" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${formatAmount(m.amountMinor, m.currencyCode)} · ${Formats.date(m.date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onLink(m) }) { Text("Usar este") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (!FinanceValidators.validateMovement(amountText, date, "").hasErrors) {
                    onConfirm(Money.parseToMinor(amountText)!!, date!!)
                }
            }) { Text("Confirmar pago") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
