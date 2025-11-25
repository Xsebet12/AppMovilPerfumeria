package com.example.apptest.ui.cliente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.apptest.core.network.XanoAuthRepository
import com.example.apptest.databinding.FragmentProfileBinding
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.example.apptest.ui.comun.LoginActivity
import com.example.apptest.ui.comun.EditProfileActivity

class ProfileFragment : Fragment() {
    // Uso ViewBinding para acceder a las vistas 
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private val repo by lazy { XanoAuthRepository(requireContext().applicationContext) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        sessionManager = SessionManager.getInstance(requireContext())
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Si no hay sesión (token), enviamos a Login
        if (sessionManager.getToken() == null) {
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            return
        }

        renderUser()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _binding?.pbPerfil?.visibility = android.view.View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val usuario = repo.obtenerPerfil()
                sessionManager.saveUser(usuario)
                renderUser()
            } catch (e: Exception) {
                val msg = when (e) {
                    is HttpException ->
                        if (e.code() == 401) {
                            "Sesión inválida o token de otro tipo de usuario."
                        } else {
                            "Error ${e.code()} al obtener el perfil"
                        }
                    else -> e.message ?: "Error desconocido"
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                renderUser()
            }
            finally { _binding?.pbPerfil?.visibility = android.view.View.GONE }
        }

        // Configuro el listener del botón de logout.
        binding.btnLogout.setOnClickListener {
            sessionManager.clear()

            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnEditarPerfil.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }
    }

    private fun renderUser() {
        val user = sessionManager.getUser()
        binding.tvName.text = sessionManager.getUserName() ?: "Cargando perfil..."
        binding.tvNroCuenta.text = "Nro de cuenta: ${user?.id ?: "-"}"
        binding.tvEmail.text = sessionManager.getUserEmail() ?: "-"

        val esEmpleado = user?.user_type?.equals("empleado", true) == true
        if (esEmpleado) {
            // Ocultar elementos propios de cliente
            binding.tvTelefono.visibility = View.GONE
            binding.tvTipoCliente.visibility = View.GONE

            // Mostrar layout empleado
            binding.layoutEmpleadoExtra.visibility = View.VISIBLE
            binding.tvRolEmpleado.text = "Rol: ${user?.rol ?: "-"}"
            val rutFmt = formatearRut(user?.rut, user?.dv)
            binding.tvRutEmpleado.text = "RUT: $rutFmt"
            binding.tvUsernameEmpleado.text = "Usuario: ${user?.username ?: "-"}"
            binding.tvEstadoEmpleado.text = "Estado: ${if (user?.enabled == true) "Habilitado" else "Deshabilitado"}"
            binding.tvCreatedAtEmpleado.text = "Creado: ${user?.createdAt ?: "-"}"
            binding.tvComunaEmpleado.text = "Comuna: ${user?.nombre_comuna ?: user?.comuna_id ?: "-"}"
        } else {
            // Cliente: mostrar tipo y teléfono
            binding.tvTelefono.visibility = View.VISIBLE
            binding.tvTipoCliente.visibility = View.VISIBLE
            binding.layoutEmpleadoExtra.visibility = View.GONE
            binding.tvTelefono.text = "Teléfono: ${user?.telefono_contacto ?: "-"}"
            val tipoLegible = formatearTipoCliente(user?.tipo_cliente)
            binding.tvTipoCliente.text = tipoLegible
        }

        // Dirección: para empleado mostramos la dirección tal cual sin repetición de comuna; para cliente agregamos comuna.
        val direccionBase = (user?.direccion
            ?: listOfNotNull(user?.nombre_calle, user?.numero_calle?.toString())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null })
        if (esEmpleado) {
            binding.tvDireccionDetalle.text = "Dirección: ${direccionBase ?: "-"}"
        } else {
            val comunaTexto = when {
                !user?.nombre_comuna.isNullOrBlank() -> "Comuna: ${user?.nombre_comuna}"
                user?.comuna_id != null -> "Comuna ID: ${user.comuna_id}"
                else -> null
            }
            binding.tvDireccionDetalle.text = "Dirección: ${direccionBase ?: "-"}${if (comunaTexto != null) ", $comunaTexto" else ""}"
        }
    }

    private fun formatearTipoCliente(tipo: String?): String {
        val normalizado = tipo?.trim()?.lowercase()
        return when (normalizado) {
            "vip", "cliente vip" -> "Cliente Vip"
            "detalle", "cliente detalle" -> "Cliente Detalle"
            "mayorista", "cliente mayorista" -> "Cliente Mayorista"
            null, "" -> "Cliente"
            else -> tipo
        }
    }

    private fun formatearRut(rutRaw: String?, dv: String?): String {
        val cuerpo = rutRaw?.filter { it.isDigit() } ?: return "-"
        if (cuerpo.isBlank()) return "-"
        // Insertar puntos cada 3 desde la derecha
        val reversed = cuerpo.reversed()
        val grupos = reversed.chunked(3).joinToString(".") { it }
        val conPuntos = grupos.reversed()
        val dvStr = dv?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        return if (dvStr != null) "$conPuntos-$dvStr" else conPuntos
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
