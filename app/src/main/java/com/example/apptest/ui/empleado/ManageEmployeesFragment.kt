package com.example.apptest.ui.empleado

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.apptest.empleado.services.EmpleadoRepository
import com.example.apptest.empleado.models.XanoEmpleado
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.apptest.R
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController

class ManageEmployeesFragment: Fragment() {
    private lateinit var adapter: EmpleadosAdapter
    private lateinit var repo: EmpleadoRepository
    private var empleados: List<XanoEmpleado> = emptyList()
    private var filtro: String = ""
    private var cargando: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_employees, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvEmpleados)
        val etBuscar = view.findViewById<EditText>(R.id.etBuscar)
        val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabCrear)
        val progress = view.findViewById<View>(R.id.progressBar)

        val sessionManager = SessionManager.getInstance(requireContext())
        val usuario = sessionManager.getUser()
        val esOwner = usuario?.rol_id == 2
        fab.visibility = if (esOwner) View.VISIBLE else View.GONE

        adapter = EmpleadosAdapter(emptyList(), onDetalle = { e -> e.id?.let { abrirDetalle(it) } })
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        repo = EmpleadoRepository(requireContext().applicationContext)
        etBuscar.addTextChangedListener { editable ->
            filtro = editable?.toString().orEmpty()
            adapter.submit(empleadosFiltrados())
        }
        if (esOwner) fab.setOnClickListener { mostrarDialogoCrearEmpleado() }

        lifecycleScope.launch {
            cargarEmpleados()
            progress.visibility = if (cargando) View.VISIBLE else View.GONE
            adapter.submit(empleadosFiltrados())
        }
    }

    private fun abrirDetalle(id: Int) {
        val bundle = android.os.Bundle().apply { putInt("empleado_id", id) }
        findNavController().navigate(R.id.empleadoDetailFragment, bundle)
    }

    private fun mostrarDialogoCrearEmpleado() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crear_empleado, null)
        val etPrimer = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPrimerNombre)
        val etSegundo = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSegundoNombre)
        val etApPat = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApellidoPaterno)
        val etApMat = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApellidoMaterno)
        val etUsername = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUsername)
        val etRut = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRut)
        val etDv = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDv)
        val etEmail = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        val etSueldo = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSueldo)
        val etCalle = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCalle)
        val etNumero = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNumero)
        val etPassword = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)
        val etRolId = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRolId)
        val spComuna = v.findViewById<android.widget.Spinner>(R.id.spRegComuna)
        val swHabilitado = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swHabilitado)

        val servicioRegComuna = com.example.apptest.core.network.ApiClient.getRetrofit(requireContext()).create(com.example.apptest.pais.services.XanoRegComunaService::class.java)
        var listaComunas: List<Pair<Int, String>> = emptyList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val regiones = servicioRegComuna.obtenerRegionesConComunas()
                listaComunas = regiones.flatMap { it.comunas.orEmpty() }.map { it.id to (it.nombreComuna ?: it.id.toString()) }
                val nombres = listaComunas.map { it.second }
                val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, nombres).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                spComuna.adapter = adapter
            } catch (_: Exception) { }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Crear Empleado")
            .setView(v)
            .setPositiveButton("Guardar") { dialog, _ ->
                val datos = mutableMapOf<String, Any?>()
                datos["primer_nombre"] = etPrimer.text?.toString()?.takeIf { it.isNotBlank() }
                datos["segundo_nombre"] = etSegundo.text?.toString()?.takeIf { it.isNotBlank() }
                datos["apellido_paterno"] = etApPat.text?.toString()?.takeIf { it.isNotBlank() }
                datos["apellido_materno"] = etApMat.text?.toString()?.takeIf { it.isNotBlank() }
                datos["username"] = etUsername.text?.toString()?.takeIf { it.isNotBlank() }
                datos["rut"] = etRut.text?.toString()?.toIntOrNull()
                datos["dv"] = etDv.text?.toString()?.takeIf { it.isNotBlank() }
                datos["email_contacto"] = etEmail.text?.toString()?.takeIf { it.isNotBlank() }
                datos["sueldo"] = etSueldo.text?.toString()?.toDoubleOrNull()
                datos["nombre_calle"] = etCalle.text?.toString()?.takeIf { it.isNotBlank() }
                datos["numero_calle"] = etNumero.text?.toString()?.toIntOrNull()
                datos["password"] = etPassword.text?.toString()?.takeIf { it.isNotBlank() }
                datos["rol_id"] = etRolId.text?.toString()?.toIntOrNull()
                val idx = spComuna.selectedItemPosition
                datos["comuna_id"] = if (idx in listaComunas.indices) listaComunas[idx].first else null
                datos["habilitado"] = swHabilitado.isChecked
                val errores = mutableListOf<Pair<com.google.android.material.textfield.TextInputEditText, String>>()
                fun required(e: com.google.android.material.textfield.TextInputEditText, name: String): Boolean {
                    val ok = e.text?.toString()?.isNotBlank() == true
                    if (!ok) errores.add(e to "$name requerido")
                    return ok
                }
                fun emailOk(): Boolean {
                    val t = etEmail.text?.toString()?.trim().orEmpty()
                    val ok = t.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(t).matches()
                    if (!ok) errores.add(etEmail to "Correo inválido")
                    return ok
                }
                fun passOk(): Boolean {
                    val t = etPassword.text?.toString().orEmpty()
                    val ok = t.length >= 8
                    if (!ok) errores.add(etPassword to "Mínimo 8 caracteres")
                    return ok
                }
                fun rutOk(): Boolean {
                    val r = etRut.text?.toString()?.toIntOrNull()
                    val dvv = etDv.text?.toString()?.trim().orEmpty()
                    val ok = (r != null && r > 0 && dvv.isNotBlank())
                    if (!ok) errores.add(etRut to "RUT/DV inválidos")
                    return ok
                }
                val valido = required(etUsername, "Usuario") && emailOk() && passOk() && rutOk() && (datos["comuna_id"] != null)
                errores.forEach { (field, msg) -> field.error = msg }
                if (!valido) { Toast.makeText(requireContext(), "Completa los campos requeridos", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val ok = repo.registrarEmpleado(datos).onSuccess { e -> empleados = empleados + e }.isSuccess
                        if (!ok) Toast.makeText(requireContext(), "Error creando empleado", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error creando", android.widget.Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    val gold = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.apptest.R.color.boton_dorado)
                    val black = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black)
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.let { b ->
                        b.setTextColor(black)
                        (b as? com.google.android.material.button.MaterialButton)?.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
                    }
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.let { b ->
                        b.setTextColor(black)
                        (b as? com.google.android.material.button.MaterialButton)?.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
                    }
                }
            }.show()
    }

    private suspend fun cargarEmpleados() {
        cargando = true
        repo.listar().onSuccess { lista -> empleados = lista }.onFailure { }
        cargando = false
    }

    private fun empleadosFiltrados(): List<XanoEmpleado> {
        if (filtro.isBlank()) return empleados
        val f = filtro.lowercase()
        return empleados.filter {
            listOfNotNull(
                it.primer_nombre, it.segundo_nombre, it.apellido_paterno, it.apellido_materno, it.email_contacto, it.username
            ).any { v -> v?.lowercase()?.contains(f) == true }
        }
    }
}
