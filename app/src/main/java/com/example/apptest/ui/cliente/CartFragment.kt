package com.example.apptest.ui.cliente

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.apptest.core.network.ApiClient
import com.example.apptest.cliente.services.XanoVentaService
import com.example.apptest.cliente.services.IngresarVentaRequest
import com.example.apptest.cliente.services.IngresarVentaDetalle
import com.example.apptest.databinding.FragmentCartBinding
import com.example.apptest.databinding.ItemCartBinding
import com.example.apptest.core.storage.CartManager
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.launch

/**
 * Fragmento de Carrito para navegación inferior.
 */
class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private lateinit var cart: CartManager
    private var items: MutableList<com.example.apptest.core.storage.ItemCarrito> = mutableListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager.getInstance(requireContext())
        cart = CartManager.getInstance(requireContext())
        items = cart.obtenerItems(session.getUser()?.id).toMutableList()

        binding.recyclerCarrito.layoutManager = LinearLayoutManager(requireContext())
        val adaptador = CarritoAdapterF(items,
            onCantidadCambiada = { inventarioId, nuevaCantidad ->
                cart.actualizarCantidad(session.getUser()?.id, inventarioId, nuevaCantidad)
                refrescarDesdeStorage()
            },
            onEliminar = { inventarioId ->
                cart.eliminarItem(session.getUser()?.id, inventarioId)
                refrescarDesdeStorage()
            }
        )
        binding.recyclerCarrito.adapter = adaptador

        binding.btnPagar.setOnClickListener { iniciarPago(adaptador) }
        binding.btnHistorial.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), PurchaseHistoryActivity::class.java))
        }
        actualizarTotales()
    }

    private fun refrescarDesdeStorage() {
        val nuevos = cart.obtenerItems(session.getUser()?.id)
        items = nuevos.toMutableList()
        (binding.recyclerCarrito.adapter as? CarritoAdapterF)?.update(nuevos.toList())
        actualizarTotales()
    }

    private fun actualizarTotales() {
        val total = items.sumOf { it.subtotal }
        binding.tvTotal.text = formatearCLP(total)
    }

    private fun iniciarPago(adaptador: CarritoAdapterF) {
        val usuario = session.getUser()
        if (usuario?.id == null) {
            Toast.makeText(requireContext(), "Necesitas iniciar sesión", Toast.LENGTH_SHORT).show(); return
        }
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "Carrito vacío", Toast.LENGTH_SHORT).show(); return
        }
        binding.btnPagar.isEnabled = false
        binding.btnHistorial.isEnabled = false
        binding.pbPago.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(5000)
                val servicio = ApiClient.getRetrofit(requireContext()).create(XanoVentaService::class.java)
                val productosSrv = ApiClient.getRetrofit(requireContext()).create(com.example.apptest.user.services.XanoProducInvService::class.java)
                val reconciliados = mutableListOf<com.example.apptest.core.storage.ItemCarrito>()
                val insuficientes = mutableListOf<Pair<com.example.apptest.core.storage.ItemCarrito, Int>>()
                for (it in items) {
                    val pid = it.producto_id
                    var actualizado: com.example.apptest.user.services.XanoProducInvItem? = null
                    val invFinal = try {
                        if (pid != null) {
                            actualizado = productosSrv.obtener(pid)
                            actualizado?.inventario_id
                        } else null
                    } catch (_: Exception) { null }
                    var elegido = invFinal?.let { id -> it.copy(inventario_id = id) } ?: it
                    val stock = actualizado?.stock ?: it.stock_disponible
                    if (stock != null) {
                        if (elegido.cantidad > stock) {
                            insuficientes.add(elegido to stock)
                        }
                        val limitada = kotlin.math.max(0, kotlin.math.min(elegido.cantidad, stock))
                        elegido = elegido.copy(cantidad = limitada, stock_disponible = stock)
                    }
                    reconciliados.add(elegido)
                }
                if (insuficientes.isNotEmpty()) {
                    val usuarioId = usuario.id
                    val listaMsg = insuficientes.joinToString(separator = "\n") { (it, st) ->
                        val nombre = it.nombre_producto ?: "Producto"
                        "- ${nombre}: pedido ${it.cantidad}, stock ${st}"
                    }
                    val dialogMsg = "Algunos productos superan el stock disponible. Se ajustó la cantidad al máximo permitido:\n\n${listaMsg}"
                    // Ajustar cantidades al stock actual (o eliminar si stock 0)
                    insuficientes.forEach { (it, st) ->
                        if (st <= 0) {
                            cart.eliminarItem(usuarioId, it.inventario_id)
                        } else {
                            cart.actualizarCantidad(usuarioId, it.inventario_id, st)
                        }
                    }
                    refrescarDesdeStorage()
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Stock insuficiente")
                        .setMessage(dialogMsg)
                        .setPositiveButton("Aceptar", null)
                        .show()
                    binding.pbPago.visibility = View.GONE
                    binding.btnPagar.isEnabled = true
                    binding.btnHistorial.isEnabled = true
                    return@launch
                }
                if (reconciliados.isEmpty()) { Toast.makeText(requireContext(), "Carrito sin inventarios válidos", Toast.LENGTH_SHORT).show(); cart.limpiar(usuario.id); refrescarDesdeStorage(); return@launch }
                val tipo = when (session.getUser()?.tipo_cliente?.trim()?.lowercase()) {
                    "vip" -> "Vip"
                    "mayorista" -> "Mayorista"
                    else -> "Detalle"
                }
                val req = IngresarVentaRequest(
                    metodo_pago = "Transferencia",
                    canal = "AppMovil",
                    tipo_cliente = tipo,
                    detalles = reconciliados.map { IngresarVentaDetalle(inventario_id = it.inventario_id, cantidad = it.cantidad) }
                )
                val compuesta = try { servicio.ingresarVentaCompuesto(req) } catch (_: Exception) { null }
                val ventaId = compuesta?.venta?.id ?: try { servicio.ingresarVenta(req).id } catch (_: Exception) { -1L }
                val resumen = if (ventaId > 0) try { servicio.obtenerDetalleVenta(ventaId) } catch (_: Exception) { null } else null
                val total = resumen?.venta_info?.total_venta ?: compuesta?.venta?.total_venta ?: reconciliados.sumOf { it.subtotal }
                cart.limpiar(usuario.id); refrescarDesdeStorage()
                if (ventaId > 0) {
                    Toast.makeText(requireContext(), "Venta #${ventaId} total ${formatearCLP(total)}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Venta creada", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error pago: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.pbPago.visibility = View.GONE
                binding.btnPagar.isEnabled = true
                binding.btnHistorial.isEnabled = true
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Refrescar al volver a la pantalla por si cambiaron items desde otro flujo
        refrescarDesdeStorage()
    }
}

    class CarritoAdapterF(
    private var items: List<com.example.apptest.core.storage.ItemCarrito>,
    private val onCantidadCambiada: (Long, Int) -> Unit,
    private val onEliminar: (Long) -> Unit
) : RecyclerView.Adapter<CarritoAdapterF.VH>() {
    inner class VH(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvNombre.text = item.nombre_producto ?: "Producto"
        holder.binding.tvPrecioUnitario.text = formatearCLP(item.precio_unitario)
        holder.binding.tvSubtotal.text = formatearCLP(item.subtotal)
        // Evitar acumulación de watchers al reciclar
        // Limpiar watcher previo para evitar duplicados por reciclado
        (holder.binding.etCantidad.tag as? android.text.TextWatcher)?.let {
            holder.binding.etCantidad.removeTextChangedListener(it)
        }
        holder.binding.etCantidad.setText(item.cantidad.toString())
        val watcher = object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val nueva = s?.toString()?.toIntOrNull()
                if (nueva == null) return
                val maxStock = item.stock_disponible ?: Int.MAX_VALUE
                val ajustada = kotlin.math.min(nueva, maxStock)
                if (ajustada <= 0) {
                    onEliminar(item.inventario_id)
                } else if (ajustada != item.cantidad) {
                    onCantidadCambiada(item.inventario_id, ajustada)
                }
            }
        }
        holder.binding.etCantidad.addTextChangedListener(watcher)
        holder.binding.etCantidad.tag = watcher
        holder.binding.btnEliminar.setOnClickListener { onEliminar(item.inventario_id) }
        holder.binding.btnMenos.setOnClickListener {
            val actual = holder.binding.etCantidad.text.toString().toIntOrNull() ?: item.cantidad
            val nueva = actual - 1
            if (nueva <= 0) {
                onEliminar(item.inventario_id)
            } else {
                holder.binding.etCantidad.setText(nueva.toString())
            }
        }
        holder.binding.btnMas.setOnClickListener {
            val actual = holder.binding.etCantidad.text.toString().toIntOrNull() ?: item.cantidad
            val maxStock = item.stock_disponible ?: Int.MAX_VALUE
            val nueva = kotlin.math.min(actual + 1, maxStock)
            holder.binding.etCantidad.setText(nueva.toString())
        }
    }
    fun update(nuevos: List<com.example.apptest.core.storage.ItemCarrito>) {
        val diff = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = nuevos.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition].inventario_id == nuevos[newItemPosition].inventario_id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = items[oldItemPosition] == nuevos[newItemPosition]
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
