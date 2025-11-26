package com.example.apptest.cliente.services

import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
interface XanoVentaService {
    @GET("venta/{id}")
    suspend fun obtenerVenta(@Path("id") id: Long): VentaRespuesta

    @GET("obtener_detalle_venta")
    suspend fun obtenerDetalleVenta(@Query("venta_id") ventaId: Long): VentaDetalleRespuesta

    @POST("venta/Ingresar_venta")
    suspend fun ingresarVenta(@Body cuerpo: IngresarVentaRequest): VentaRespuesta

    @POST("venta/Ingresar_venta")
    suspend fun ingresarVentaCompuesto(@Body cuerpo: IngresarVentaRequest): IngresarVentaCompuestaRespuesta

    @GET("venta_pendiente")
    suspend fun ventaPendiente(): List<VentaInfo>

    @POST("venta/{id}/aceptar")
    suspend fun aceptar(@Path("id") id: Long, @Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): EstadoVentaRespuesta

    @POST("venta/{id}/rechazar")
    suspend fun rechazar(@Path("id") id: Long, @Body body: Map<String, @JvmSuppressWildcards Any?>): EstadoVentaRespuesta

    @GET("venta/estado/{estado_pago}")
    suspend fun ventasPorEstado(@Path("estado_pago") estadoPago: String): List<VentaInfo>

    @POST("venta/{id}/enviado")
    suspend fun marcarEnviado(@Path("id") id: Long): com.example.apptest.cliente.services.SeguimientoPedido

    @POST("venta/{id}/despachado")
    suspend fun marcarDespachado(@Path("id") id: Long): com.example.apptest.cliente.services.SeguimientoPedido

    @POST("venta/{id}/entregado")
    suspend fun marcarEntregado(@Path("id") id: Long): com.example.apptest.cliente.services.SeguimientoPedido

    @GET("historial_compras_cliente")
    suspend fun historialComprasCliente(@Query("cliente_id") clienteId: Long): List<HistorialPedido>
}

data class VentaRespuesta(
    val id: Long,
    val fecha: Long?,
    val metodo_pago: String?,
    val canal: String?,
    val estado: Boolean?,
    val cliente_id: Long?,
    val total_venta: Double?,
    val estado_pago: String?
)

data class VentaDetalleRespuesta(
    val venta_info: VentaInfo,
    val detalles: List<DetalleVentaExtendido>,
    val verificacion: VerificacionVenta
)

data class VentaInfo(
    val id: Long,
    val fecha: Long?,
    val metodo_pago: String?,
    val canal: String?,
    val estado: Boolean?,
    val cliente_id: Long?,
    val total_venta: Double?,
    val estado_pago: String?,
    val estado_envio: String?
)

data class DetalleVentaExtendido(
    val id: Long,
    val inventario_id: Long,
    val venta_id: Long,
    val cantidad: Int,
    val costo_unitario: Double?,
    val precio_unitario: Double,
    val subtotal: Double,
    val nombre_producto: String?,
    val subtotal_calculado: Double?
)

data class VerificacionVenta(
    val total_almacenado: Double?,
    val total_calculado: Double?,
    val consistente: Boolean?
)

data class IngresarVentaRequest(
    val metodo_pago: String,
    val canal: String,
    val tipo_cliente: String?,
    val detalles: List<IngresarVentaDetalle>
)

data class IngresarVentaDetalle(
    val inventario_id: Long,
    val cantidad: Int
)

data class IngresarVentaCompuestaRespuesta(
    val venta: VentaRespuesta,
    val seguimiento: com.example.apptest.cliente.services.SeguimientoPedido
)

data class EstadoVentaRespuesta(
    val id: Long,
    val estado: Boolean,
    val estado_pago: String
)

data class HistorialPedido(
    val pedido_info: String,
    val id_venta: Long,
    val estado_pago: String?,
    val estado_envio: String?,
    val detalles: List<HistorialDetalle>
)

data class HistorialDetalle(
    val id: Long,
    val inventario_id: Long,
    val venta_id: Long,
    val cantidad: Int,
    val costo_unitario: Double?,
    val precio_unitario: Double,
    val subtotal: Double,
    val nombre_producto: String?
)
