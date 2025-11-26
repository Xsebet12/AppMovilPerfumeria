package com.example.apptest.ui.empleado

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.apptest.empleado.services.EmpleadoRepository
import androidx.lifecycle.lifecycleScope
import com.example.apptest.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmpleadoDetailFragment: Fragment() {
    private val empleadoId: Int by lazy { arguments?.getInt("empleado_id") ?: -1 }
    private lateinit var repo: EmpleadoRepository
    private var empleado: com.example.apptest.empleado.models.XanoEmpleado? = null
    private var cargando: Boolean = false
    private var guardando: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_empleado_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvNombre = view.findViewById<TextView>(R.id.tvNombreDetalle)
        val tvEmail = view.findViewById<TextView>(R.id.tvEmailDetalle)
        val tvEstado = view.findViewById<TextView>(R.id.tvEstadoDetalle)
        val btnEditar = view.findViewById<View>(R.id.btnEditarDetalle)
        val btnToggle = view.findViewById<View>(R.id.btnToggleDetalle)
        val btnEliminar = view.findViewById<View>(R.id.btnEliminarDetalle)
        val progress = view.findViewById<View>(R.id.progressDetalle)

        repo = EmpleadoRepository(requireContext().applicationContext)
        lifecycleScope.launch {
            cargar()
            progress.visibility = if (cargando || guardando) View.VISIBLE else View.GONE
            val e = empleado
            if (e != null) {
                val nombre = listOfNotNull(e.primer_nombre, e.segundo_nombre, e.apellido_paterno, e.apellido_materno).joinToString(" ").trim()
                tvNombre.text = if (nombre.isNotBlank()) nombre else "(Sin nombre)"
                tvEmail.text = e.email_contacto ?: "-"
                tvEstado.text = if (e.habilitado == true) "Habilitado" else "Bloqueado"
            }
        }

        btnEditar.setOnClickListener { mostrarDialogoEdicion() }
        btnToggle.setOnClickListener { toggleHabilitado() }
        btnEliminar.setOnClickListener { confirmarEliminar() }
    }

    private fun mostrarDialogoEdicion() {
        val e = empleado ?: return
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_empleado, null)
        val etPrimer = v.findViewById<TextInputEditText>(R.id.etPrimerNombre)
        val etSegundo = v.findViewById<TextInputEditText>(R.id.etSegundoNombre)
        val etApPat = v.findViewById<TextInputEditText>(R.id.etApellidoPaterno)
        val etApMat = v.findViewById<TextInputEditText>(R.id.etApellidoMaterno)
        val etUsername = v.findViewById<TextInputEditText>(R.id.etUsername)
        val etRut = v.findViewById<TextInputEditText>(R.id.etRut)
        val etDv = v.findViewById<TextInputEditText>(R.id.etDv)
        val etEmail = v.findViewById<TextInputEditText>(R.id.etEmail)
        val etSueldo = v.findViewById<TextInputEditText>(R.id.etSueldo)
        val etCalle = v.findViewById<TextInputEditText>(R.id.etCalle)
        val etNumero = v.findViewById<TextInputEditText>(R.id.etNumero)
        val etRolId = v.findViewById<TextInputEditText>(R.id.etRolId)
        val spComuna = v.findViewById<android.widget.Spinner>(R.id.spRegComuna)
        val swHabilitado = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swHabilitado)

        etPrimer.setText(e.primer_nombre)
        etSegundo.setText(e.segundo_nombre)
        etApPat.setText(e.apellido_paterno)
        etApMat.setText(e.apellido_materno)
        etUsername.setText(e.username)
        etRut.setText(e.rut?.toString())
        etDv.setText(e.dv)
        etEmail.setText(e.email_contacto)
        etSueldo.setText(e.sueldo?.toString())
        etCalle.setText(e.nombre_calle)
        etNumero.setText(e.numero_calle?.toString())
        etRolId.setText(e.rol_id?.toString())
        val servicioRegComuna = com.example.apptest.core.network.ApiClient.getRetrofit(requireContext()).create(com.example.apptest.pais.services.XanoRegComunaService::class.java)
        var listaComunas: List<Pair<Int, String>> = emptyList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val regiones = servicioRegComuna.obtenerRegionesConComunas()
                listaComunas = regiones.flatMap { it.comunas.orEmpty() }.map { it.id to (it.nombreComuna ?: it.id.toString()) }
                val nombres = listaComunas.map { it.second }
                val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, nombres).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                spComuna.adapter = adapter
                val preIdx = listaComunas.indexOfFirst { it.first == e.comuna_id }
                if (preIdx >= 0) spComuna.setSelection(preIdx)
            } catch (_: Exception) { }
        }
        swHabilitado.isChecked = e.habilitado == true

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Editar Empleado")
            .setView(v)
            .setPositiveButton("Guardar") { _, _ ->
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
                datos["rol_id"] = etRolId.text?.toString()?.toIntOrNull()
                val idx = spComuna.selectedItemPosition
                datos["comuna_id"] = if (idx in listaComunas.indices) listaComunas[idx].first else null
                datos["habilitado"] = swHabilitado.isChecked
                viewLifecycleOwner.lifecycleScope.launch { guardarCambios(datos) { ok -> if (!ok) Toast.makeText(requireContext(), "Error guardando", Toast.LENGTH_SHORT).show() } }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            val color = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black)
            val gold = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.apptest.R.color.boton_dorado)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { b ->
                b.setTextColor(color)
                (b as? com.google.android.material.button.MaterialButton)?.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { b ->
                b.setTextColor(color)
                (b as? com.google.android.material.button.MaterialButton)?.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            }
        }
        dialog.show()
    }

    private fun confirmarEliminar() {
        val d = AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Empleado")
            .setMessage("¿Seguro que deseas eliminar?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    eliminar { ok ->
                        if (ok) {
                            Toast.makeText(requireContext(), "Empleado eliminado", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.setFragmentResult("empleado_eliminado", android.os.Bundle())
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        } else Toast.makeText(requireContext(), "Error eliminando", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        d.setOnShowListener {
            val black = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black)
            val gold = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.apptest.R.color.boton_dorado)
            d.getButton(AlertDialog.BUTTON_POSITIVE)?.let { b ->
                b.setTextColor(black)
                (b as? com.google.android.material.button.MaterialButton)?.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            }
            d.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { b ->
                b.setTextColor(black)
                (b as? com.google.android.material.button.MaterialButton)?.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            }
        }
        d.show()
    }

    private suspend fun cargar() {
        cargando = true
        repo.obtener(empleadoId).onSuccess { e -> empleado = e }.onFailure { }
        cargando = false
    }

    private fun toggleHabilitado() {
        val id = empleado?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repo.actualizar(id, mapOf("habilitado" to (empleado?.habilitado != true))).onSuccess { e -> empleado = e }.onFailure { }
        }
    }

    private suspend fun guardarCambios(datos: Map<String, Any?>, onFin: (Boolean)->Unit) {
        val id = empleado?.id ?: return
        guardando = true
        repo.actualizar(id, datos).onSuccess { e -> empleado = e; guardando = false; onFin(true) }.onFailure { guardando = false; onFin(false) }
    }

    private suspend fun eliminar(onFin: (Boolean)->Unit) {
        val id = empleado?.id ?: return
        repo.eliminar(id).onSuccess { onFin(true) }.onFailure { onFin(false) }
    }
}
