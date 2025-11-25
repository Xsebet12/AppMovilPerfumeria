package com.example.apptest.empleado.models

data class XanoEmpleado(
    val id: Int? = null,
    val created_at: String? = null,
    val primer_nombre: String? = null,
    val segundo_nombre: String? = null,
    val apellido_paterno: String? = null,
    val apellido_materno: String? = null,
    val username: String? = null,
    val rut: Int? = null,
    val dv: String? = null,
    val email_contacto: String? = null,
    val sueldo: Double? = null,
    val nombre_calle: String? = null,
    val numero_calle: Int? = null,
    val comuna_id: Int? = null,
    val rol_id: Int? = null,
    val habilitado: Boolean? = null
)

