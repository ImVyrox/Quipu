@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.finance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.DebtDraft
import com.quipu.app.domain.DebtFormErrors
import com.quipu.app.domain.DebtItem
import com.quipu.app.domain.DebtLogic
import com.quipu.app.domain.DebtStatus
import com.quipu.app.domain.DebtStatusFilter
import com.quipu.app.domain.DebtType
import com.quipu.app.domain.DebtTypeFilter
import com.quipu.app.domain.FinanceCalculator
import com.quipu.app.domain.Limits
import com.quipu.app.domain.Money
import com.quipu.app.domain.PersonBalance
import com.quipu.app.domain.PersonGroup
import com.quipu.app.domain.PersonKey
import com.quipu.app.domain.PersonRecords
import com.quipu.app.domain.Validators
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.ConfirmDeleteDialog
import com.quipu.app.ui.components.DateField
import com.quipu.app.ui.components.EmptyState
import com.quipu.app.ui.components.FormActions
import com.quipu.app.ui.components.FormSheet
import com.quipu.app.ui.components.SectionHeader
import com.quipu.app.ui.components.StatusPill
import com.quipu.app.ui.theme.QuipuColors
import java.time.LocalDate

@Composable
fun FinanceScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: FinanceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Persona abierta (clave normalizada); null = lista de personas. */
    var personKey by rememberSaveable { mutableStateOf<String?>(null) }

    val personItems = personKey?.let { key -> state.all.filter { it.personKey == key } }.orEmpty()
    val person: PersonBalance? = personKey?.takeIf { personItems.isNotEmpty() }?.let {
        FinanceCalculator.balanceOf(it, personItems)
    }

    // Si se borran o renombran todos sus registros, la persona deja de existir: volver a la lista.
    LaunchedEffect(state.loaded, personKey, personItems.isEmpty()) {
        if (state.loaded && personKey != null && personItems.isEmpty()) personKey = null
    }
    BackHandler(enabled = personKey != null) { personKey = null }

    val openEdit: (DebtItem) -> Unit = { item ->
        editingId = item.id
        formOpen = true
    }

    Scaffold(
        topBar = {
            if (person != null) {
                TopAppBar(
                    title = { Text(person.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { personKey = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a personas")
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text("Préstamos y deudas") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingId = null
                formOpen = true
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (person != null) "Nuevo registro de ${person.displayName}" else "Nuevo registro",
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (person != null) {
                PersonDetail(
                    person = person,
                    records = DebtLogic.recordsOf(state.all, person.personKey, state.filters.newestFirst),
                    currency = state.currency,
                    today = today,
                    onToggleSettled = { item -> viewModel.setSettled(item, item.status == DebtStatus.PENDING) },
                    onEdit = openEdit,
                    onDelete = { deletingId = it.id },
                )
                return@Column
            }

            if (state.loaded && state.all.isNotEmpty()) {
                DebtFilterBar(
                    filters = state.filters,
                    countText = if (state.groups.size == 1) "1 persona" else "${state.groups.size} personas",
                    onQuery = viewModel::setQuery,
                    onType = viewModel::setTypeFilter,
                    onStatus = viewModel::setStatusFilter,
                    onToggleSort = viewModel::toggleSortOrder,
                )
            }

            when {
                !state.loaded -> Unit
                state.all.isEmpty() -> EmptyState(
                    title = "Sin préstamos ni deudas",
                    message = "Toca el botón + para registrar dinero que te deben o que debes.",
                )
                state.groups.isEmpty() -> EmptyState(
                    title = "Sin resultados",
                    message = "Ningún registro coincide con la búsqueda o los filtros.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.groups, key = { it.personKey }) { group ->
                        PersonGroupCard(
                            group = group,
                            currency = state.currency,
                            onClick = { personKey = group.personKey },
                        )
                    }
                }
            }
        }
    }

    if (formOpen) {
        DebtFormSheet(
            initial = state.all.firstOrNull { it.id == editingId },
            initialPerson = person?.displayName.orEmpty(),
            knownPeople = state.knownPeople,
            currency = state.currency,
            onDismiss = { formOpen = false },
            onSave = { draft ->
                viewModel.save(draft)
                formOpen = false
            },
        )
    }

    deletingId?.let { id ->
        state.all.firstOrNull { it.id == id }?.let { item ->
            ConfirmDeleteDialog(
                title = "¿Eliminar registro?",
                message = "Se eliminará el registro de ${item.personName} por " +
                    "${Money.format(item.amountMinor, state.currency)}. Esta acción no se puede deshacer.",
                onConfirm = {
                    viewModel.delete(id)
                    deletingId = null
                },
                onDismiss = { deletingId = null },
            )
        }
    }
}

private fun signed(minor: Long, currency: AppCurrency): String =
    if (minor > 0) "+" + Money.format(minor, currency) else Money.format(minor, currency)

/** "Te debe X · Le debes Y", o un texto neutro si no hay nada pendiente. */
private fun balanceDetail(balance: PersonBalance, currency: AppCurrency): String =
    buildList {
        if (balance.owedToMe > 0) add("Te debe " + Money.format(balance.owedToMe, currency))
        if (balance.iOwe > 0) add("Le debes " + Money.format(balance.iOwe, currency))
    }.joinToString("  ·  ").ifEmpty { "Sin saldo pendiente" }

@Composable
private fun netColor(net: Long): Color = when {
    net > 0 -> QuipuColors.extra.positive
    net < 0 -> QuipuColors.extra.negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Fila de la lista de personas: nombre, saldo pendiente y número de registros. */
@Composable
private fun PersonGroupCard(
    group: PersonGroup,
    currency: AppCurrency,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val balance = group.balance
    val counts = buildString {
        append(if (group.recordCount == 1) "1 registro" else "${group.recordCount} registros")
        if (balance.pendingCount > 0) {
            append("  ·  ")
            append(if (balance.pendingCount == 1) "1 pendiente" else "${balance.pendingCount} pendientes")
        }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = balanceDetail(balance, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (group.hasOverdue) {
                        StatusPill(text = "Vencido", container = scheme.errorContainer, content = scheme.onErrorContainer)
                    }
                    Text(
                        text = counts,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = signed(balance.net, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = netColor(balance.net),
                )
                Text(
                    text = "neto",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Vista de una persona: su saldo y sus registros separados por tipo y estado. */
@Composable
private fun PersonDetail(
    person: PersonBalance,
    records: PersonRecords,
    currency: AppCurrency,
    today: LocalDate,
    onToggleSettled: (DebtItem) -> Unit,
    onEdit: (DebtItem) -> Unit,
    onDelete: (DebtItem) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "balance") {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Saldo pendiente", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Text(
                        text = signed(person.net, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = netColor(person.net),
                    )
                    Text(
                        text = balanceDetail(person, currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        fun section(key: String, title: String, list: List<DebtItem>) {
            if (list.isEmpty()) return
            item(key = "header-$key") { SectionHeader("$title (${list.size})") }
            items(list, key = { it.id }) { item ->
                DebtCard(
                    item = item,
                    currency = currency,
                    today = today,
                    onToggleSettled = { onToggleSettled(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                )
            }
        }

        section("owed-pending", "Me debe · pendientes", records.owedToMePending)
        section("owed-settled", "Me debe · cobrados", records.owedToMeSettled)
        section("iowe-pending", "Yo le debo · pendientes", records.iOwePending)
        section("iowe-settled", "Yo le debo · pagados", records.iOweSettled)
    }
}

@Composable
private fun DebtFilterBar(
    filters: DebtFilters,
    countText: String,
    onQuery: (String) -> Unit,
    onType: (DebtTypeFilter) -> Unit,
    onStatus: (DebtStatusFilter) -> Unit,
    onToggleSort: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = onQuery,
            placeholder = { Text("Buscar por nombre") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (filters.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda")
                    }
                }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.type == DebtTypeFilter.ALL,
                onClick = { onType(DebtTypeFilter.ALL) },
                label = { Text("Todos") },
            )
            FilterChip(
                selected = filters.type == DebtTypeFilter.OWED_TO_ME,
                onClick = { onType(DebtTypeFilter.OWED_TO_ME) },
                label = { Text("Me deben") },
            )
            FilterChip(
                selected = filters.type == DebtTypeFilter.I_OWE,
                onClick = { onType(DebtTypeFilter.I_OWE) },
                label = { Text("Debo") },
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.status == DebtStatusFilter.ALL,
                onClick = { onStatus(DebtStatusFilter.ALL) },
                label = { Text("Cualquier estado") },
            )
            FilterChip(
                selected = filters.status == DebtStatusFilter.PENDING,
                onClick = { onStatus(DebtStatusFilter.PENDING) },
                label = { Text("Pendientes") },
            )
            FilterChip(
                selected = filters.status == DebtStatusFilter.SETTLED,
                onClick = { onStatus(DebtStatusFilter.SETTLED) },
                label = { Text("Pagados/cobrados") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onToggleSort) {
                Text(if (filters.newestFirst) "Fecha: recientes primero ↓" else "Fecha: antiguos primero ↑")
            }
        }
    }
}

@Composable
private fun DebtCard(
    item: DebtItem,
    currency: AppCurrency,
    today: LocalDate,
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extra = QuipuColors.extra
    val scheme = MaterialTheme.colorScheme
    val owedToMe = item.type == DebtType.OWED_TO_ME
    val settled = item.status == DebtStatus.SETTLED
    val overdue = DebtLogic.isOverdue(item, today)

    val accent: Color = if (owedToMe) extra.positive else extra.negative
    val accentContainer: Color = if (owedToMe) extra.positiveContainer else extra.negativeContainer
    val typeLabel = if (owedToMe) "Me debe" else "Yo debo"
    val sign = if (owedToMe) "+" else "−"
    val settleLabel = if (owedToMe) "Marcar cobrado" else "Marcar pagado"
    val settledLabel = if (owedToMe) "Cobrado" else "Pagado"

    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (settled) scheme.surfaceContainerLow else scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.personName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (settled) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(text = typeLabel, container = accentContainer, content = accent)
                        if (settled) {
                            StatusPill(text = settledLabel, container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
                        } else if (overdue) {
                            StatusPill(text = "Vencido", container = scheme.errorContainer, content = scheme.onErrorContainer)
                        }
                    }
                }
                Text(
                    text = sign + Money.format(item.amountMinor, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (settled) scheme.onSurfaceVariant else accent,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
            }
            if (item.note.isNotBlank()) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val dates = buildString {
                append("Registro: ").append(Formats.date(item.recordDate))
                if (item.dueDate != null) append("  ·  Vence: ").append(Formats.date(item.dueDate))
                if (settled && item.settledDate != null) {
                    append("  ·  ").append(settledLabel).append(": ").append(Formats.date(item.settledDate))
                }
            }
            Text(
                text = dates,
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) scheme.error else scheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToggleSettled) {
                    Text(if (settled) "Volver a pendiente" else settleLabel)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar registro de ${item.personName}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar registro de ${item.personName}")
                }
            }
        }
    }
}

@Composable
private fun DebtFormSheet(
    initial: DebtItem?,
    /** Persona sugerida para un registro nuevo (al crear desde la vista de una persona). */
    initialPerson: String,
    knownPeople: List<String>,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (DebtDraft) -> Unit,
) {
    // Fechas como días-época (Long) para poder conservarlas al rotar la pantalla.
    var typeName by rememberSaveable { mutableStateOf((initial?.type ?: DebtType.OWED_TO_ME).name) }
    var person by rememberSaveable { mutableStateOf(initial?.personName ?: initialPerson) }
    var amountText by rememberSaveable {
        mutableStateOf(initial?.let { Money.toInputText(it.amountMinor) } ?: "")
    }
    var recordDay by rememberSaveable {
        mutableStateOf<Long?>((initial?.recordDate ?: LocalDate.now()).toEpochDay())
    }
    var dueDay by rememberSaveable { mutableStateOf<Long?>(initial?.dueDate?.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val type = DebtType.valueOf(typeName)
    val recordDate = recordDay?.let { LocalDate.ofEpochDay(it) }
    val dueDate = dueDay?.let { LocalDate.ofEpochDay(it) }
    val errors = if (submitted) {
        Validators.validateDebt(person, amountText, recordDate, dueDate, note)
    } else {
        DebtFormErrors()
    }

    val typedKey = PersonKey.keyOf(person)
    val sameAs = if (typedKey.isEmpty()) {
        null
    } else {
        knownPeople.firstOrNull { PersonKey.keyOf(it) == typedKey }
    }
    val suggestions = knownPeople
        .filter { name ->
            val key = PersonKey.keyOf(name)
            key != typedKey && (typedKey.isEmpty() || key.contains(typedKey))
        }
        .take(8)

    FormSheet(
        title = if (initial == null) "Nuevo registro" else "Editar registro",
        onDismiss = onDismiss,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = type == DebtType.OWED_TO_ME,
                onClick = { typeName = DebtType.OWED_TO_ME.name },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Me deben") }
            SegmentedButton(
                selected = type == DebtType.I_OWE,
                onClick = { typeName = DebtType.I_OWE.name },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Yo debo") }
        }

        OutlinedTextField(
            value = person,
            onValueChange = { if (it.length <= Limits.PERSON_MAX) person = it },
            label = { Text("Persona") },
            singleLine = true,
            isError = errors.person != null,
            supportingText = {
                Text(
                    errors.person
                        ?: sameAs?.let { "Se agrupará con «$it»." }
                        ?: "Añade un detalle para distinguir nombres parecidos, p. ej. «Juan (primo)».",
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { name ->
                    SuggestionChip(
                        onClick = { person = name },
                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { if (it.length <= 14) amountText = it },
            label = { Text("Importe") },
            prefix = { Text(currency.symbol + " ") },
            singleLine = true,
            isError = errors.amount != null,
            supportingText = if (errors.amount != null) {
                { Text(errors.amount) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        DateField(
            label = "Fecha del registro",
            value = recordDate,
            onValueChange = { recordDay = it?.toEpochDay() },
            error = errors.recordDate,
        )
        DateField(
            label = "Vencimiento (opcional)",
            value = dueDate,
            onValueChange = { dueDay = it?.toEpochDay() },
            error = errors.dueDate,
            clearable = true,
            placeholder = "Sin vencimiento",
        )

        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= Limits.NOTE_MAX) note = it },
            label = { Text("Nota (opcional)") },
            minLines = 2,
            maxLines = 4,
            isError = errors.note != null,
            supportingText = if (errors.note != null) {
                { Text(errors.note) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                val result = Validators.validateDebt(person, amountText, recordDate, dueDate, note)
                if (!result.hasErrors) {
                    onSave(
                        DebtDraft(
                            id = initial?.id ?: 0L,
                            type = type,
                            personName = PersonKey.displayName(person),
                            amountMinor = Money.parseToMinor(amountText)!!,
                            recordDate = recordDate!!,
                            dueDate = dueDate,
                            note = note.trim(),
                        ),
                    )
                }
            },
        )
    }
}
