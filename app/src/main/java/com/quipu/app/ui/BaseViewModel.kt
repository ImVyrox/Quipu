package com.quipu.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quipu.app.QuipuApplication
import com.quipu.app.data.QuipuRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Base con acceso al repositorio y un canal de mensajes (éxito/error) para mostrar en pantalla. */
abstract class BaseViewModel(application: Application) : AndroidViewModel(application) {
    protected val repository: QuipuRepository =
        getApplication<QuipuApplication>().repository

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    protected fun say(message: String) {
        _messages.tryEmit(message)
    }

    /** Ejecuta [block]; muestra [success] si termina bien o un error genérico si falla. */
    protected fun launchAction(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (success != null) say(success)
            } catch (e: Exception) {
                say("No se pudo completar la acción. Inténtalo de nuevo.")
            }
        }
    }
}
