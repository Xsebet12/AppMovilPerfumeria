package com.example.apptest.ui.empleado

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.apptest.cliente.services.VentaInfo
import com.example.apptest.databinding.ItemAdminOrderBinding

class AdminOrdersAdapter(
    private var items: List<VentaInfo>,
    private val onVerResumen: (Long) -> Unit,
    private val onAceptar: (Long) -> Unit,
    private val onMarcarEnviado: (Long) -> Unit,
    private val onMarcarEntregado: (Long) -> Unit,
    private val onRechazar: (Long) -> Unit
) : RecyclerView.Adapter<AdminOrdersAdapter.VH>() {

    private var bindingRecyclerView: RecyclerView? = null

    inner class VH(val binding: ItemAdminOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAdminOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        bindingRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        bindingRecyclerView = null
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvOrdenTitulo.text = "Orden #${item.id}"
        holder.binding.tvEstadoPago.text = item.estado_pago ?: "pendiente"
        holder.binding.tvCliente.text = "Cliente: ${item.cliente_id ?: "-"}"
        holder.binding.tvFecha.text = item.fecha?.let { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it)) } ?: ""
        holder.binding.tvTotal.text = item.total_venta?.let { formatearCLP(it) } ?: "$0"
        holder.binding.tvEstadoEnvio.text = item.estado_envio ?: "-"
        val pago = item.estado_pago ?: ""
        val envio = item.estado_envio ?: ""
        
        // Debug: Verificar valores
        android.util.Log.d("AdminOrdersAdapter", "Orden #${item.id} - Estado pago: '$pago', Estado envio: '$envio'")
        
        holder.binding.btnAceptar.visibility = if (pago == "pendiente") android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.btnRechazar.visibility = if (pago == "pendiente") android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.btnMarcarEnviado.visibility = if (pago == "aceptado" && (envio.isBlank() || envio == "preparando")) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.btnMarcarEntregado.visibility = if (envio == "despachado") android.view.View.VISIBLE else android.view.View.GONE
        
        // Debug: Verificar visibilidad del botón rechazar
        val rechazarVisible = if (pago == "pendiente") "VISIBLE" else "GONE"
        android.util.Log.d("AdminOrdersAdapter", "Botón rechazar visibilidad: $rechazarVisible para orden #${item.id}")
        
        if (pago == "rechazado" || envio == "entregado") {
            holder.binding.btnAceptar.visibility = android.view.View.GONE
            holder.binding.btnRechazar.visibility = android.view.View.GONE
            holder.binding.btnMarcarEnviado.visibility = android.view.View.GONE
            holder.binding.btnMarcarEntregado.visibility = android.view.View.GONE
        }
        holder.binding.btnVerResumen.setOnClickListener { onVerResumen(item.id) }
        holder.binding.btnAceptar.setOnClickListener {
            holder.binding.pbAccion.visibility = android.view.View.VISIBLE
            holder.binding.btnAceptar.isEnabled = false
            holder.binding.btnRechazar.isEnabled = false
            holder.binding.btnMarcarEnviado.isEnabled = false
            holder.binding.btnMarcarEntregado.isEnabled = false
            onAceptar(item.id)
        }
        holder.binding.btnMarcarEnviado.setOnClickListener {
            holder.binding.pbAccion.visibility = android.view.View.VISIBLE
            holder.binding.btnAceptar.isEnabled = false
            holder.binding.btnRechazar.isEnabled = false
            holder.binding.btnMarcarEnviado.isEnabled = false
            holder.binding.btnMarcarEntregado.isEnabled = false
            onMarcarEnviado(item.id)
        }
        holder.binding.btnMarcarEntregado.setOnClickListener {
            holder.binding.pbAccion.visibility = android.view.View.VISIBLE
            holder.binding.btnAceptar.isEnabled = false
            holder.binding.btnRechazar.isEnabled = false
            holder.binding.btnMarcarEnviado.isEnabled = false
            holder.binding.btnMarcarEntregado.isEnabled = false
            onMarcarEntregado(item.id)
        }
        holder.binding.btnRechazar.setOnClickListener {
            holder.binding.pbAccion.visibility = android.view.View.VISIBLE
            holder.binding.btnAceptar.isEnabled = false
            holder.binding.btnRechazar.isEnabled = false
            holder.binding.btnMarcarEnviado.isEnabled = false
            holder.binding.btnMarcarEntregado.isEnabled = false
            onRechazar(item.id)
        }
    }

    fun update(nuevos: List<VentaInfo>) {
        // Resetear todos los estados de loading antes de actualizar
        resetLoadingStates()
        
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

    fun resetLoadingStates() {
        for (i in 0 until itemCount) {
            val holder = bindingRecyclerView?.findViewHolderForAdapterPosition(i) as? VH
            holder?.let {
                it.binding.pbAccion.visibility = android.view.View.GONE
                it.binding.btnAceptar.isEnabled = true
                it.binding.btnRechazar.isEnabled = true
                it.binding.btnMarcarEnviado.isEnabled = true
                it.binding.btnMarcarEntregado.isEnabled = true
            }
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