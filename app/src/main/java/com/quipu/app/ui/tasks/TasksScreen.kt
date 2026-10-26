@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.tasks

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quipu.app.domain.Limits
import com.quipu.app.domain.TaskDraft
import com.quipu.app.domain.TaskFilter
import com.quipu.app.domain.TaskFormErrors
import com.quipu.app.domain.TaskItem
import com.quipu.app.domain.TaskLogic
import com.quipu.app.domain.TaskTiming
import com.quipu.app.domain.Validators
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.ConfirmDeleteDialog
import com.quipu.app.ui.components.DateField
import com.quipu.app.ui.components.EmptyState
import com.quipu.app.ui.components.FormActions
import com.quipu.app.ui.components.FormSheet
import com.quipu.app.ui.components.StatusPill
import com.quipu.app.ui.components.TimeField
import com.quipu.app.ui.theme.QuipuColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun TasksScreen(
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    viewModel: TasksViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // "Ahora" se refresca cada 30 s para que vencidas y próximas se actualicen solas.
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }

    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tareas") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingId = null
                formOpen = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva tarea")
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.loaded && state.all.isNotEmpty()) {
                TaskFilters(
                    filter = state.filter,
                    ascending = state.ascending,
                    count = state.visible.size,
                    onFilter = viewModel::setFilter,
                    onToggleSort = viewModel::toggleSortOrder,
                )
            }

            when {
                !state.loaded -> Unit
                state.all.isEmpty() -> EmptyState(
                    title = "Aún no tienes tareas",
                    message = "Toca el botón + para crear la primera.",
                )
                state.visible.isEmpty() -> EmptyState(
                    title = "Nada por aquí",
                    message = "No hay tareas que coincidan con este filtro.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visible, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            timing = TaskLogic.timing(task, now),
                            today = now.toLocalDate(),
                            onToggle = { viewModel.setCompleted(task.id, it) },
                            onEdit = {
                                editingId = task.id
                                formOpen = true
                            },
                            onDelete = { deletingId = task.id },
                        )
                    }
                }
            }
        }
    }

    if (formOpen) {
        val editing = state.all.firstOrNull { it.id == editingId }
        TaskFormSheet(
            initial = editing,
            onDismiss = { formOpen = false },
            onSave = { draft ->
                viewModel.save(draft)
                formOpen = false
            },
        )
    }

    deletingId?.let { id ->
        state.all.firstOrNull { it.id == id }?.let { task ->
            ConfirmDeleteDialog(
                title = "¿Eliminar tarea?",
                message = "Se eliminará «${task.title}». Esta acción no se puede deshacer.",
                onConfirm = {
                    viewModel.delete(id)
                    deletingId = null
                },
                onDismiss = { deletingId = null },
            )
        }
    }
}

@Composable
private fun TaskFilters(
    filter: TaskFilter,
    ascending: Boolean,
    count: Int,
    onFilter: (TaskFilter) -> Unit,
    onToggleSort: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == TaskFilter.ALL,
                onClick = { onFilter(TaskFilter.ALL) },
                label = { Text("Todas") },
            )
            FilterChip(
                selected = filter == TaskFilter.PENDING,
                onClick = { onFilter(TaskFilter.PENDING) },
                label = { Text("Pendientes") },
            )
            FilterChip(
                selected = filter == TaskFilter.COMPLETED,
                onClick = { onFilter(TaskFilter.COMPLETED) },
                label = { Text("Completadas") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (count == 1) "1 tarea" else "$count tareas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onToggleSort) {
                Text(if (ascending) "Fecha: más próximas primero ↑" else "Fecha: más lejanas primero ↓")
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    timing: TaskTiming,
    today: LocalDate,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extra = QuipuColors.extra
    val scheme = MaterialTheme.colorScheme

    val pillContainer = when (timing) {
        TaskTiming.OVERDUE -> scheme.errorContainer
        TaskTiming.SOON -> extra.soonContainer
        TaskTiming.DONE -> scheme.surfaceVariant
        TaskTiming.LATER -> scheme.surfaceVariant
    }
    val pillContent = when (timing) {
        TaskTiming.OVERDUE -> scheme.onErrorContainer
        TaskTiming.SOON -> extra.soon
        TaskTiming.DONE -> scheme.onSurfaceVariant
        TaskTiming.LATER -> scheme.onSurfaceVariant
    }
    val pillText: String? = when (timing) {
        TaskTiming.OVERDUE -> "Vencida"
        TaskTiming.SOON -> "Próxima"
        TaskTiming.DONE -> "Completada"
        TaskTiming.LATER -> null
    }
    val dateColor = when (timing) {
        TaskTiming.OVERDUE -> scheme.error
        TaskTiming.SOON -> extra.soon
        else -> scheme.onSurfaceVariant
    }

    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) scheme.surfaceContainerLow else scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    val day = if (task.isDaily) " hoy" else ""
                    contentDescription = if (task.completed) {
                        "Completada$day: ${task.title}. Tocar para marcar como pendiente"
                    } else {
                        "Pendiente$day: ${task.title}. Tocar para marcar como completada"
                    }
                },
            )
            Column(
                modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.completed) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (task.isDaily) {
                        StatusPill(text = "Diaria", container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
                    }
                    if (pillText != null) {
                        StatusPill(text = pillText, container = pillContainer, content = pillContent)
                    }
                    Text(
                        // Las diarias no tienen fecha ni hora: se hacen cualquier momento del día.
                        text = if (task.isDaily) "Hoy" else Formats.dateTime(task.dueAt, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = dateColor,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar ${task.title}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar ${task.title}")
            }
        }
    }
}

@Composable
private fun TaskFormSheet(
    initial: TaskItem?,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
) {
    // Fecha y hora se guardan como números para sobrevivir a rotaciones de pantalla.
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var dayEpoch by rememberSaveable { mutableStateOf<Long?>(initial?.dueAt?.toLocalDate()?.toEpochDay()) }
    var minuteOfDay by rememberSaveable {
        mutableStateOf<Int?>(initial?.dueAt?.toLocalTime()?.let { it.hour * 60 + it.minute })
    }
    var isDaily by rememberSaveable { mutableStateOf(initial?.isDaily ?: false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val date = dayEpoch?.let { LocalDate.ofEpochDay(it) }
    val time = minuteOfDay?.let { LocalTime.of(it / 60, it % 60) }
    val errors = if (submitted) {
        Validators.validateTask(title, description, date, time, isDaily)
    } else {
        TaskFormErrors()
    }

    FormSheet(
        title = if (initial == null) "Nueva tarea" else "Editar tarea",
        onDismiss = onDismiss,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isDaily,
                onClick = { isDaily = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Normal") }
            SegmentedButton(
                selected = isDaily,
                onClick = { isDaily = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Diaria") }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= Limits.TITLE_MAX) title = it },
            label = { Text("Título") },
            singleLine = true,
            isError = errors.title != null,
            supportingText = if (errors.title != null) {
                { Text(errors.title) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= Limits.DESCRIPTION_MAX) description = it },
            label = { Text("Descripción (opcional)") },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (isDaily) {
            Text(
                text = "Se repite cada día y puedes completarla a cualquier hora. Al día siguiente vuelve a quedar pendiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = "Fecha",
                    value = date,
                    onValueChange = { dayEpoch = it?.toEpochDay() },
                    error = errors.date,
                    modifier = Modifier.weight(1.4f),
                )
                TimeField(
                    label = "Hora",
                    value = time,
                    onValueChange = { minuteOfDay = it.hour * 60 + it.minute },
                    error = errors.time,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FormActions(
            onCancel = onDismiss,
            onSave = {
                submitted = true
                if (!Validators.validateTask(title, description, date, time, isDaily).hasErrors) {
                    // Una diaria no usa fecha ni hora; se guarda la elegida (si la hay) para
                    // recuperarla si después vuelve a ser normal.
                    val dueAt = if (date != null && time != null) {
                        LocalDateTime.of(date, time)
                    } else {
                        LocalDateTime.now().withSecond(0).withNano(0)
                    }
                    onSave(
                        TaskDraft(
                            id = initial?.id ?: 0L,
                            title = title.trim(),
                            description = description.trim(),
                            dueAt = dueAt,
                            isDaily = isDaily,
                        ),
                    )
                }
            },
        )
    }
}
