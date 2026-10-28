@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.CurrencyAmounts
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.IncomeProgress
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementKindFilter
import com.quipu.app.domain.MovementType
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.ConfirmDeleteDialog
import com.quipu.app.ui.components.EmptyState
import com.quipu.app.ui.components.SectionHeader
import com.quipu.app.ui.components.StatusPill
import com.quipu.app.ui.theme.QuipuColors
import java.time.LocalDate

/** Qué formulario está abierto. Se guarda como texto para sobrevivir a rotaciones. */
private enum class MoneySheet { CHOOSER, EXPENSE, INCOME, SOURCE }

@Composable
fun MoneyScreen(
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    viewModel: MoneyViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var sheet by rememberSaveable { mutableStateOf<MoneySheet?>(null) }
    var editingMovementId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Fuente preseleccionada al anotar un cobro desde su tarjeta. */
    var presetSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var settlingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var declaring by rememberSaveable { mutableStateOf(false) }

    val today = LocalDate.now()
    val sourceNames = state.sources.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dinero") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingMovementId = null
                presetSourceId = null
                sheet = MoneySheet.CHOOSER
            }) {
                Icon(Icons.Default.Add, contentDescription = "Anotar ingreso o gasto")
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (!state.loaded) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {}
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PeriodBar(
                    label = state.filter.month?.let { Formats.month(it) } ?: "Todo el historial",
                    allTime = state.filter.month == null,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onAllTime = viewModel::setAllTime,
                )
            }
            item {
                BalanceCard(state = state, onDeclare = { declaring = true })
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Relleno inferior igual al superior para centrarlo con el botón.
                    SectionHeader(
                        "Fuentes de ingreso · ${Formats.month(state.progressMonth).lowercase()}",
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    TextButton(onClick = {
                        editingSourceId = null
                        sheet = MoneySheet.SOURCE
                    }) { Text("Nueva") }
                }
            }
            if (state.progress.isEmpty()) {
                item {
                    HintCard(
                        "Añade tu sueldo, un trabajo por horas o ingresos variables para ver lo previsto " +
                            "frente a lo recibido.",
                    )
                }
            }
            items(state.progress, key = { "source-${it.source.id}" }) { progress ->
                SourceCard(
                    progress = progress,
                    onRegister = {
                        editingMovementId = null
                        presetSourceId = progress.source.id
                        sheet = MoneySheet.INCOME
                    },
                    onEdit = {
                        editingSourceId = progress.source.id
                        sheet = MoneySheet.SOURCE
                    },
                )
            }
            item {
                SectionHeader("Movimientos")
                HistoryFilters(
                    kind = state.filter.kind,
                    category = state.filter.category,
                    onKind = viewModel::setKind,
                    onCategory = viewModel::setCategory,
                )
            }
            if (state.visible.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.all.isEmpty()) "Aún no anotaste movimientos" else "Nada por aquí",
                        message = if (state.all.isEmpty()) {
                            "Toca el botón + para anotar un ingreso o un gasto."
                        } else {
                            "No hay movimientos con este periodo o filtro."
                        },
                        modifier = Modifier.heightIn(max = 220.dp),
                    )
                }
            }
            items(state.visible, key = { "movement-${it.id}" }) { movement ->
                MovementCard(
                    movement = movement,
                    sourceName = movement.incomeSourceId?.let { sourceNames[it] },
                    today = today,
                    onEdit = {
                        editingMovementId = movement.id
                        presetSourceId = null
                        sheet = if (movement.type == MovementType.INCOME) MoneySheet.INCOME else MoneySheet.EXPENSE
                    },
                    onDelete = { deletingId = movement.id },
                    onSettle = { settlingId = movement.id },
                )
            }
        }
    }

    val editingMovement = state.all.firstOrNull { it.id == editingMovementId }
    when (sheet) {
        MoneySheet.CHOOSER -> KindChooserSheet(
            onDismiss = { sheet = null },
            onExpense = { sheet = MoneySheet.EXPENSE },
            onIncome = { sheet = MoneySheet.INCOME },
        )
        MoneySheet.EXPENSE -> ExpenseFormSheet(
            initial = editingMovement,
            defaultCurrency = state.defaultCurrency,
            onDismiss = { sheet = null },
            onSave = {
                viewModel.save(it)
                sheet = null
            },
        )
        MoneySheet.INCOME -> IncomeFormSheet(
            initial = editingMovement,
            sources = state.sources,
            presetSourceId = presetSourceId,
            defaultCurrency = state.defaultCurrency,
            onDismiss = { sheet = null },
            onSave = {
                viewModel.save(it)
                sheet = null
            },
        )
        MoneySheet.SOURCE -> SourceFormSheet(
            initial = state.sources.firstOrNull { it.id == editingSourceId },
            defaultCurrency = state.defaultCurrency,
            onDismiss = { sheet = null },
            onSave = {
                viewModel.saveSource(it)
                sheet = null
            },
            onRemove = {
                viewModel.removeSource(it)
                sheet = null
            },
        )
        null -> Unit
    }

    deletingId?.let { id ->
        state.all.firstOrNull { it.id == id }?.let { movement ->
            val noun = if (movement.type == MovementType.INCOME) "ingreso" else "gasto"
            ConfirmDeleteDialog(
                title = "¿Eliminar $noun?",
                message = "Se eliminará el $noun de ${formatAmount(movement.amountMinor, movement.currencyCode)} " +
                    "del ${Formats.date(movement.date)}. Esta acción no se puede deshacer.",
                onConfirm = {
                    viewModel.delete(movement)
                    deletingId = null
                },
                onDismiss = { deletingId = null },
            )
        }
    }

    settlingId?.let { id ->
        state.all.firstOrNull { it.id == id }?.let { movement ->
            SettleDialog(
                movement = movement,
                onDismiss = { settlingId = null },
                onConfirm = { date ->
                    viewModel.markSettled(movement, date)
                    settlingId = null
                },
            )
        }
    }

    if (declaring) {
        DeclareDialog(
            defaultCurrency = state.defaultCurrency,
            declared = state.declared.map { it.declared },
            onDismiss = { declaring = false },
            onSave = { code, minor, date ->
                viewModel.declare(code, minor, date)
                declaring = false
            },
            onClear = { code ->
                viewModel.clearDeclared(code)
                declaring = false
            },
        )
    }
}

// ---- Formato ----

internal fun currencyOf(code: String): AppCurrency = AppCurrencies.byCode(code)

internal fun formatAmount(minor: Long, code: String): String = Money.format(minor, currencyOf(code))

private fun signedAmount(minor: Long, code: String): String =
    if (minor > 0) "+" + formatAmount(minor, code) else formatAmount(minor, code)

/** Una línea por moneda; si no hay nada, cero en la moneda predeterminada. */
private fun amountLines(amounts: CurrencyAmounts, fallback: AppCurrency, signed: Boolean = false): List<Pair<String, Long>> {
    val nonZero = amounts.nonZero().byCode.toList()
    val list = nonZero.ifEmpty { listOf(fallback.code to 0L) }
    return list.map { (code, minor) -> (if (signed) signedAmount(minor, code) else formatAmount(minor, code)) to minor }
}

@Composable
private fun signColor(minor: Long): Color = when {
    minor > 0 -> QuipuColors.extra.positive
    minor < 0 -> QuipuColors.extra.negative
    else -> MaterialTheme.colorScheme.onSurface
}

// ---- Piezas de la pantalla ----

@Composable
private fun MoneyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun PeriodBar(
    label: String,
    allTime: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAllTime: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = !allTime) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = !allTime) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
        }
        FilterChip(
            selected = allTime,
            onClick = { onAllTime(!allTime) },
            label = { Text("Todo") },
        )
    }
}

@Composable
private fun BalanceCard(state: MoneyUiState, onDeclare: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fallback = state.defaultCurrency
    MoneyCard {
        Text("Saldo calculado", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        amountLines(state.available, fallback).forEach { (text, minor) ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (minor < 0) QuipuColors.extra.negative else scheme.onSurface,
            )
        }
        Text(
            "Ingresos recibidos menos gastos pagados, con todos tus movimientos hasta hoy. " +
                "Los préstamos no cuentan.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = scheme.outlineVariant)
        Text(
            if (state.filter.month == null) "En todo el historial" else "En ${Formats.month(state.filter.month).lowercase()}",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("Ingresos recibidos", amountLines(state.totals.income, fallback), QuipuColors.extra.positive, Modifier.weight(1f))
            Figure("Gastos", amountLines(state.totals.expense, fallback), QuipuColors.extra.negative, Modifier.weight(1f))
        }
        Figure("Saldo del periodo", amountLines(state.totals.net, fallback, signed = true), null)

        val hasForecast = !state.projected.isEmpty || !state.pending.income.isEmpty || !state.pending.expense.isEmpty
        if (hasForecast) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = scheme.outlineVariant)
            Text("Previsto (no cuenta en el saldo)", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            if (!state.projected.isEmpty) {
                ForecastLine("Ingresos previstos al mes", state.projected)
            }
            if (!state.pending.income.isEmpty) ForecastLine("Por cobrar", state.pending.income)
            if (!state.pending.expense.isEmpty) ForecastLine("Gastos por pagar", state.pending.expense)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = scheme.outlineVariant)
        if (state.declared.isEmpty()) {
            Text(
                "¿Quieres comparar con el dinero que de verdad tienes? Decláralo; no cambia tus movimientos.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            Text("Dinero real declarado", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            state.declared.forEach { comparison ->
                val code = comparison.declared.currencyCode
                val diff = comparison.differenceMinor
                Text(
                    "${formatAmount(comparison.declared.amountMinor, code)} · ${Formats.date(comparison.declared.date)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = when {
                        diff == 0L -> "Coincide con el saldo calculado de ese día."
                        diff > 0 -> "Tienes ${formatAmount(diff, code)} más que el saldo calculado: puede faltar anotar algún ingreso."
                        else -> "Tienes ${formatAmount(-diff, code)} menos que el saldo calculado: puede faltar anotar algún gasto."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDeclare, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (state.declared.isEmpty()) "Declarar dinero real" else "Actualizar dinero real")
        }
    }
}

@Composable
private fun Figure(label: String, lines: List<Pair<String, Long>>, color: Color?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { (text, minor) ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color ?: signColor(minor),
            )
        }
    }
}

@Composable
private fun ForecastLine(label: String, amounts: CurrencyAmounts) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(horizontalAlignment = Alignment.End) {
            amounts.nonZero().byCode.forEach { (code, minor) ->
                Text(formatAmount(minor, code), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun HintCard(text: String) {
    MoneyCard {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun kindLabel(kind: IncomeKind): String = when (kind) {
    IncomeKind.FIXED -> "Fijo"
    IncomeKind.VARIABLE -> "Variable"
    IncomeKind.HOURLY -> "Por horas"
}

@Composable
private fun SourceCard(progress: IncomeProgress, onRegister: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = progress.source
    val code = source.currencyCode
    MoneyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(kindLabel(source.kind), container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
                    if (source.kind == IncomeKind.HOURLY && source.hourlyRateMinor != null) {
                        Text(
                            "${formatAmount(source.hourlyRateMinor, code)} por hora",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar ${source.name}")
            }
        }

        if (progress.plannedMinutes != null) {
            val planned = progress.plannedMinutes
            Text(
                "Horas: ${Formats.hours(progress.workedMinutes)} trabajadas de ${Formats.hours(planned)} previstas",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { if (planned > 0) (progress.workedMinutes.toFloat() / planned).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val projected = progress.projectedMinor
        Text(
            text = if (projected != null) {
                "Recibido ${formatAmount(progress.receivedMinor, code)} de ${formatAmount(projected, code)} previstos"
            } else {
                "Recibido ${formatAmount(progress.receivedMinor, code)} este mes"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = QuipuColors.extra.positive,
        )
        if (progress.toCollectMinor > 0) {
            Text(
                "Por cobrar: ${formatAmount(progress.toCollectMinor, code)}",
                style = MaterialTheme.typography.bodySmall,
                color = QuipuColors.extra.soon,
            )
        }
        TextButton(onClick = onRegister, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (source.kind == IncomeKind.HOURLY) "Anotar horas y cobro" else "Anotar ingreso")
        }
    }
}

@Composable
private fun HistoryFilters(
    kind: MovementKindFilter,
    category: ExpenseCategory?,
    onKind: (MovementKindFilter) -> Unit,
    onCategory: (ExpenseCategory?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = kind == MovementKindFilter.ALL, onClick = { onKind(MovementKindFilter.ALL) }, label = { Text("Todos") })
            FilterChip(selected = kind == MovementKindFilter.INCOME, onClick = { onKind(MovementKindFilter.INCOME) }, label = { Text("Ingresos") })
            FilterChip(selected = kind == MovementKindFilter.EXPENSE, onClick = { onKind(MovementKindFilter.EXPENSE) }, label = { Text("Gastos") })
        }
        if (kind != MovementKindFilter.INCOME) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = category == null, onClick = { onCategory(null) }, label = { Text("Cualquier categoría") })
                ExpenseCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { onCategory(c) }, label = { Text(c.label) })
                }
            }
        }
    }
}

@Composable
private fun MovementCard(
    movement: Movement,
    sourceName: String?,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = QuipuColors.extra
    val income = movement.type == MovementType.INCOME
    val title = movement.description.ifBlank {
        if (income) sourceName ?: "Ingreso" else movement.category?.label ?: "Gasto"
    }
    val amountText = (if (income) "+" else "-") + formatAmount(movement.amountMinor, movement.currencyCode)

    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (movement.pending) scheme.surfaceContainerLow else scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        movement.pending -> scheme.onSurfaceVariant
                        income -> extra.positive
                        else -> extra.negative
                    },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            // La etiqueta de categoría o fuente solo aporta si el título es la descripción.
            val tag = if (movement.description.isBlank()) null else if (income) sourceName else movement.category?.label
            if (movement.pending || tag != null) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (movement.pending) {
                        StatusPill(if (income) "Por cobrar" else "Por pagar", container = extra.soonContainer, content = extra.soon)
                    }
                    tag?.let { StatusPill(it, container = scheme.surfaceVariant, content = scheme.onSurfaceVariant) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(Formats.relativeDate(movement.date, today))
                        movement.workedMinutes?.let { minutes ->
                            append(" · ").append(Formats.hours(minutes))
                            movement.workDate?.takeIf { it != movement.date }?.let {
                                append(" trabajadas el ").append(Formats.date(it))
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (movement.pending) {
                    TextButton(onClick = onSettle) { Text(if (income) "Marcar cobrado" else "Marcar pagado") }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar $title")
                }
            }
        }
    }
}
