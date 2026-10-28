@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.FinanceValidators
import com.quipu.app.domain.GoalFormErrors
import com.quipu.app.domain.GoalProgress
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.Limits
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.SavingsGoal
import com.quipu.app.domain.TransferError
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.ConfirmDeleteDialog
import com.quipu.app.ui.components.DateField
import com.quipu.app.ui.components.FormActions
import com.quipu.app.ui.components.FormSheet
import com.quipu.app.ui.components.StatusPill
import com.quipu.app.ui.theme.QuipuColors
import java.time.LocalDate

/** Nombre corto de cada movimiento de ahorro. */
internal fun savingLabel(type: MovementType): String = when (type) {
    MovementType.SAVING_DEPOSIT -> "Aportación"
    MovementType.SAVING_WITHDRAWAL -> "Retiro"
    MovementType.SAVING_OPENING -> "Ahorro inicial"
    MovementType.INCOME -> "Ingreso"
    MovementType.EXPENSE -> "Gasto"
}

/** Signo respecto a lo ahorrado: + aporta al objetivo, − lo reduce. */
internal fun savingSigned(movement: Movement): String =
    (if (Ledger.savingEffect(movement) < 0) "-" else "+") + formatAmount(movement.amountMinor, movement.currencyCode)

@Composable
private fun StatusTag(status: GoalStatus, reached: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val extra = QuipuColors.extra
    when {
        status == GoalStatus.COMPLETED -> StatusPill("Completado", container = extra.positiveContainer, content = extra.positive)
        status == GoalStatus.PAUSED -> StatusPill("En pausa", container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
        reached -> StatusPill("Meta alcanzada", container = extra.positiveContainer, content = extra.positive)
    }
}

@Composable
internal fun GoalCard(
    progress: GoalProgress,
    onOpen: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val goal = progress.goal
    val code = goal.currencyCode
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.status == GoalStatus.ACTIVE) scheme.surface else scheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                StatusTag(goal.status, progress.reached)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatAmount(progress.savedMinor, code),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    " de ${formatAmount(goal.targetMinor, code)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                )
                Text(
                    "${progress.percent} %",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = buildString {
                    if (progress.reached) append("Meta cubierta") else append("Faltan ").append(formatAmount(progress.remainingMinor, code))
                    goal.deadline?.let { append(" · hasta el ").append(Formats.date(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (goal.status == GoalStatus.ACTIVE) {
                    TextButton(onClick = onDeposit) { Text("Aportar") }
                }
                if (progress.savedMinor > 0) {
                    TextButton(onClick = onWithdraw) { Text("Retirar") }
                }
            }
        }
    }
}

@Composable
internal fun GoalFormSheet(
    initial: SavingsGoal?,
    hasMovements: Boolean,
    defaultCurrency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (SavingsGoal, Long?) -> Unit,
) {
    val today = LocalDate.now()
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var targetText by rememberSaveable { mutableStateOf(initial?.let { Money.toInputText(it.targetMinor) } ?: "") }
    var currencyCode by rememberSaveable { mutableStateOf(initial?.currencyCode ?: defaultCurrency.code) }
    var deadlineEpoch by rememberSaveable { mutableStateOf(initial?.deadline?.toEpochDay()) }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var openingText by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val createdDate = initial?.createdDate ?: today
    val deadline = deadlineEpoch?.let { LocalDate.ofEpochDay(it) }
    fun validate() = FinanceValidators.validateGoal(name, targetText, deadline, createdDate, description)
    val errors = if (submitted) validate() else GoalFormErrors()
    val openingError = if (submitted && openingText.isNotBlank()) FinanceValidators.validateSavingAmount(openingText) else null

    FormSheet(title = if (initial == null) "Nuevo objetivo de ahorro" else "Editar objetivo", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= Limits.TITLE_MAX) name = it },
            label = { Text("Nombre") },
            placeholder = { Text("Viaje, laptop, emergencias…") },
            singleLine = true,
            isError = errors.name != null,
            supportingText = errors.name?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(
            label = "Meta",
            value = targetText,
            onValueChange = { targetText = it },
            currencyCode = currencyCode,
            onCurrencyChange = { currencyCode = it },
            error = errors.target,
            currencyEditable = !hasMovements,
        )
        DateField(
            label = "Fecha límite (opcional)",
            value = deadline,
            onValueChange = { deadlineEpoch = it?.toEpochDay() },
            error = errors.deadline,
            clearable = true,
            placeholder = "Sin fecha límite",
        )
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= Limits.NOTE_MAX) description = it },
            label = { Text("Descripción (opcional)") },
            singleLine = true,
            isError = errors.description != null,
            supportingText = errors.description?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        if (initial == null) {
            AmountField(
                label = "Ya tengo ahorrado (opcional)",
                value = openingText,
                onValueChange = { openingText = it },
                currencyCode = currencyCode,
                onCurrencyChange = {},
                error = openingError,
                currencyEditable = false,
            )
            Text(
                "Solo si ya tenías dinero guardado fuera de Quipu para esta meta. No sale de tu disponible ni cuenta como ingreso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (hasMovements) {
            Text(
                "La moneda no se puede cambiar porque el objetivo ya tiene movimientos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                val openingOk = openingText.isBlank() || FinanceValidators.validateSavingAmount(openingText) == null
                if (!validate().hasErrors && openingOk) {
                    onSave(
                        SavingsGoal(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            currencyCode = currencyCode,
                            targetMinor = Money.parseToMinor(targetText)!!,
                            createdDate = createdDate,
                            deadline = deadline,
                            description = description.trim(),
                            status = initial?.status ?: GoalStatus.ACTIVE,
                            completedDate = initial?.completedDate,
                        ),
                        openingText.takeIf { it.isNotBlank() }?.let { Money.parseToMinor(it) },
                    )
                }
            },
        )
    }
}

/** Detalle de un objetivo: progreso, acciones y su historial de movimientos. */
@Composable
internal fun GoalDetailSheet(
    progress: GoalProgress,
    history: List<Movement>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSaving: (MovementType) -> Unit,
    onStatus: (GoalStatus) -> Unit,
    onDeleteMovement: (Movement) -> Unit,
    onDeleteGoal: () -> Unit,
) {
    val goal = progress.goal
    val scheme = MaterialTheme.colorScheme
    val hasOpening = history.any { it.type == MovementType.SAVING_OPENING }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    FormSheet(title = goal.name, onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(goal.status.label, container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
            Text(
                buildString {
                    append("Creado el ").append(Formats.date(goal.createdDate))
                    goal.completedDate?.let { append(" · completado el ").append(Formats.date(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (goal.description.isNotBlank()) {
            Text(goal.description, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "${formatAmount(progress.savedMinor, goal.currencyCode)} de ${formatAmount(goal.targetMinor, goal.currencyCode)} · ${progress.percent} %",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
        Text(
            if (progress.reached) "Meta cubierta." else "Faltan ${formatAmount(progress.remainingMinor, goal.currencyCode)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (goal.status == GoalStatus.ACTIVE) {
                OutlinedButton(onClick = { onSaving(MovementType.SAVING_DEPOSIT) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("Aportar")
                }
            }
            if (progress.savedMinor > 0) {
                OutlinedButton(onClick = { onSaving(MovementType.SAVING_WITHDRAWAL) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("Retirar")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onEdit) { Text("Editar") }
            when (goal.status) {
                GoalStatus.ACTIVE -> {
                    TextButton(onClick = { onStatus(GoalStatus.PAUSED) }) { Text("Pausar") }
                    TextButton(onClick = { onStatus(GoalStatus.COMPLETED) }) { Text("Completar") }
                }
                GoalStatus.PAUSED -> {
                    TextButton(onClick = { onStatus(GoalStatus.ACTIVE) }) { Text("Reanudar") }
                    TextButton(onClick = { onStatus(GoalStatus.COMPLETED) }) { Text("Completar") }
                }
                GoalStatus.COMPLETED -> TextButton(onClick = { onStatus(GoalStatus.ACTIVE) }) { Text("Reactivar") }
            }
        }
        if (!hasOpening) {
            TextButton(onClick = { onSaving(MovementType.SAVING_OPENING) }) {
                Text("Registrar ahorro inicial")
            }
        }

        HorizontalDivider(color = scheme.outlineVariant)
        Text("Historial", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
        if (history.isEmpty()) {
            Text("Aún no hay movimientos.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        history.forEach { movement ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        savingLabel(movement.type) + (movement.description.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(Formats.relativeDate(movement.date, today), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text(
                    savingSigned(movement),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (Ledger.savingEffect(movement) < 0) QuipuColors.extra.negative else QuipuColors.extra.positive,
                )
                IconButton(onClick = { onDeleteMovement(movement) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar ${savingLabel(movement.type).lowercase()}")
                }
            }
        }

        HorizontalDivider(color = scheme.outlineVariant)
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Eliminar objetivo", color = scheme.error)
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "¿Eliminar «${goal.name}»?",
            message = if (progress.savedMinor > 0) {
                "Lo ahorrado (${formatAmount(progress.savedMinor, goal.currencyCode)}) volverá a tu disponible con un retiro. " +
                    "El historial se conserva."
            } else {
                "El objetivo se eliminará. Su historial se conserva."
            },
            onConfirm = {
                confirmDelete = false
                onDeleteGoal()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Aportación, retiro o ahorro inicial. Muestra el límite y lo comprueba antes de guardar. */
@Composable
internal fun SavingDialog(
    progress: GoalProgress,
    type: MovementType,
    movements: List<Movement>,
    onDismiss: () -> Unit,
    onConfirm: (Long, LocalDate, String) -> Unit,
) {
    val goal = progress.goal
    val code = goal.currencyCode
    var amountText by rememberSaveable { mutableStateOf("") }
    var dayEpoch by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    var description by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val date = dayEpoch?.let { LocalDate.ofEpochDay(it) }

    fun check(): String? {
        FinanceValidators.validateSavingAmount(amountText)?.let { return it }
        val minor = Money.parseToMinor(amountText)!!
        val error: TransferError? = if (type == MovementType.SAVING_OPENING) {
            Ledger.validateOpening(minor, goal, movements)
        } else {
            Ledger.validateTransfer(type, minor, code, goal, movements)
        }
        return error?.message
    }
    val amountError = if (submitted) check() else null

    val (title, hint) = when (type) {
        MovementType.SAVING_DEPOSIT ->
            "Aportar a «${goal.name}»" to "Disponible: ${formatAmount(Ledger.available(movements)[code], code)}. Pasa de tu disponible al ahorro."
        MovementType.SAVING_WITHDRAWAL ->
            "Retirar de «${goal.name}»" to "Ahorrado: ${formatAmount(progress.savedMinor, code)}. Vuelve a tu disponible."
        else ->
            "Ahorro inicial" to "Dinero que ya tenías guardado fuera de Quipu para esta meta. No sale de tu disponible ni es un ingreso."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                AmountField("Importe", amountText, { amountText = it }, code, {}, amountError, currencyEditable = false)
                DateField(label = "Fecha", value = date, onValueChange = { dayEpoch = it?.toEpochDay() })
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= Limits.NOTE_MAX) description = it },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = date != null,
                onClick = {
                    submitted = true
                    if (check() == null) onConfirm(Money.parseToMinor(amountText)!!, date!!, description.trim())
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
