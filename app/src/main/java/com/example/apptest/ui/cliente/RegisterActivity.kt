package com.example.apptest.ui.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import java.util.regex.Pattern
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.apptest.core.network.XanoAuthRepository
import com.example.apptest.core.network.ApiClient
import com.example.apptest.pais.services.XanoRegComunaService
import com.example.apptest.pais.models.XanoRegionComuna
import com.example.apptest.user.models.XanoClienteRegistro
import com.example.apptest.databinding.ActivityRegisterBinding
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {
    // ViewBinding para el layout de registro
    private lateinit var enlace: ActivityRegisterBinding
    // Gestor de sesión con SharedPreferences
    private lateinit var gestorSesion: SessionManager
    // Repositorio simple para llamadas a Xano
    private lateinit var repositorioXano: XanoAuthRepository
    // Servicio de Región/Comuna (RegComuna)
    private val servicioRegComuna by lazy { ApiClient.getRetrofit(this).create(XanoRegComunaService::class.java) }
    // Listas en memoria para poblar los spinners (id,nombre)
    private var listaRegiones: List<Pair<Int, String>> = emptyList()
    private var listaComunas: List<Triple<Int, String, Int?>> = emptyList() // (id, nombre, region_id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    enlace = ActivityRegisterBinding.inflate(layoutInflater)
    setContentView(enlace.root)

        // Inicializo las dependencias.
        gestorSesion = SessionManager.getInstance(this)
        repositorioXano = XanoAuthRepository(applicationContext)
        enlace.btnRegister.setOnClickListener {
            // Limpiar errores previos
            enlace.etPrimerNombre.error = null
            enlace.etSegundoNombre.error = null
            enlace.etApellidoPaterno.error = null
            enlace.etApellidoMaterno.error = null
            enlace.etEmail.error = null
            enlace.etPassword.error = null
            enlace.etNombreCalle.error = null
            enlace.etNumeroCalle.error = null

            val primerNombre = enlace.etPrimerNombre.text.toString().trim()
            val segundoNombre = enlace.etSegundoNombre.text.toString().trim().ifBlank { null }
            val apellidoPaterno = enlace.etApellidoPaterno.text.toString().trim()
            val apellidoMaterno = enlace.etApellidoMaterno.text.toString().trim().ifBlank { null }
            val correo = enlace.etEmail.text.toString().trim()
            val contrasena = enlace.etPassword.text.toString()
            val nombreCalle = enlace.etNombreCalle.text.toString().trim()
            val numeroCalleTxt = enlace.etNumeroCalle.text.toString().trim()
            val telefono = enlace.etTelefono.text.toString().trim().ifBlank { null }

            var valido = true
            val patronNombre: Pattern = Pattern.compile("^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$")
            if (primerNombre.isEmpty()) {
                enlace.etPrimerNombre.error = "Requerido"
                valido = false
            } else if (!patronNombre.matcher(primerNombre).matches()) {
                enlace.etPrimerNombre.error = "Solo letras y espacios"
                valido = false
            }
            if (!segundoNombre.isNullOrBlank() && !patronNombre.matcher(segundoNombre).matches()) {
                enlace.etSegundoNombre.error = "Solo letras y espacios"
                valido = false
            }
            if (apellidoPaterno.isEmpty()) {
                enlace.etApellidoPaterno.error = "Requerido"
                valido = false
            } else if (!patronNombre.matcher(apellidoPaterno).matches()) {
                enlace.etApellidoPaterno.error = "Solo letras y espacios"
                valido = false
            }
            if (!apellidoMaterno.isNullOrBlank() && !patronNombre.matcher(apellidoMaterno).matches()) {
                enlace.etApellidoMaterno.error = "Solo letras y espacios"
                valido = false
            }
            if (correo.isEmpty()) {
                enlace.etEmail.error = "Requerido"
                valido = false
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                enlace.etEmail.error = "Correo inválido"
                valido = false
            }
            if (contrasena.isEmpty()) {
                enlace.etPassword.error = "Requerida"
                valido = false
            } else if (contrasena.length < 8) {
                enlace.etPassword.error = "Mínimo 8 caracteres"
                valido = false
            }
            if (nombreCalle.isEmpty()) {
                enlace.etNombreCalle.error = "Requerido"
                valido = false
            }
            val numeroCalle = numeroCalleTxt.toIntOrNull()
            if (numeroCalleTxt.isEmpty()) {
                enlace.etNumeroCalle.error = "Requerido"
                valido = false
            } else if (numeroCalle == null || numeroCalle <= 0) {
                enlace.etNumeroCalle.error = "Debe ser un número válido (>0)"
                valido = false
            }
            if (!telefono.isNullOrBlank()) {
                val patronTelefono = Pattern.compile("^[0-9+\\- ]{7,15}$")
                if (!patronTelefono.matcher(telefono).matches()) {
                    enlace.etTelefono.error = "Formato inválido"
                    valido = false
                }
            }
            if (!valido) return@setOnClickListener

            // Validación de selección de región y comuna
            val indiceRegion = enlace.spRegion.selectedItemPosition
            if (indiceRegion !in listaRegiones.indices) {
                Toast.makeText(this, "Selecciona una región", Toast.LENGTH_SHORT).show()
                enlace.spRegion.requestFocus()
                return@setOnClickListener
            }
            val indiceComuna = enlace.spComuna.selectedItemPosition
            val comunaId = if (enlace.spComuna.isEnabled && indiceComuna in listaComunas.indices) {
                listaComunas[indiceComuna].first
            } else null
            if (comunaId == null) {
                Toast.makeText(this, "Selecciona una comuna", Toast.LENGTH_SHORT).show()
                enlace.spComuna.requestFocus()
                return@setOnClickListener
            }

            enlace.btnRegister.isEnabled = false
            enlace.pbCargando.visibility = View.VISIBLE
            enlace.tvEstadoRegistro.text = "Registrando..."
            enlace.tvEstadoRegistro.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    run {
                        // Registrar SIEMPRE como CLIENTE
                        val numeroCalleInt = numeroCalle!!
                        val registroCliente = XanoClienteRegistro(
                            primer_nombre = primerNombre,
                            segundo_nombre = segundoNombre,
                            apellido_paterno = apellidoPaterno,
                            apellido_materno = apellidoMaterno,
                            email_contacto = correo,
                            password = contrasena,
                            comuna_id = comunaId,
                            nombre_calle = nombreCalle,
                            numero_calle = numeroCalleInt,
                            telefono_contacto = telefono,
                            tipo_cliente = "Detalle"
                        )
                        withContext(Dispatchers.IO) { repositorioXano.registrarCliente(registroCliente) }

                        // Autologin tras registro para obtener el token
                        val json = withContext(Dispatchers.IO) { repositorioXano.login(correo, contrasena) }
                        val token = when {
                            json.has("authToken") -> json.get("authToken").asString
                            json.has("token") -> json.get("token").asString
                            json.has("jwt") -> json.get("jwt").asString
                            else -> throw IllegalStateException("No se encontró token en la respuesta de Xano")
                        }
                        gestorSesion.saveToken(token)
                        // Obtenemos perfil actual y lo guardamos
                        try {
                            val usuario = withContext(Dispatchers.IO) { repositorioXano.obtenerPerfil() }
                            gestorSesion.saveUser(usuario)
                        } catch (_: Exception) { }
                    }

                    // Muestro un mensaje de bienvenida.
                    val nombreParaMostrar = gestorSesion.getUserName() ?: ""
                    Toast.makeText(this@RegisterActivity, "Registro exitoso. Bienvenido ${nombreParaMostrar}", Toast.LENGTH_LONG).show()

                    // Navego a la pantalla principal y limpio el historial de actividades.
                    val intent = Intent(this@RegisterActivity, com.example.apptest.ui.comun.MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)

                } catch (e: Exception) {
                    // Error: mostrarmos mensaje y lo reflejamos en estado.
                    enlace.tvEstadoRegistro.text = "Error: ${e.message}"
                    Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    // Oculto el indicador y rehabilito el botón.
                    enlace.pbCargando.visibility = View.GONE
                    enlace.btnRegister.isEnabled = true
                    if (enlace.tvEstadoRegistro.text.toString().startsWith("Registrando")) {
                        enlace.tvEstadoRegistro.visibility = View.GONE
                    }
                }
            }
        }

        // Cargar regiones+comunas combinadas y configurar cascada
        cargarRegionesYConfigurarSpinners()
    }

    private fun cargarRegionesYConfigurarSpinners() {
        enlace.spComuna.isEnabled = false
        lifecycleScope.launch {
            try {
                val regionesCompletas = servicioRegComuna.obtenerRegionesConComunas()
                listaRegiones = regionesCompletas.map { it.id to (it.nombreRegion ?: it.id.toString()) }
                val nombresRegion = listaRegiones.map { it.second }
                val adaptadorRegion = android.widget.ArrayAdapter(this@RegisterActivity, android.R.layout.simple_spinner_item, nombresRegion).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                enlace.spRegion.adapter = adaptadorRegion

                // Preconstruir comunas agrupadas por región usando la relación directa del objeto región
                val comunasPorRegion: Map<Int, List<Triple<Int, String, Int?>>> = regionesCompletas.associate { region ->
                    val lista = region.comunas.orEmpty().map { c ->
                        Triple(c.id, c.nombreComuna ?: c.id.toString(), c.regionId)
                    }
                    region.id to lista
                }

                enlace.spRegion.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                        val rid = listaRegiones.getOrNull(position)?.first ?: return
                        listaComunas = comunasPorRegion[rid].orEmpty()
                        val nombresComuna = listaComunas.map { it.second }
                        val adaptadorComuna = android.widget.ArrayAdapter(this@RegisterActivity, android.R.layout.simple_spinner_item, nombresComuna).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        enlace.spComuna.adapter = adaptadorComuna
                        enlace.spComuna.isEnabled = listaComunas.isNotEmpty()
                        if (listaComunas.isNotEmpty()) {
                            enlace.spComuna.setSelection(0)
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                        enlace.spComuna.isEnabled = false
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Error cargando regiones: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
