package com.example.apptest.core.network

import android.content.Context
import com.example.apptest.core.network.ApiClient
import com.example.apptest.user.models.User
import com.example.apptest.user.models.XanoClienteRegistro
import com.example.apptest.user.models.XanoLoginForm
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repositorio del flujo de autenticación contra Xano (sin MVVM, llamado directo desde la UI).
 */
class XanoAuthRepository(private val context: Context) {
    // Usamos ApiClient para asegurar que el AuthInterceptor adjunta el token Bearer en auth/me
    private val servicio by lazy { ApiClient.getRetrofitAuth(context).create(com.example.apptest.user.services.XanoAuthService::class.java) }
    private val sessionManager by lazy { com.example.apptest.core.storage.SessionManager.getInstance(context) }
    // Servicio de edición de cliente autenticado
    private val servicioEditar by lazy { ApiClient.getRetrofit(context).create(com.example.apptest.cliente.services.XanoClienteEditarService::class.java) }

    suspend fun login(correo: String, contrasena: String): JsonObject {
        val cuerpo = XanoLoginForm(correo, contrasena).toBody()
        return servicio.login(cuerpo)
    }

    /**
     * Registro EXCLUSIVAMENTE como cliente, con el esquema requerido por Xano.
     */
    suspend fun registrarCliente(datos: XanoClienteRegistro): JsonObject {
        return servicio.registrar(datos.toBody())
    }

    suspend fun obtenerPerfil(): User {
        val tipo = sessionManager.getUser()?.user_type?.lowercase()
        return when (tipo) {
            "empleado" -> {
                val json = servicio.meEmpleado()
                mapearEmpleadoDesdeJson(json)
            }
            else -> {
                try {
                    val json = servicio.meCliente()
                    mapearUserDesdeJson(json)
                } catch (_: Exception) {
                    val jsonAlt = servicio.meEmpleado()
                    mapearEmpleadoDesdeJson(jsonAlt)
                }
            }
        }
    }

    fun mapearUserDesdeJson(json: JsonObject): User {
        // Extraemos los campos directamente de la nueva API /auth/MeCliente
        val id = json.optLong("id")
        val createdAtRaw = json.optAnyToString("created_at")
        val createdAt = formatEpochMillis(createdAtRaw)
        val habilitado = json.optBoolean("habilitado")

        // Construimos nombre(s) y apellido(s) completos siguiendo la respuesta de MeCliente
        val primerNombre = json.optString("primer_nombre")
        val segundoNombre = json.optString("segundo_nombre")
        val apellidoPaterno = json.optString("apellido_paterno")
        val apellidoMaterno = json.optString("apellido_materno")
        val nombres = listOfNotNull(primerNombre, segundoNombre).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        val apellidos = listOfNotNull(apellidoPaterno, apellidoMaterno).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

        val email = json.optString("email_contacto")
        val telefono = json.optString("telefono_contacto")

        // Datos de dirección
        val direccion = json.optString("direccion")
        val nombreCalle = json.optString("nombre_calle")
        val numeroCalle = json.optIntFlex("numero_calle")
        val comunaId = json.optIntFlex("comuna_id")
        val nombreComuna = json.optString("nombre_comuna")

        // Datos específicos del tipo de usuario (cliente en este caso)
        val tipoCliente = json.optString("tipo_cliente")
        // Como esta API es solo para clientes, podemos asignarlo directamente.
        val userType = "cliente"

        return User(
            id = id,
            nombres = nombres,
            apellidos = apellidos,
            rut = null, // No viene en la respuesta
            dv = null,  // No viene en la respuesta
            correo = email,
            rol = null, // No es parte de MeCliente
            direccion = direccion,
            enabled = habilitado,
            createdAt = createdAt,
            user_type = userType,
            username = null, // No es parte de MeCliente
            telefono_contacto = telefono,
            tipo_cliente = tipoCliente,
            comuna_id = comunaId,
            nombre_comuna = nombreComuna,
            nombre_calle = nombreCalle,
            numero_calle = numeroCalle,
            rol_id = null // No es parte de MeCliente
        )
    }

    fun mapearEmpleadoDesdeJson(json: JsonObject): User {
        val id = json.optLong("id")
        val createdAtRaw = json.optAnyToString("created_at")
        val createdAt = formatEpochMillis(createdAtRaw)
        val habilitado = json.optBoolean("habilitado")
        val primerNombre = json.optString("primer_nombre")
        val segundoNombre = json.optString("segundo_nombre")
        val apellidoPaterno = json.optString("apellido_paterno")
        val apellidoMaterno = json.optString("apellido_materno")
        val nombres = listOfNotNull(primerNombre, segundoNombre).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        val apellidos = listOfNotNull(apellidoPaterno, apellidoMaterno).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        // RUT puede venir numérico; usamos optAnyToString para capturarlo
        val rut = json.optAnyToString("rut")
        val dv = json.optString("dv")
        val correo = json.optString("email_contacto")
        val username = json.optString("username")
        val rolId = json.optIntFlex("rol_id")
        val comunaId = json.optIntFlex("comuna_id")
        val nombreRol = json.optString("nombre_rol")
        val nombreComuna = json.optString("nombre_comuna")
        val nombreCalle = json.optString("nombre_calle")
        val numeroCalle = json.optIntFlex("numero_calle")
        val fullAddress = json.optString("full_address")
        return User(
            id = id,
            nombres = nombres,
            apellidos = apellidos,
            rut = rut,
            dv = dv,
            correo = correo,
            rol = nombreRol,
            direccion = fullAddress,
            enabled = habilitado,
            createdAt = createdAt,
            user_type = "empleado",
            username = username,
            telefono_contacto = null,
            tipo_cliente = null,
            comuna_id = comunaId,
            nombre_comuna = nombreComuna,
            nombre_calle = nombreCalle,
            numero_calle = numeroCalle,
            rol_id = rolId
        )
    }

    /**
     * Edita campos del cliente autenticado. Solo envía los pares clave-valor no nulos.
     * Usa PATCH cliente/editar.
     */
    suspend fun editarCliente(campos: Map<String, Any?>): JsonObject {
        // Filtramos valores nulos y strings vacíos para replicar lógica filter_null & filter_empty_text
        val payload = campos.filter { (_, v) ->
            when (v) {
                null -> false
                is String -> v.isNotBlank()
                else -> true
            }
        }
        return servicioEditar.editar(payload)
    }

}

// Helpers
private fun formatEpochMillis(raw: String?): String? {
    val ms = raw?.toLongOrNull() ?: return raw
    // Sanity check: epoch ms should be within plausible range (after 2000)
    if (ms < 946684800000L) return raw // leave as-is if seems not ms
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(ms))
    } catch (e: Exception) {
        raw
    }
}
private fun JsonObject.opt(key: String): JsonElement? =
    if (this.has(key) && !this.get(key).isJsonNull) this.get(key) else null

private fun JsonObject.optString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { k -> opt(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() } }

private fun JsonObject.optLong(key: String): Long? =
    opt(key)?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asLong else it.asString.toLongOrNull() }

private fun JsonObject.optIntFlex(key: String): Int? {
    val value = this.get(key)
    return when {
        value == null || value.isJsonNull -> null
        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asInt
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.toIntOrNull()
        else -> null
    }
}

private fun JsonObject.optBoolean(vararg keys: String): Boolean? =
    keys.firstNotNullOfOrNull { k ->
        opt(k)?.let {
            if (it.isJsonPrimitive) {
                when {
                    it.asJsonPrimitive.isBoolean -> it.asBoolean
                    it.asJsonPrimitive.isString -> it.asString.lowercase().let { s ->
                        when (s) {
                            "true", "1", "t", "yes", "y" -> true
                            "false", "0", "f", "no", "n" -> false
                            else -> null
                        }
                    }
                    it.asJsonPrimitive.isNumber -> (it.asInt != 0)
                    else -> null
                }
            } else null
        }
    }

private fun JsonObject.optAnyToString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { k ->
        opt(k)?.let {
            when {
                it.isJsonPrimitive && it.asJsonPrimitive.isString -> it.asString
                it.isJsonPrimitive && it.asJsonPrimitive.isNumber -> it.asLong.toString()
                else -> null
            }
        }
    }
