package com.example.apptest.ui.cliente

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.apptest.core.network.ApiClient
import com.example.apptest.cliente.services.XanoVentaService
import com.example.apptest.cliente.services.XanoSeguimientoPedidoService
import com.example.apptest.cliente.services.DetalleVentaExtendido
import com.example.apptest.core.storage.SessionManager
import com.example.apptest.databinding.ActivityOrderSummaryBinding
import com.example.apptest.databinding.DialogUpdateTrackingBinding
import com.example.apptest.databinding.ItemOrderDetailBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Pantalla de Resumen de Venta.
 * Consulta `GET obtener_detalle_venta?venta_id=` y muestra encabezado + lista de detalles.
 */
class OrderSummaryActivity : ComponentActivity() {
    private lateinit var binding: ActivityOrderSummaryBinding
    private val scope = MainScope()
    private val items: MutableList<DetalleVentaExtendido> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOrderSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerDetalles.layoutManager = LinearLayoutManager(this)
        val adaptador = DetallesAdapter(items)
        binding.recyclerDetalles.adapter = adaptador

        val ventaId = intent.getLongExtra("venta_id", -1L)
        if (ventaId == -1L) {
            finish(); return
        }

        scope.launch {
            try {
                binding.pbOrderSummary.visibility = android.view.View.VISIBLE
                kotlinx.coroutines.delay(4000)
                val servicio = ApiClient.getRetrofit(this@OrderSummaryActivity).create(XanoVentaService::class.java)
                val respuesta = servicio.obtenerDetalleVenta(ventaId)
                // Encabezado
                binding.tvVentaId.text = "Venta #${respuesta.venta_info.id}"
                binding.tvMetodoPago.text = respuesta.venta_info.metodo_pago ?: "-"
                binding.tvCanal.text = respuesta.venta_info.canal ?: "-"
                val estadoPago = respuesta.venta_info.estado_pago?.lowercase()
                binding.tvEstado.text = when (estadoPago) {
                    "aceptado" -> "Pagada"
                    "rechazado" -> "Rechazada"
                    else -> "Pendiente"
                }
                val total = respuesta.venta_info.total_venta ?: respuesta.detalles.sumOf { it.subtotal }
                binding.tvTotalVenta.text = formatearCLP(total)
                // Lista
                items.clear(); items.addAll(respuesta.detalles)
                adaptador.update(items)
                try {
                    val segSrv = ApiClient.getRetrofit(this@OrderSummaryActivity).create(XanoSeguimientoPedidoService::class.java)
                    val seg = segSrv.obtenerPorVenta(ventaId)
                    binding.tvEstadoEnvio.text = seg.estado_envio
                    binding.tvNumeroSeguimiento.text = seg.numero_seguimiento ?: "-"
                    binding.tvFechaEstimada.text = seg.fecha_estimada_entrega ?: "-"
                } catch (_: Exception) {
                    binding.tvEstadoEnvio.text = "-"
                    binding.tvNumeroSeguimiento.text = "-"
                    binding.tvFechaEstimada.text = "-"
                }
            } catch (_: Exception) {
                finish()
            } finally { binding.pbOrderSummary.visibility = android.view.View.GONE }
        }

        binding.btnActualizarSeguimiento.setOnClickListener {
            val user = SessionManager.getInstance(this).getUser()
            val rol = user?.rol?.lowercase()
            if (rol == null || rol.contains("cliente")) { return@setOnClickListener }
            val dialogView = DialogUpdateTrackingBinding.inflate(layoutInflater)
            val d = android.app.AlertDialog.Builder(this).setView(dialogView.root).create()
            dialogView.btnCancelar.setOnClickListener { d.dismiss() }
            dialogView.btnGuardar.setOnClickListener {
                val estado = dialogView.spEstadoEnvio.selectedItem?.toString() ?: ""
                val numero = dialogView.etNumeroSeguimiento.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                val fecha = dialogView.etFechaEstimada.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                scope.launch {
                    try {
                        val segSrv = com.example.apptest.core.network.ApiClient.getRetrofit(this@OrderSummaryActivity).create(XanoSeguimientoPedidoService::class.java)
                        val actual = try { segSrv.obtenerPorVenta(ventaId) } catch (_: Exception) { null }
                        if (actual != null) {
                            val body = com.example.apptest.cliente.services.SeguimientoPedidoUpdate(
                                seguimiento_pedido_id = actual.id,
                                venta_id = ventaId,
                                estado_envio = estado,
                                numero_seguimiento = numero,
                                fecha_estimada_entrega = fecha
                            )
                            val res = segSrv.actualizar(body)
                            binding.tvEstadoEnvio.text = res.estado_envio
                            binding.tvNumeroSeguimiento.text = res.numero_seguimiento ?: "-"
                            binding.tvFechaEstimada.text = res.fecha_estimada_entrega ?: "-"
                        }
                    } catch (_: Exception) { }
                }
                d.dismiss()
            }
            d.show()
        }
    }

    private fun formatearCLP(valor: Double): String {
        return try {
            val locale = java.util.Locale.forLanguageTag("es-CL")
            val nf = java.text.NumberFormat.getCurrencyInstance(locale)
            nf.maximumFractionDigits = 0
            nf.format(valor)
        } catch (_: Exception) { "$${"%.0f".format(valor)}" }
    }
}

class DetallesAdapter(private var items: List<DetalleVentaExtendido>) : RecyclerView.Adapter<DetallesAdapter.VH>() {
    inner class VH(val binding: ItemOrderDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemOrderDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvNombre.text = item.nombre_producto ?: "Producto"
        holder.binding.tvCantidadPrecio.text = "${item.cantidad} x ${formatearCLP(item.precio_unitario)}"
        holder.binding.tvSubtotal.text = formatearCLP(item.subtotal)
    }

    fun update(nuevos: List<DetalleVentaExtendido>) {
        val diff = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = nuevos.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition].id == nuevos[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition] == nuevos[newItemPosition]
        })
        items = nuevos
        diff.dispatchUpdatesTo(this)
    }

    private fun formatearCLP(valor: Double): String {
        return try {
            val locale = java.util.Locale.forLanguageTag("es-CL")
            val nf = java.text.NumberFormat.getCurrencyInstance(locale)
            nf.maximumFractionDigits = 0
            nf.format(valor)
        } catch (_: Exception) { "$${"%.0f".format(valor)}" }
    }
}
