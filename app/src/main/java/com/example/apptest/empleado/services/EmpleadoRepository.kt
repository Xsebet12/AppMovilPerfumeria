package com.example.apptest.empleado.services

import android.content.Context
import com.example.apptest.empleado.models.XanoEmpleado
import com.example.apptest.core.network.ApiClient
import okhttp3.ResponseBody

class EmpleadoRepository(private val context: Context) {
    private val servicio by lazy { ApiClient.getRetrofit(context).create(XanoEmpleadoService::class.java) }
    private val authSrv by lazy { ApiClient.getRetrofitAuth(context).create(com.example.apptest.user.services.XanoAuthService::class.java) }

    suspend fun listar(): Result<List<XanoEmpleado>> = runCatching { servicio.listar() }
    suspend fun obtener(id: Int): Result<XanoEmpleado> = runCatching { servicio.obtener(id) }

    suspend fun crear(datos: Map<String, Any?>): Result<XanoEmpleado> = runCatching { servicio.crear(datos) }

    /**
     * Crea empleado vía /auth/register con tipo_registro="empleado".
     * Si la respuesta incluye id, retorna el empleado obtenido por GET empleado/{id}.
     */
    suspend fun registrarEmpleado(datos: Map<String, Any?>): Result<XanoEmpleado> = runCatching {
        val payload = mutableMapOf<String, Any>("tipo_registro" to "empleado")
        for ((k, v) in datos) {
            when (v) {
                null -> {}
                is String -> if (v.isNotBlank()) payload[k] = v
                else -> payload[k] = v
            }
        }
        val resp = authSrv.registrar(payload)
        fun jsonInt(key: String): Int? {
            val el = resp.get(key) ?: return null
            return try { if (el.isJsonPrimitive) el.asInt else el.asString.toIntOrNull() } catch (_: Exception) { null }
        }
        val id = jsonInt("id") ?: jsonInt("user_id") ?: throw IllegalStateException("Registro empleado sin id")
        servicio.obtener(id)
    }

    suspend fun actualizar(id: Int, datos: Map<String, Any?>): Result<XanoEmpleado> = runCatching { servicio.actualizar(id, datos) }

    suspend fun eliminar(id: Int): Result<String> = runCatching {
        val body: ResponseBody = servicio.eliminar(id)
        val raw = body.string().trim()
        val parsed = try {
            if (raw.startsWith("{")) {
                val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
                json.get("message")?.asString ?: raw
            } else raw
        } catch (_: Exception) { raw }
        parsed.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "Empleado eliminado"
    }
}
