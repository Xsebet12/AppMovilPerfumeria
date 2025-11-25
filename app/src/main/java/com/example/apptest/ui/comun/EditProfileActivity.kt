package com.example.apptest.ui.comun

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.apptest.core.network.XanoAuthRepository
import com.example.apptest.core.network.ApiClient
import com.example.apptest.pais.services.XanoRegComunaService
import com.example.apptest.pais.models.XanoRegionComuna
import com.example.apptest.databinding.ActivityEditProfileBinding
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var session: SessionManager
    private lateinit var repo: XanoAuthRepository
    private val servicioRegComuna by lazy { ApiClient.getRetrofit(this).create(XanoRegComunaService::class.java) }

    private var listaRegiones: List<Pair<Int, String>> = emptyList()
    private var listaComunas: List<Triple<Int, String, Int?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager.getInstance(this)
        repo = XanoAuthRepository(applicationContext)

        if (session.getToken() == null) {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        precargarDatos()
        configurarSpinnersRegionComuna()
        binding.btnGuardarCambios.setOnClickListener { guardarCambios() }
    }

    private fun precargarDatos() {
        session.getUser()?.let { u ->
            binding.etPrimerNombre.setText(u.nombres?.split(' ')?.firstOrNull() ?: "")
            // Heurística simple: segundo nombre = resto (si hubiera)
            val nombresSplit = u.nombres?.split(' ')?.drop(1)?.joinToString(" ")?.ifBlank { null }
            binding.etSegundoNombre.setText(nombresSplit ?: "")
            // Apellidos: no tenemos desglose si se guardó junto, asumimos primer segmento y resto
            val apParts = u.apellidos?.split(' ') ?: emptyList()
            binding.etApellidoPaterno.setText(apParts.getOrNull(0) ?: "")
            binding.etApellidoMaterno.setText(apParts.drop(1).joinToString(" ").ifBlank { "" })
            binding.etEmail.setText(u.correo ?: "")
            binding.etTelefono.setText(u.telefono_contacto ?: "")
            binding.etNombreCalle.setText(u.nombre_calle ?: u.direccion ?: "")
            binding.etNumeroCalle.setText(u.numero_calle?.toString() ?: "")

            val esEmpleado = u.user_type?.equals("empleado", true) == true || u.rol?.equals("empleado", true) == true
            if (esEmpleado) {
                val rutFmt = formatearRut(u.rut, u.dv)
                binding.tvRutInfo.text = "RUT: $rutFmt"
                binding.tvCreadoInfo.text = "Creado: ${u.createdAt ?: "-"}"
                binding.tvRutInfo.visibility = View.VISIBLE
                binding.tvCreadoInfo.visibility = View.VISIBLE
            } else {
                binding.tvRutInfo.visibility = View.GONE
                binding.tvCreadoInfo.visibility = View.GONE
            }
        }
    }

    private fun formatearRut(rutRaw: String?, dv: String?): String {
        val cuerpo = rutRaw?.filter { it.isDigit() } ?: return "-"
        if (cuerpo.isBlank()) return "-"
        val reversed = cuerpo.reversed()
        val grupos = reversed.chunked(3).joinToString(".") { it }
        val conPuntos = grupos.reversed()
        val dvStr = dv?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        return if (dvStr != null) "$conPuntos-$dvStr" else conPuntos
    }

    private fun configurarSpinnersRegionComuna() {
        binding.spComuna.isEnabled = false
        lifecycleScope.launch {
            try {
                val regionesCompletas = servicioRegComuna.obtenerRegionesConComunas()
                listaRegiones = regionesCompletas.map { it.id to (it.nombreRegion ?: it.id.toString()) }
                val adaptadorRegion = ArrayAdapter(this@EditProfileActivity, android.R.layout.simple_spinner_item, listaRegiones.map { it.second }).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                binding.spRegion.adapter = adaptadorRegion

                // Preconstruir comunas agrupadas por región
                val comunasTodas = regionesCompletas.flatMap { region ->
                    region.comunas.orEmpty().map { c -> Triple(c.id, c.nombreComuna ?: c.id.toString(), c.regionId) }
                }

                binding.spRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        val rid = listaRegiones.getOrNull(position)?.first ?: return
                        listaComunas = comunasTodas.filter { it.third != null && it.third == rid }
                        val adaptadorComuna = ArrayAdapter(this@EditProfileActivity, android.R.layout.simple_spinner_item, listaComunas.map { it.second }).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        binding.spComuna.adapter = adaptadorComuna
                        binding.spComuna.isEnabled = listaComunas.isNotEmpty()
                        val comunaActualId = session.getUser()?.comuna_id
                        if (comunaActualId != null) {
                            val idx = listaComunas.indexOfFirst { it.first == comunaActualId }
                            if (idx >= 0) binding.spComuna.setSelection(idx)
                        } else if (listaComunas.isNotEmpty()) {
                            binding.spComuna.setSelection(0)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) { binding.spComuna.isEnabled = false }
                }

                // Preseleccionar región según comuna actual del usuario
                session.getUser()?.comuna_id?.let { comunaId ->
                    val rid = comunasTodas.firstOrNull { it.first == comunaId }?.third
                    if (rid != null) {
                        val idxRegion = listaRegiones.indexOfFirst { it.first == rid }
                        if (idxRegion >= 0) binding.spRegion.setSelection(idxRegion)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Error cargando regiones: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarCambios() {
        // Validaciones básicas similares a registro (menos estrictas para opcionales)
        val primerNombre = binding.etPrimerNombre.text.toString().trim()
        val segundoNombre = binding.etSegundoNombre.text.toString().trim().ifBlank { null }
        val apellidoPaterno = binding.etApellidoPaterno.text.toString().trim()
        val apellidoMaterno = binding.etApellidoMaterno.text.toString().trim().ifBlank { null }
        val emailNuevo = binding.etEmail.text.toString().trim()
        val passwordNueva = binding.etPasswordNueva.text.toString().trim().ifBlank { null }
        val telefono = binding.etTelefono.text.toString().trim().ifBlank { null }
        val nombreCalle = binding.etNombreCalle.text.toString().trim()
        val numeroCalleTxt = binding.etNumeroCalle.text.toString().trim()
        val comunaId = if (binding.spComuna.isEnabled && binding.spComuna.selectedItemPosition in listaComunas.indices) listaComunas[binding.spComuna.selectedItemPosition].first else null

        var valido = true
        val patronNombre: Pattern = Pattern.compile("^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$")
        if (primerNombre.isEmpty()) { binding.etPrimerNombre.error = "Requerido"; valido = false }
        else if (!patronNombre.matcher(primerNombre).matches()) { binding.etPrimerNombre.error = "Solo letras"; valido = false }
        if (apellidoPaterno.isEmpty()) { binding.etApellidoPaterno.error = "Requerido"; valido = false }
        else if (!patronNombre.matcher(apellidoPaterno).matches()) { binding.etApellidoPaterno.error = "Solo letras"; valido = false }
        if (!apellidoMaterno.isNullOrBlank() && !patronNombre.matcher(apellidoMaterno).matches()) { binding.etApellidoMaterno.error = "Solo letras"; valido = false }
        if (emailNuevo.isEmpty()) { binding.etEmail.error = "Requerido"; valido = false }
        else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailNuevo).matches()) { binding.etEmail.error = "Correo inválido"; valido = false }
        val numeroCalle = numeroCalleTxt.toIntOrNull()
        if (nombreCalle.isEmpty()) { binding.etNombreCalle.error = "Requerido"; valido = false }
        if (numeroCalleTxt.isNotEmpty() && (numeroCalle == null || numeroCalle <= 0)) { binding.etNumeroCalle.error = "Número inválido"; valido = false }
        if (!valido) return

        val userActual = session.getUser()
        val cambiosCriticos = (emailNuevo != userActual?.correo) || passwordNueva != null || (comunaId != null && comunaId != userActual?.comuna_id)

        fun ejecutarPatchConfirmado() {
            binding.btnGuardarCambios.isEnabled = false
            binding.pbGuardando.visibility = View.VISIBLE
            binding.tvEstado.visibility = View.VISIBLE
            binding.tvEstado.text = "Guardando cambios..."
            lifecycleScope.launch {
                try {
                    val campos = mapOf(
                        "primer_nombre" to primerNombre,
                        "segundo_nombre" to segundoNombre,
                        "apellido_paterno" to apellidoPaterno,
                        "apellido_materno" to apellidoMaterno,
                        "email_contacto" to emailNuevo,
                        "password" to passwordNueva,
                        "telefono_contacto" to telefono,
                        "nombre_calle" to nombreCalle,
                        "numero_calle" to numeroCalle,
                        "comuna_id" to comunaId
                    )
                    withContext(Dispatchers.IO) { repo.editarCliente(campos) }
                    // Refrescar perfil
                    val usuarioActualizado = withContext(Dispatchers.IO) { repo.obtenerPerfil() }
                    session.saveUser(usuarioActualizado)
                    binding.tvEstado.text = "Cambios guardados"
                    Toast.makeText(this@EditProfileActivity, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    binding.tvEstado.text = "Error: ${e.message}"
                    Toast.makeText(this@EditProfileActivity, "Error guardando: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.pbGuardando.visibility = View.GONE
                    binding.btnGuardarCambios.isEnabled = true
                }
            }
        }

        if (cambiosCriticos) {
            mostrarDialogoConfirmacion { correoConfirmado, passwordConfirmada ->
                lifecycleScope.launch {
                    try {
                        val jsonLogin = withContext(Dispatchers.IO) { repo.login(correoConfirmado, passwordConfirmada) }
                        val token = when {
                            jsonLogin.has("authToken") -> jsonLogin.get("authToken").asString
                            jsonLogin.has("token") -> jsonLogin.get("token").asString
                            jsonLogin.has("jwt") -> jsonLogin.get("jwt").asString
                            else -> null
                        } ?: throw IllegalStateException("Respuesta sin token")
                        session.saveToken(token)
                        ejecutarPatchConfirmado()
                    } catch (e: Exception) {
                        Toast.makeText(this@EditProfileActivity, "Credenciales inválidas: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            ejecutarPatchConfirmado()
        }
    }

    

    private fun mostrarDialogoConfirmacion(onOk: (String, String) -> Unit) {
        val vista = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        // Usamos simple layout, agregamos programáticamente 2 EditTexts
        val cont = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }
        val etCorreo = android.widget.EditText(this).apply {
            hint = "Correo actual"
            setText(session.getUserEmail() ?: "")
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val etPassword = android.widget.EditText(this).apply {
            hint = "Contraseña actual"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        cont.addView(etCorreo)
        cont.addView(etPassword)
        AlertDialog.Builder(this)
            .setTitle("Confirmar credenciales")
            .setView(cont)
            .setMessage("Ingresa tu correo y contraseña para confirmar cambios críticos (email/contraseña/comuna).")
            .setPositiveButton("Confirmar") { d, _ ->
                val c = etCorreo.text.toString().trim()
                val p = etPassword.text.toString()
                if (c.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this, "Campos vacíos", Toast.LENGTH_SHORT).show()
                } else onOk(c, p)
                d.dismiss()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }
}
