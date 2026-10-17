package com.quipu.app.ui.tasks

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.quipu.app.domain.TaskDraft
import com.quipu.app.domain.TaskFilter
import com.quipu.app.domain.TaskItem
import com.quipu.app.domain.TaskLogic
import com.quipu.app.ui.BaseViewModel
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class TasksUiState(
    val loaded: Boolean = false,
    /** Todas las tareas guardadas (sin filtrar), con las diarias resueltas para hoy. */
    val all: List<TaskItem> = emptyList(),
    /** Tareas visibles con el filtro y el orden actuales. */
    val visible: List<TaskItem> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val ascending: Boolean = true,
)

class TasksViewModel(application: Application) : BaseViewModel(application) {

    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val ascending = MutableStateFlow(true)

    /**
     * Fecha actual. Se emite al empezar a observar (al abrir o volver a la app) y se
     * revisa cada 30 s, para que las diarias se reinicien al cambiar de día.
     */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<TasksUiState> = combine(
        repository.observeTasks(),
        today,
        filter,
        ascending,
    ) { stored, day, currentFilter, asc ->
        val all = TaskLogic.resolveForDay(stored, day)
        TasksUiState(
            loaded = true,
            all = all,
            visible = TaskLogic.filterAndSort(all, currentFilter, asc),
            filter = currentFilter,
            ascending = asc,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun toggleSortOrder() {
        ascending.update { !it }
    }

    fun save(draft: TaskDraft) {
        val isNew = draft.id == 0L
        launchAction(if (isNew) "Tarea creada" else "Cambios guardados") { repository.saveTask(draft) }
    }

    fun setCompleted(id: Long, completed: Boolean) {
        launchAction(null) { repository.setTaskCompleted(id, completed, LocalDate.now()) }
    }

    fun delete(id: Long) {
        launchAction("Tarea eliminada") { repository.deleteTask(id) }
    }
}
