package com.example.apptest.ui.empleado

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apptest.cliente.services.ClienteRepository
import com.example.apptest.core.network.ApiClient
import com.example.apptest.pais.services.XanoRegComunaService
import com.example.apptest.pais.models.XanoRegionComuna
import com.example.apptest.cliente.models.XanoCliente
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageUsersState(
    val clientes: List<XanoCliente> = emptyList(),
    val filtro: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val regiones: List<Pair<Int, String>> = emptyList(),
    // Lista simple de comunas (legacy) para compatibilidad
    val comunas: List<Pair<Int, String>> = emptyList(),
    // Lista detallada (id comuna, id región, nombre comuna) para filtrado dinámico
    val comunasDetalladas: List<Triple<Int, Int?, String>> = emptyList()
)

class ManageUsersViewModel(private val context: Context): ViewModel() {
    private val repo = ClienteRepository(context)
    private val regComunaSrv by lazy { ApiClient.getRetrofit(context).create(XanoRegComunaService::class.java) }

    private val _state = MutableStateFlow(ManageUsersState())
    val state: StateFlow<ManageUsersState> = _state

    init { cargarClientes() }

    fun setFiltro(q: String) { _state.update { it.copy(filtro = q) } }

    fun clientesFiltrados(): List<XanoCliente> {
        val s = _state.value
        if (s.filtro.isBlank()) return s.clientes
        val f = s.filtro.lowercase()
        return s.clientes.filter {
            listOfNotNull(
                it.primer_nombre, it.segundo_nombre, it.apellido_paterno, it.apellido_materno, it.email_contacto
            ).any { v -> v?.lowercase()?.contains(f) == true }
        }
    }

    fun cargarClientes() {
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, error = null) }
            kotlinx.coroutines.delay(4000)
            repo.listar().onSuccess { lista ->
                _state.update { it.copy(clientes = lista, cargando = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, cargando = false) }
            }
        }
    }

    fun crearCliente(datos: Map<String, Any?>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repo.crear(datos).onSuccess { c ->
                _state.update { it.copy(clientes = it.clientes + c) }
                onDone(true)
            }.onFailure { onDone(false) }
        }
    }

    fun actualizarCliente(id: Int, datos: Map<String, Any?>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repo.actualizar(id, datos).onSuccess { c ->
                _state.update { it.copy(clientes = it.clientes.map { if (it.id == id) c else it }) }
                onDone(true)
            }.onFailure { onDone(false) }
        }
    }

    fun toggleHabilitado(id: Int) {
        val cliente = _state.value.clientes.firstOrNull { it.id == id } ?: return
        val nuevo = cliente.habilitado != true
        viewModelScope.launch {
            repo.actualizarHabilitado(id, nuevo).onSuccess { c ->
                _state.update { it.copy(clientes = it.clientes.map { if (it.id == id) c else it }) }
            }
        }
    }

    fun eliminar(id: Int) {
        viewModelScope.launch {
            repo.eliminar(id).onSuccess {
                _state.update { it.copy(clientes = it.clientes.filterNot { it.id == id }) }
            }
        }
    }

    fun cargarRegionesYComunas() {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(4000)
                val regionesCompletas = regComunaSrv.obtenerRegionesConComunas()
                val regiones = regionesCompletas.mapNotNull { it.regionPair() }
                val comunasDetalladas = regionesCompletas.flatMap { reg ->
                    reg.comunas.orEmpty().mapNotNull { c ->
                        val nombre = c.nombreComuna ?: c.id.toString()
                        Triple(c.id, c.regionId, nombre)
                    }
                }
                val comunasLegacy = comunasDetalladas.map { it.first to it.third }
                _state.update { it.copy(regiones = regiones, comunas = comunasLegacy, comunasDetalladas = comunasDetalladas) }
            } catch (e: Exception) { /* ignorar errores silenciosamente */ }
        }
    }
}

private fun XanoRegionComuna.regionPair(): Pair<Int, String>? = id.let { rid -> rid to (nombreRegion ?: rid.toString()) }
