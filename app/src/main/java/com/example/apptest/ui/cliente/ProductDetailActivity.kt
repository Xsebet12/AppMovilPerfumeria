package com.example.apptest.ui.cliente

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.apptest.BuildConfig
import com.example.apptest.R
import com.example.apptest.user.services.XanoProducInvItem
import com.example.apptest.user.services.XanoProducInvService
import com.example.apptest.core.network.ApiClient
import com.example.apptest.databinding.ActivityProductDetailBinding
import com.example.apptest.core.storage.CartManager
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ProductDetailActivity : ComponentActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var productId: Long = -1L
    private var itemDetalle: XanoProducInvItem? = null
    private lateinit var cartManager: CartManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.progresoDetalle.visibility = android.view.View.VISIBLE

        productId = intent.getLongExtra("product_id", -1)
        if (productId == -1L) { finish(); return }

        MainScope().launch {
            try {
                val servicio = ApiClient.getRetrofit(this@ProductDetailActivity).create(XanoProducInvService::class.java)
                val combinado = servicio.obtener(productId)
                if (combinado == null) {
                    mostrarError(Exception("Producto no encontrado"))
                } else {
                    itemDetalle = combinado
                    bindFull(combinado)
                }
            } catch (e: Exception) {
                mostrarError(e)
            } finally {
        binding.progresoDetalle.visibility = android.view.View.GONE
        }
        }

        cartManager = CartManager.getInstance(this)
        binding.btnAgregarCarrito.setOnClickListener { agregarAlCarrito() }

        // Vista cliente: opción modificar removida (solo admin en fragmento dedicado)
    }

    private fun bindFull(item: XanoProducInvItem) {
        binding.tvName.text = item.nombre_producto
        binding.tvSku.text = item.sku?.let { "SKU: $it" } ?: "SKU: N/D"
        binding.tvMarca.text = item.nombre_marca?.let { "Marca: $it" } ?: ""
        binding.tvCategoria.text = item.nombre_categoria?.let { "Categoría: $it" } ?: ""
        val descCompuesta = buildString {
            if (!item.descripcion.isNullOrBlank()) append(item.descripcion)
            if (item.ml != null) { if (isNotEmpty()) append(" • "); append("${item.ml} ml") }
            if (!item.presentacion.isNullOrBlank()) { if (isNotEmpty()) append(" • "); append(item.presentacion) }
        }
        binding.tvDescripcion.text = if (descCompuesta.isBlank()) "Sin descripción" else descCompuesta
        binding.tvPresentacion.text = item.presentacion?.let { "Presentación: $it" } ?: ""
        binding.tvMl.text = item.ml?.let { "Contenido: ${it} ml" } ?: ""
        binding.tvStock.text = item.stock?.let { "Stock: $it" } ?: "Stock no disponible"

        val tipoCliente = SessionManager.getInstance(applicationContext).getUser()?.tipo_cliente?.lowercase()
        val etiqueta = when (tipoCliente) {
            "vip" -> "Precio VIP"
            "mayorista" -> "Precio Mayorista"
            else -> "Precio Detalle"
        }
        binding.tvTipoPrecio.text = etiqueta
        val precio = when (tipoCliente) {
            "vip" -> item.precio_vip ?: item.precio_detalle
            "mayorista" -> item.precio_mayorista ?: item.precio_detalle
            else -> item.precio_detalle
        }
        binding.tvPrecioValor.text = precio?.let { formatearCLP(it) } ?: "--"
        binding.tvPrecioReferencia.text = item.costo_referencia?.let { "Costo referencia: ${formatearCLP(it)}" } ?: ""

        val stockMax = item.stock ?: 0
        binding.etCantidad.setText("1")
        if (stockMax <= 0) {
            binding.btnAgregarCarrito.isEnabled = false
        }
        val watcher = object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = s?.toString()?.toIntOrNull() ?: 1
                val clamped = kotlin.math.max(1, kotlin.math.min(v, stockMax))
                val t = binding.etCantidad.text?.toString()?.toIntOrNull() ?: 1
                if (clamped != t) binding.etCantidad.setText(clamped.toString())
            }
        }
        binding.etCantidad.addTextChangedListener(watcher)

        val urls: List<String> = buildList {
            if (!item.url_imagen_principal.isNullOrBlank()) add(normalizarUrl(item.url_imagen_principal))
            (item.url_imagen_extras ?: emptyList())
                .filter { !it.isNullOrBlank() }
                .forEach { add(normalizarUrl(it)) }
        }
        if (urls.isNotEmpty()) {
            binding.viewPager.adapter = object: androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun getItemCount(): Int = urls.size
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    val vb = com.example.apptest.databinding.ItemImageBinding.inflate(layoutInflater, parent, false)
                    return object: androidx.recyclerview.widget.RecyclerView.ViewHolder(vb.root) {}
                }
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                    val vb = com.example.apptest.databinding.ItemImageBinding.bind(holder.itemView)
                    vb.imageView.load(urls[position]) {
                        crossfade(true)
                        placeholder(R.drawable.ic_product_placeholder)
                        error(R.drawable.ic_product_placeholder)
                        fallback(R.drawable.ic_product_placeholder)
                    }
                    if (position == 0) vb.imageView.contentDescription = "Imagen principal"
                }
            }
        } else {
            binding.viewPager.adapter = object: androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun getItemCount(): Int = 1
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    val vb = com.example.apptest.databinding.ItemImageBinding.inflate(layoutInflater, parent, false)
                    return object: androidx.recyclerview.widget.RecyclerView.ViewHolder(vb.root) {}
                }
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                    val vb = com.example.apptest.databinding.ItemImageBinding.bind(holder.itemView)
                    vb.imageView.load(null as String?) {
                        placeholder(R.drawable.ic_product_placeholder)
                        error(R.drawable.ic_product_placeholder)
                        fallback(R.drawable.ic_product_placeholder)
                    }
                }
            }
        }
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
    }

    private fun normalizarUrl(ruta: String): String {
        val r = ruta.trim()
        if (r.startsWith("http", true)) {
            return try {
                val uri = java.net.URI(r)
                val host = uri.host ?: ""
                if (host == "localhost" || host == "127.0.0.1") {
                    val base = BuildConfig.XANO_BASE_URL.trimEnd('/')
                    val baseUri = java.net.URI(base)
                    val esquema = baseUri.scheme ?: uri.scheme
                    val nuevoHost = baseUri.host ?: host
                    val nuevoPuerto = if (baseUri.port == -1) uri.port else baseUri.port
                    java.net.URI(esquema, null, nuevoHost, nuevoPuerto, uri.path, uri.query, uri.fragment).toString()
                } else r
            } catch (_: Exception) { r }
        }
        val rel = r.trimStart('/')
        return BuildConfig.XANO_BASE_URL.trimEnd('/') + "/" + rel
    }

    private fun formatearCLP(valor: Double?): String {
        if (valor == null) return "--"
        return try {
            val nf = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("es-CL"))
            nf.maximumFractionDigits = 0
            nf.format(valor)
        } catch (_: Exception) { "$${"%.0f".format(valor)}" }
    }

    private fun mostrarError(e: Exception) {
        val msg = when (e) {
            is HttpException -> "Error ${e.code()} al cargar detalle"
            is java.util.concurrent.TimeoutException -> "Timeout al cargar producto"
            else -> e.message ?: "Error desconocido"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun agregarAlCarrito() {
        MainScope().launch {
            val usuario = SessionManager.getInstance(applicationContext).getUser()
            val tipoCliente = usuario?.tipo_cliente?.lowercase()
            val detalle = itemDetalle
            val precio = when (tipoCliente) {
                "vip" -> detalle?.precio_vip ?: detalle?.precio_detalle
                "mayorista" -> detalle?.precio_mayorista ?: detalle?.precio_detalle
                else -> detalle?.precio_detalle
            }
            if (precio == null) { Toast.makeText(this@ProductDetailActivity, "Precio no disponible", Toast.LENGTH_SHORT).show(); return@launch }
            val cantidadIngresada = binding.etCantidad.text?.toString()?.toIntOrNull() ?: 1
            val stock = detalle?.stock ?: 0
            if (cantidadIngresada > stock) {
                Toast.makeText(this@ProductDetailActivity, "Stock insuficiente: pedido ${cantidadIngresada}, stock ${stock}", Toast.LENGTH_SHORT).show(); return@launch
            }
            var inventarioId = detalle?.inventario_id
            if (inventarioId == null) {
                try {
                    val srv = ApiClient.getRetrofit(this@ProductDetailActivity).create(XanoProducInvService::class.java)
                    val actualizado = srv.obtener(productId)
                    inventarioId = actualizado?.inventario_id
                } catch (_: Exception) { }
            }
            if (inventarioId == null) { Toast.makeText(this@ProductDetailActivity, "Inventario no disponible para este producto", Toast.LENGTH_SHORT).show(); return@launch }
            val item = com.example.apptest.core.storage.ItemCarrito(
                inventario_id = inventarioId!!,
                producto_id = productId,
                nombre_producto = binding.tvName.text?.toString(),
                cantidad = cantidadIngresada,
                precio_unitario = precio,
                costo_referencia = null,
                stock_disponible = detalle?.stock
            )
            cartManager.agregarItem(usuario?.id, item)
            Toast.makeText(this@ProductDetailActivity, "Agregado al carrito", Toast.LENGTH_SHORT).show()
        }
    }
}
