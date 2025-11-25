package com.example.apptest.empleado.services

import com.example.apptest.empleado.models.XanoEmpleado
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface XanoEmpleadoService {
    @GET("empleado")
    suspend fun listar(): List<XanoEmpleado>
    @GET("empleado/{id}")
    suspend fun obtener(@Path("id") id: Int): XanoEmpleado

    @PATCH("empleado/{id}")
    suspend fun actualizar(@Path("id") id: Int, @Body cuerpo: Map<String, @JvmSuppressWildcards Any?>): XanoEmpleado

    @DELETE("empleado/{id}")
    suspend fun eliminar(@Path("id") id: Int): ResponseBody

    @POST("empleado")
    suspend fun crear(@Body cuerpo: Map<String, @JvmSuppressWildcards Any?>): XanoEmpleado
}
