package com.example.apptest.ui.empleado

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.apptest.cliente.services.ClienteRepository
import com.example.apptest.pais.services.XanoRegComunaService
import com.example.apptest.cliente.models.XanoCliente
import com.example.apptest.core.network.ApiClient
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.apptest.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import com.example.apptest.core.storage.SessionManager

class ManageUsersFragment: Fragment() {
    private lateinit var adapter: ClientesAdapter
    private lateinit var repo: ClienteRepository
    private lateinit var regSrv: XanoRegComunaService
    private var clientes: List<XanoCliente> = emptyList()
    private var filtro: String = ""
    private var cargando: Boolean = false
    private var regiones: List<Pair<Int, String>> = emptyList()
    private var comunasDetalladas: List<Triple<Int, Int?, String>> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_users, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvClientes)
        val etBuscar = view.findViewById<EditText>(R.id.etBuscar)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabCrear)
        val progress = view.findViewById<View>(R.id.progressBar)

        // Verificar si el usuario es owner (rol_id=2) para mostrar funcionalidades de gestión
        val sessionManager = SessionManager.getInstance(requireContext())
        val usuario = sessionManager.getUser()
        val esOwner = usuario?.rol_id == 2
        
        // Ocultar FAB de creación si no es owner
        fab.visibility = if (esOwner) View.VISIBLE else View.GONE

        adapter = ClientesAdapter(
            emptyList(),
            onDetalle = { c -> c.id?.let { id -> abrirDetalle(id) } }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        repo = ClienteRepository(requireContext().applicationContext)
        regSrv = ApiClient.getRetrofit(requireContext()).create(XanoRegComunaService::class.java)

        etBuscar.addTextChangedListener { editable ->
            filtro = editable?.toString().orEmpty()
            adapter.submit(clientesFiltrados())
        }
        
        // Solo permitir crear si es owner
        if (esOwner) {
            fab.setOnClickListener { mostrarDialogoCrear() }
        }

        lifecycleScope.launch {
            cargarRegionesYComunas()
            cargarClientes()
            progress.visibility = if (cargando) View.VISIBLE else View.GONE
            adapter.submit(clientesFiltrados())
        }

        parentFragmentManager.setFragmentResultListener("cliente_eliminado", viewLifecycleOwner) { _, _ ->
            lifecycleScope.launch { cargarClientes() }
        }
    }

    private fun abrirDetalle(id: Int) {
        val bundle = android.os.Bundle().apply { putInt("cliente_id", id) }
        findNavController().navigate(R.id.userDetailFragment, bundle)
    }

    private fun mostrarDialogoCrear() {
        lifecycleScope.launch { cargarRegionesYComunas() }
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crear_cliente, null)
        val etPrimer = v.findViewById<TextInputEditText>(R.id.etPrimerNombre)
        val etSegundo = v.findViewById<TextInputEditText>(R.id.etSegundoNombre)
        val etApPat = v.findViewById<TextInputEditText>(R.id.etApellidoPaterno)
        val etApMat = v.findViewById<TextInputEditText>(R.id.etApellidoMaterno)
        val etEmail = v.findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = v.findViewById<TextInputEditText>(R.id.etPassword)
        val etTelefono = v.findViewById<TextInputEditText>(R.id.etTelefono)
        val etCalle = v.findViewById<TextInputEditText>(R.id.etCalle)
        val etNumero = v.findViewById<TextInputEditText>(R.id.etNumero)
        val spRegion = v.findViewById<Spinner>(R.id.spRegion)
        val spComuna = v.findViewById<Spinner>(R.id.spComuna)
        val spTipoCliente = v.findViewById<Spinner>(R.id.spTipoCliente)
        val swHabilitado = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swHabilitado)

        // Tipos permitidos
        val tiposCliente = listOf("Detalle", "Vip", "Mayorista")
        spTipoCliente.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tiposCliente)

        var comunasFiltradasPairs: List<Pair<Int, String>> = emptyList()

        lifecycleScope.launch {
            val regionesNombres = regiones.map { it.second }
            spRegion.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, regionesNombres)
            val regionIdSel = spRegion.selectedItemPosition.takeIf { it in regiones.indices }?.let { regiones[it].first }
            comunasFiltradasPairs = comunasDetalladas.filter { it.second != null && it.second == regionIdSel }.map { it.first to it.third }
            val comunasNombres = comunasFiltradasPairs.map { it.second }
            spComuna.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, comunasNombres)
        }

        spRegion.setOnItemSelectedListener(object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val regionIdSel = regiones.getOrNull(position)?.first
                comunasFiltradasPairs = comunasDetalladas.filter { it.second != null && it.second == regionIdSel }.map { it.first to it.third }
                val nombres = comunasFiltradasPairs.map { it.second }
                spComuna.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nombres)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Crear Cliente")
            .setView(v)
            .setPositiveButton("Guardar") { _, _ ->
                val datos = mutableMapOf<String, Any?>()
                datos["primer_nombre"] = etPrimer.text?.toString()?.takeIf { it.isNotBlank() }
                datos["segundo_nombre"] = etSegundo.text?.toString()?.takeIf { it.isNotBlank() }
                datos["apellido_paterno"] = etApPat.text?.toString()?.takeIf { it.isNotBlank() }
                datos["apellido_materno"] = etApMat.text?.toString()?.takeIf { it.isNotBlank() }
                datos["email_contacto"] = etEmail.text?.toString()?.takeIf { it.isNotBlank() }
                datos["password"] = etPassword.text?.toString()?.takeIf { it.isNotBlank() }
                datos["telefono_contacto"] = etTelefono.text?.toString()?.takeIf { it.isNotBlank() }
                datos["nombre_calle"] = etCalle.text?.toString()?.takeIf { it.isNotBlank() }
                datos["numero_calle"] = etNumero.text?.toString()?.takeIf { it.isNotBlank() }
                val regionIdx = spRegion.selectedItemPosition
                if (regionIdx >= 0 && regionIdx < regiones.size) datos["region_id"] = regiones[regionIdx].first
                val comunaIdx = spComuna.selectedItemPosition
                if (comunaIdx >= 0 && comunaIdx < comunasFiltradasPairs.size) datos["comuna_id"] = comunasFiltradasPairs[comunaIdx].first
                val tipoIdx = spTipoCliente.selectedItemPosition
                if (tipoIdx >= 0 && tipoIdx < tiposCliente.size) datos["tipo_cliente"] = tiposCliente[tipoIdx]
                datos["habilitado"] = swHabilitado.isChecked
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(5000)
                    repo.crear(datos).onSuccess { c ->
                        clientes = clientes + c
                        adapter.submit(clientesFiltrados())
                    }.onFailure { Toast.makeText(requireContext(), "Error creando", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            val color = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }
        dialog.show()
    }

    private suspend fun cargarClientes() {
        cargando = true
        repo.listar().onSuccess { lista ->
            clientes = lista
        }.onFailure { }
        cargando = false
    }

    private fun clientesFiltrados(): List<XanoCliente> {
        if (filtro.isBlank()) return clientes
        val f = filtro.lowercase()
        return clientes.filter {
            listOfNotNull(
                it.primer_nombre, it.segundo_nombre, it.apellido_paterno, it.apellido_materno, it.email_contacto
            ).any { v -> v?.lowercase()?.contains(f) == true }
        }
    }

    private suspend fun cargarRegionesYComunas() {
        try {
            kotlinx.coroutines.delay(4000)
            val regionesCompletas = regSrv.obtenerRegionesConComunas()
            regiones = regionesCompletas.mapNotNull { it.regionPair() }
            comunasDetalladas = regionesCompletas.flatMap { reg ->
                reg.comunas.orEmpty().mapNotNull { c -> Triple(c.id, c.regionId, c.nombreComuna ?: c.id.toString()) }
            }
        } catch (_: Exception) { }
    }
}

private fun com.example.apptest.pais.models.XanoRegionComuna.regionPair(): Pair<Int, String>? = id.let { rid -> rid to (nombreRegion ?: rid.toString()) }
