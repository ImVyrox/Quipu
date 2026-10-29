@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.money

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.DeclaredBalance
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.FinanceValidators
import com.quipu.app.domain.IncomeFormErrors
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.IncomePlanner
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.IncomeSourceFormErrors
import com.quipu.app.domain.Limits
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementFormErrors
import com.quipu.app.domain.MovementType
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.ConfirmDeleteDialog
import com.quipu.app.ui.components.DateField
import com.quipu.app.ui.components.FormActions
import com.quipu.app.ui.components.FormSheet
import java.time.LocalDate

/** Primer paso del botón +: elegir qué anotar. */
@Composable
internal fun KindChooserSheet(onDismiss: () -> Unit, onExpense: () -> Unit, onIncome: () -> Unit) {
    FormSheet(title = "¿Qué quieres anotar?", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onExpense, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text("Gasto")
            }
            OutlinedButton(onClick = onIncome, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text("Ingreso")
            }
        }
    }
}

/** Campo de importe con la moneda como prefijo; la moneda se cambia desde un menú si [currencyEditable]. */
@Composable
internal fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    currencyCode: String,
    onCurrencyChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    currencyEditable: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 13) onValueChange(it) },
            label = { Text(label) },
            prefix = { Text(currencyOf(currencyCode).symbol + " ") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        if (currencyEditable) {
            CurrencyMenu(currencyCode, onCurrencyChange)
        }
    }
}

@Composable
private fun CurrencyMenu(currencyCode: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("$currencyCode ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppCurrencies.all.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.code} · ${currency.name}") },
                    onClick = {
                        onChange(currency.code)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun DescriptionField(value: String, onValueChange: (String) -> Unit, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= Limits.NOTE_MAX) onValueChange(it) },
        label = { Text("Descripción (opcional)") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ExpenseFormSheet(
    initial: Movement?,
    defaultCurrency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (Movement) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(initial?.let { Money.toInputText(it.amountMinor) } ?: "") }
    var currencyCode by rememberSaveable { mutableStateOf(initial?.currencyCode ?: defaultCurrency.code) }
    var dayEpoch by rememberSaveable { mutableStateOf<Long?>((initial?.date ?: LocalDate.now()).toEpochDay()) }
    var category by rememberSaveable { mutableStateOf(initial?.category) }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var paid by rememberSaveable { mutableStateOf(initial?.pending?.not() ?: true) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val date = dayEpoch?.let { LocalDate.ofEpochDay(it) }
    val errors = if (submitted) FinanceValidators.validateExpense(amountText, date, category, description) else MovementFormErrors()

    FormSheet(title = if (initial == null) "Nuevo gasto" else "Editar gasto", onDismiss = onDismiss) {
        AmountField("Importe", amountText, { amountText = it }, currencyCode, { currencyCode = it }, errors.amount)
        Text("Categoría", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExpenseCategory.entries.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
            }
        }
        errors.category?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        DateField(
            label = if (paid) "Fecha" else "Fecha prevista",
            value = date,
            onValueChange = { dayEpoch = it?.toEpochDay() },
            error = errors.date,
        )
        DescriptionField(description, { description = it }, errors.description)
        SwitchRow("Ya lo pagué", paid) { paid = it }
        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                if (!FinanceValidators.validateExpense(amountText, date, category, description).hasErrors) {
                    onSave(
                        Movement(
                            id = initial?.id ?: 0L,
                            type = MovementType.EXPENSE,
                            amountMinor = Money.parseToMinor(amountText)!!,
                            currencyCode = currencyCode,
                            date = date!!,
                            description = description.trim(),
                            category = category,
                            pending = !paid,
                        ),
                    )
                }
            },
        )
    }
}

@Composable
internal fun IncomeFormSheet(
    initial: Movement?,
    sources: List<IncomeSource>,
    presetSourceId: Long?,
    defaultCurrency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (Movement) -> Unit,
) {
    // Al editar se ofrece también la fuente del ingreso aunque esté archivada.
    val choices = sources.filter { it.active || it.id == initial?.incomeSourceId }
    var sourceId by rememberSaveable { mutableStateOf(initial?.incomeSourceId ?: presetSourceId) }
    val source = choices.firstOrNull { it.id == sourceId }
    val hourly = source?.kind == IncomeKind.HOURLY

    var amountText by rememberSaveable {
        mutableStateOf(
            initial?.let { Money.toInputText(it.amountMinor) }
                ?: source?.fixedAmountMinor?.let { Money.toInputText(it) }
                ?: "",
        )
    }
    var currencyCode by rememberSaveable { mutableStateOf(initial?.currencyCode ?: defaultCurrency.code) }
    var hoursText by rememberSaveable {
        mutableStateOf(initial?.workedMinutes?.let { Formats.hours(it).removeSuffix(" h") } ?: "")
    }
    /** Fecha del ingreso; en los ingresos por horas, el día trabajado. */
    var dayEpoch by rememberSaveable {
        mutableStateOf<Long?>((initial?.workDate ?: initial?.date ?: LocalDate.now()).toEpochDay())
    }
    var receivedEpoch by rememberSaveable {
        mutableStateOf<Long?>(if (initial != null && !initial.pending) initial.date.toEpochDay() else LocalDate.now().toEpochDay())
    }
    var received by rememberSaveable { mutableStateOf(initial?.pending?.not() ?: true) }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val date = dayEpoch?.let { LocalDate.ofEpochDay(it) }
    val receivedDate = receivedEpoch?.let { LocalDate.ofEpochDay(it) }
    fun validate() = FinanceValidators.validateIncome(hourly, amountText, hoursText, date, received, receivedDate, description)
    val errors = if (submitted) validate() else IncomeFormErrors()
    val effectiveCurrency = source?.currencyCode ?: currencyCode
    val minutes = IncomePlanner.parseHoursToMinutes(hoursText)
    val hourlyAmount = if (hourly && minutes != null) IncomePlanner.amountForMinutes(source!!.hourlyRateMinor ?: 0, minutes) else null

    FormSheet(title = if (initial == null) "Nuevo ingreso" else "Editar ingreso", onDismiss = onDismiss) {
        if (choices.isNotEmpty()) {
            Text("Fuente", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = sourceId == null, onClick = { sourceId = null }, label = { Text("Sin fuente") })
                choices.forEach { s ->
                    FilterChip(
                        selected = sourceId == s.id,
                        onClick = {
                            sourceId = s.id
                            if (s.kind == IncomeKind.FIXED && amountText.isBlank()) {
                                amountText = s.fixedAmountMinor?.let { Money.toInputText(it) } ?: ""
                            }
                        },
                        label = { Text(s.name) },
                    )
                }
            }
        }

        if (hourly) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = "Día trabajado",
                    value = date,
                    onValueChange = { dayEpoch = it?.toEpochDay() },
                    error = errors.date,
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { if (it.length <= 6) hoursText = it },
                    label = { Text("Horas") },
                    singleLine = true,
                    isError = errors.hours != null,
                    supportingText = errors.hours?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = if (hourlyAmount != null) {
                    "${Formats.hours(minutes!!)} × ${formatAmount(source!!.hourlyRateMinor ?: 0, effectiveCurrency)} = " +
                        formatAmount(hourlyAmount, effectiveCurrency)
                } else {
                    "Escribe las horas: el importe se calcula con ${formatAmount(source!!.hourlyRateMinor ?: 0, effectiveCurrency)} por hora."
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            SwitchRow("Ya lo cobré", received) { received = it }
            if (received) {
                DateField(
                    label = "Fecha de cobro",
                    value = receivedDate,
                    onValueChange = { receivedEpoch = it?.toEpochDay() },
                    error = errors.receivedDate,
                )
            }
        } else {
            AmountField(
                label = "Importe",
                value = amountText,
                onValueChange = { amountText = it },
                currencyCode = effectiveCurrency,
                onCurrencyChange = { currencyCode = it },
                error = errors.amount,
                currencyEditable = source == null,
            )
            DateField(
                label = if (received) "Fecha en que lo recibiste" else "Fecha prevista",
                value = date,
                onValueChange = { dayEpoch = it?.toEpochDay() },
                error = errors.date,
            )
            SwitchRow("Ya lo recibí", received) { received = it }
            if (source?.kind == IncomeKind.FIXED) {
                Text(
                    "Un ingreso fijo se anota una vez por mes (el mes de la fecha).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DescriptionField(description, { description = it }, errors.description)
        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                if (!validate().hasErrors) {
                    val movement = if (hourly) {
                        Movement(
                            id = initial?.id ?: 0L,
                            type = MovementType.INCOME,
                            amountMinor = hourlyAmount!!,
                            currencyCode = effectiveCurrency,
                            // Sin cobrar, la fecha prevista es el día trabajado.
                            date = if (received) receivedDate!! else date!!,
                            description = description.trim(),
                            pending = !received,
                            incomeSourceId = source!!.id,
                            workDate = date,
                            workedMinutes = minutes,
                        )
                    } else {
                        Movement(
                            id = initial?.id ?: 0L,
                            type = MovementType.INCOME,
                            amountMinor = Money.parseToMinor(amountText)!!,
                            currencyCode = effectiveCurrency,
                            date = date!!,
                            description = description.trim(),
                            pending = !received,
                            incomeSourceId = source?.id,
                        )
                    }
                    onSave(movement)
                }
            },
        )
    }
}

@Composable
internal fun SourceFormSheet(
    initial: IncomeSource?,
    defaultCurrency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (IncomeSource) -> Unit,
    onRemove: (IncomeSource) -> Unit,
) {
    val example = IncomePlanner.EXAMPLE_HOURLY
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: IncomeKind.HOURLY) }
    var currencyCode by rememberSaveable { mutableStateOf(initial?.currencyCode ?: defaultCurrency.code) }
    var amountText by rememberSaveable { mutableStateOf(initial?.fixedAmountMinor?.let { Money.toInputText(it) } ?: "") }
    // Una fuente por horas nueva parte del ejemplo (18 por hora, 42 horas al mes); se puede cambiar.
    var rateText by rememberSaveable {
        mutableStateOf(Money.toInputText(initial?.hourlyRateMinor ?: example.hourlyRateMinor!!))
    }
    var hoursText by rememberSaveable {
        mutableStateOf(Formats.hours(initial?.plannedMinutesPerMonth ?: example.plannedMinutesPerMonth!!).removeSuffix(" h"))
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    fun validate() = FinanceValidators.validateIncomeSource(name, kind, amountText, rateText, hoursText)
    val errors = if (submitted) validate() else IncomeSourceFormErrors()

    FormSheet(title = if (initial == null) "Nueva fuente de ingreso" else "Editar fuente", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= Limits.TITLE_MAX) name = it },
            label = { Text("Nombre") },
            placeholder = { Text("Sueldo, clases, ventas…") },
            singleLine = true,
            isError = errors.name != null,
            supportingText = errors.name?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val kinds = listOf(IncomeKind.FIXED to "Fijo", IncomeKind.VARIABLE to "Variable", IncomeKind.HOURLY to "Por horas")
            kinds.forEachIndexed { index, (k, label) ->
                SegmentedButton(
                    selected = kind == k,
                    onClick = { kind = k },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                ) { Text(label) }
            }
        }
        when (kind) {
            IncomeKind.FIXED -> AmountField("Importe mensual", amountText, { amountText = it }, currencyCode, { currencyCode = it }, errors.amount)
            IncomeKind.VARIABLE -> {
                Text(
                    "Sin importe previsto: anota cada ingreso cuando llegue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Moneda", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    CurrencyMenu(currencyCode) { currencyCode = it }
                }
            }
            IncomeKind.HOURLY -> {
                AmountField("Tarifa por hora", rateText, { rateText = it }, currencyCode, { currencyCode = it }, errors.rate)
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { if (it.length <= 6) hoursText = it },
                    label = { Text("Horas previstas al mes") },
                    singleLine = true,
                    isError = errors.hours != null,
                    supportingText = errors.hours?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                val rate = Money.parseToMinor(rateText)
                val minutes = IncomePlanner.parseHoursToMinutes(hoursText)
                if (rate != null && minutes != null) {
                    Text(
                        "Previsto: ${formatAmount(IncomePlanner.amountForMinutes(rate, minutes), currencyCode)} al mes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (initial != null) {
            Text(
                "Cambiar la moneda no convierte los ingresos ya anotados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                if (!validate().hasErrors) {
                    onSave(
                        IncomeSource(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            kind = kind,
                            currencyCode = currencyCode,
                            fixedAmountMinor = if (kind == IncomeKind.FIXED) Money.parseToMinor(amountText) else null,
                            hourlyRateMinor = if (kind == IncomeKind.HOURLY) Money.parseToMinor(rateText) else null,
                            plannedMinutesPerMonth = if (kind == IncomeKind.HOURLY) IncomePlanner.parseHoursToMinutes(hoursText) else null,
                            active = initial?.active ?: true,
                        ),
                    )
                }
            },
        )
        if (initial != null) {
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Quitar fuente", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRemove && initial != null) {
        ConfirmDeleteDialog(
            title = "¿Quitar «${initial.name}»?",
            message = "Si tiene ingresos anotados se archivará y su historial se conservará. Si no tiene, se eliminará.",
            onConfirm = {
                confirmRemove = false
                onRemove(initial)
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

/** Marca un movimiento pendiente como cobrado/pagado, con la fecha real del dinero. */
@Composable
internal fun SettleDialog(movement: Movement, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val income = movement.type == MovementType.INCOME
    var dayEpoch by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    val date = dayEpoch?.let { LocalDate.ofEpochDay(it) }
    val tooEarly = date != null && movement.workDate != null && date.isBefore(movement.workDate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (income) "Marcar como cobrado" else "Marcar como pagado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${formatAmount(movement.amountMinor, movement.currencyCode)} pasará a contar en tu saldo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DateField(
                    label = if (income) "Fecha de cobro" else "Fecha de pago",
                    value = date,
                    onValueChange = { dayEpoch = it?.toEpochDay() },
                    error = if (tooEarly) "No puede ser anterior al día trabajado" else null,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = date != null && !tooEarly, onClick = { onConfirm(date!!) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Dinero real que el usuario dice tener hoy en una moneda. No crea movimientos. */
@Composable
internal fun DeclareDialog(
    defaultCurrency: AppCurrency,
    declared: List<DeclaredBalance>,
    onDismiss: () -> Unit,
    onSave: (String, Long, LocalDate) -> Unit,
    onClear: (String) -> Unit,
) {
    var currencyCode by rememberSaveable { mutableStateOf(defaultCurrency.code) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val error = if (submitted) FinanceValidators.validateDeclared(amountText) else null
    val existing = declared.firstOrNull { it.currencyCode == currencyCode }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dinero real") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "¿Cuánto dinero tienes hoy de verdad, contando lo ahorrado? Se compara con tu total calculado; tus movimientos no cambian.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AmountField("Tengo hoy", amountText, { amountText = it }, currencyCode, { currencyCode = it }, error)
                if (existing != null) {
                    TextButton(onClick = { onClear(currencyCode) }) {
                        Text("Quitar lo declarado en $currencyCode", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (FinanceValidators.validateDeclared(amountText) == null) {
                    onSave(currencyCode, Money.parseToMinor(amountText)!!, LocalDate.now())
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
