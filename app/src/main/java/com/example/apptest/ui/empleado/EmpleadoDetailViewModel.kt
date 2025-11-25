package com.example.apptest.ui.empleado

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apptest.empleado.services.EmpleadoRepository
import com.example.apptest.empleado.models.XanoEmpleado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmpleadoDetailState(
    val empleado: XanoEmpleado? = null,
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null
)

class EmpleadoDetailViewModel(private val context: Context, private val empleadoId: Int): ViewModel() {
    private val repo = EmpleadoRepository(context)
    private val _state = MutableStateFlow(EmpleadoDetailState(cargando = true))
    val state: StateFlow<EmpleadoDetailState> = _state

    init { cargar() }

    private fun cargar() {
        viewModelScope.launch {
            repo.obtener(empleadoId).onSuccess { e -> _state.update { it.copy(empleado = e, cargando = false) } }
                .onFailure { er -> _state.update { it.copy(error = er.message, cargando = false) } }
        }
    }

    fun guardarCambios(datos: Map<String, Any?>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(guardando = true) }
            repo.actualizar(empleadoId, datos).onSuccess { e ->
                _state.update { it.copy(empleado = e, guardando = false) }
                onDone(true)
            }.onFailure {
                _state.update { it.copy(guardando = false) }
                onDone(false)
            }
        }
    }

    fun toggleHabilitado() {
        val e = _state.value.empleado ?: return
        val nuevo = e.habilitado != true
        guardarCambios(mapOf("habilitado" to nuevo)) { }
    }

    fun eliminar(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repo.eliminar(empleadoId).onSuccess {
                onDone(true)
            }.onFailure { onDone(false) }
        }
    }
}

