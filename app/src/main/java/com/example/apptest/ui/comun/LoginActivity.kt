package com.example.apptest.ui.comun

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.apptest.core.network.XanoAuthRepository
import com.example.apptest.databinding.ActivityLoginBinding
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.launch
import com.example.apptest.ui.cliente.RegisterActivity
 

class LoginActivity : ComponentActivity() {
    // ViewBinding principal (enlace al layout XML de login)
    private lateinit var binding: ActivityLoginBinding
    // Gestor de sesión basado en SharedPreferences (persistencia de token + usuario)
    private lateinit var gestorSesion: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gestorSesion = SessionManager.getInstance(this)

        // Si ya existe una sesión activa con token guardado, redirigimos directamente
        if (gestorSesion.getToken() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repositorioXano = XanoAuthRepository(applicationContext)

        // Acción principal de inicio de sesión
        binding.btnLogin.setOnClickListener {
            val correo = binding.etEmail.text.toString().trim()
            val contrasena = binding.etPassword.text.toString().trim()

            lifecycleScope.launch {
                try {
                    binding.pbLogin.visibility = android.view.View.VISIBLE
                    binding.btnLogin.isEnabled = false
                    kotlinx.coroutines.delay(4000)
                    // Autenticación con timeout total de 5s
                    val respuestaJson = kotlinx.coroutines.withTimeoutOrNull(5000) {
                        repositorioXano.login(correo, contrasena)
                    } ?: throw java.util.concurrent.TimeoutException("Timeout 5s login")
                    val token = when {
                        respuestaJson.has("token") -> respuestaJson.get("token").asString
                        respuestaJson.has("authToken") -> respuestaJson.get("authToken").asString
                        respuestaJson.has("jwt") -> respuestaJson.get("jwt").asString
                        else -> throw IllegalStateException("No se encontró token en la respuesta de Xano")
                    }

                    if (binding.cbRememberSession.isChecked) {
                        gestorSesion.saveToken(token)
                    } else {
                        gestorSesion.setSessionOnlyToken(token)
                    }

                    // Determinar tipo de usuario e id desde la respuesta de login
                    val userTypeLogin = respuestaJson.get("user_type")?.asString?.lowercase()
                    val userIdLogin = respuestaJson.get("user_id")?.asLong

                    // Guardar minimal user (id + tipo) para navegación inmediata antes de perfil completo
                    gestorSesion.saveMinimalUser(userIdLogin, userTypeLogin)

                    try {
                        val usuario = kotlinx.coroutines.withTimeoutOrNull(5000) { repositorioXano.obtenerPerfil() }
                        usuario?.let { gestorSesion.saveUser(it) }
                    } catch (_: Exception) { }

                    val usuario = gestorSesion.getUser()
                    val nombreAMostrar = gestorSesion.getUserName() ?: ""
                    Toast.makeText(this@LoginActivity, "Bienvenido ${nombreAMostrar}", Toast.LENGTH_SHORT).show()

                    val intentMain = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(intentMain)
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Error de inicio de sesión: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.pbLogin.visibility = android.view.View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }

        // Navegación hacia pantalla de registro
        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
