package com.example.apptest.user.services

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface XanoAuthService {
    @POST("auth/login")
    suspend fun login(@Body cuerpo: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @POST("auth/register")
    suspend fun registrar(@Body cuerpo: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @GET("auth/MeCliente")
    suspend fun meCliente(): JsonObject

    @GET("auth/MeEmp")
    suspend fun meEmpleado(): JsonObject
}
