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

data class ManageEmployeesState(
    val empleados: List<XanoEmpleado> = emptyList(),
    val filtro: String = "",
    val cargando: Boolean = false,
    val error: String? = null
)

class ManageEmployeesViewModel(private val context: Context): ViewModel() {
    private val repo = EmpleadoRepository(context)

    private val _state = MutableStateFlow(ManageEmployeesState())
    val state: StateFlow<ManageEmployeesState> = _state

    init { cargarEmpleados() }

    fun setFiltro(q: String) { _state.update { it.copy(filtro = q) } }

    fun empleadosFiltrados(): List<XanoEmpleado> {
        val s = _state.value
        if (s.filtro.isBlank()) return s.empleados
        val f = s.filtro.lowercase()
        return s.empleados.filter {
            listOfNotNull(
                it.primer_nombre, it.segundo_nombre, it.apellido_paterno, it.apellido_materno, it.email_contacto, it.username
            ).any { v -> v?.lowercase()?.contains(f) == true }
        }
    }

    fun cargarEmpleados() {
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, error = null) }
            repo.listar().onSuccess { lista ->
                _state.update { it.copy(empleados = lista, cargando = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, cargando = false) }
            }
        }
    }

    suspend fun crearEmpleadoViaRegister(datos: Map<String, Any?>): Boolean {
        return repo.registrarEmpleado(datos).onSuccess { e ->
            _state.update { it.copy(empleados = it.empleados + e) }
        }.isSuccess
    }
}

