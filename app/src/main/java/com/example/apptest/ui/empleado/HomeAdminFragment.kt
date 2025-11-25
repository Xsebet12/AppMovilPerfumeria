package com.example.apptest.ui.empleado

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.example.apptest.R
import com.example.apptest.user.services.XanoCatalogoCompletoService
import com.example.apptest.core.network.ApiClient
import com.example.apptest.user.services.XanoProducInvItem
import com.example.apptest.user.services.CatalogoProducto
import com.example.apptest.core.storage.CatalogCache
import com.example.apptest.databinding.FragmentHomeAdminBinding
import com.example.apptest.core.storage.SessionManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Home para administradores/empleados.
 * Igual que el Home de cliente, pero muestra un encabezado "Panel Admin"
 * y un botón flotante para crear producto.
 */
class HomeAdminFragment : Fragment() {
    private var _binding: FragmentHomeAdminBinding? = null
    private val binding get() = _binding!!

    private val productos = mutableListOf<XanoProducInvItem>()
    private val todosProductos = mutableListOf<XanoProducInvItem>()
    private var busquedaActual: String = ""
    private var observadorBusqueda: TextWatcher? = null
    private var cargando = false

    private val intervaloRefrescoMs = 30_000L
    private var scrollActivo = false

    private val adapter by lazy { AdminProductAdapter { product ->
        val bundle = Bundle().apply { putLong("product_id", product.id) }
        // Navega al detalle admin (fragmento a crear en nav_graph)
        findNavController().navigate(R.id.adminProductDetailFragment, bundle)
    } }

    private var tipoClienteActual: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentHomeAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter

        // Restaura búsqueda
        val sp = requireContext().getSharedPreferences("preferencias", android.content.Context.MODE_PRIVATE)
        val claveBusqueda = "busqueda_actual"
        binding.etSearch.setText(sp.getString(claveBusqueda, "") ?: "")
        busquedaActual = binding.etSearch.text?.toString()?.trim().orEmpty()

        observadorBusqueda = object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                busquedaActual = s?.toString()?.trim().orEmpty()
                sp.edit().putString(claveBusqueda, busquedaActual).apply()
                aplicarFiltro()
            }
        }
        binding.etSearch.addTextChangedListener(observadorBusqueda)


        // Encabezado Admin
        binding.tvHeader.text = "Panel Admin"

        // Spinner de orden
        val opcionesOrden = listOf(
            "marca" to "Marca",
            "categoria" to "Categoría",
            "nombre" to "Nombre",
            "precio_asc" to "Precio ↑",
            "precio_desc" to "Precio ↓",
            "habilitado" to "Habilitado"
        )
        val adapterOrden = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            opcionesOrden.map { it.second }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spOrden.adapter = adapterOrden
        val prefs = requireContext().getSharedPreferences("preferencias", android.content.Context.MODE_PRIVATE)
        val claveOrden = "orden_lista_admin"
        var ordenActual = prefs.getString(claveOrden, "marca") ?: "marca"
        val idxInicial = opcionesOrden.indexOfFirst { it.first == ordenActual }.let { if (it == -1) 0 else it }
        binding.spOrden.setSelection(idxInicial)
        binding.spOrden.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                ordenActual = opcionesOrden[position].first
                prefs.edit().putString(claveOrden, ordenActual).apply()
                reordenarYRefrescar(ordenActual)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // FAB para crear producto
        binding.fabCrearProducto.setOnClickListener {
            startActivity(Intent(requireContext(), CreateProductActivity::class.java))
        }

        cargarProductos(true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (isActive) {
                    kotlinx.coroutines.delay(intervaloRefrescoMs)
                    if (!cargando && busquedaActual.isBlank() && !scrollActivo) cargarProductos(true)
                }
            }
        }
    }

    private fun cargarProductos(reset: Boolean = false) {
        if (cargando) return
        if (reset) { todosProductos.clear(); productos.clear(); adapter.submitList(emptyList()) }
        cargando = true
        val servicioCatalogo = ApiClient.getRetrofit(requireContext()).create(XanoCatalogoCompletoService::class.java)
        tipoClienteActual = SessionManager.getInstance(requireContext()).getUser()?.tipo_cliente?.lowercase()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.pbHomeAdmin?.visibility = android.view.View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val catalogo = kotlinx.coroutines.withTimeoutOrNull(15000) { servicioCatalogo.obtener() }
                val listaCatalogo = catalogo?.productos ?: emptyList()
                if (catalogo != null) CatalogCache.set(catalogo.categorias, catalogo.marcas)
                val lista = listaCatalogo.mapNotNull { mapCatalogoProducto(it) }
                todosProductos.clear(); todosProductos.addAll(lista)
                val filtrada = if (busquedaActual.isBlank()) lista else filtrarLista(lista, busquedaActual)
                productos.clear(); productos.addAll(filtrada)
                val orden = requireContext().getSharedPreferences("preferencias", android.content.Context.MODE_PRIVATE).getString("orden_lista_admin", "marca") ?: "marca"
                reordenarYRefrescar(orden)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Silenciar cancelaciones normales
                } else {
                    android.widget.Toast.makeText(requireContext(), "Error cargando productos: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally { cargando = false; binding.pbHomeAdmin?.visibility = android.view.View.GONE }
        }

        binding.recyclerView.addOnScrollListener(object: androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                scrollActivo = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })
    }

    private fun mapCatalogoProducto(cp: CatalogoProducto): XanoProducInvItem? {
        val id = cp.id_producto ?: return null
        return XanoProducInvItem(
            id = id,
            inventario_id = cp.id_inventario,
            created_at = null,
            sku = cp.sku,
            nombre_producto = cp.nombre,
            descripcion = cp.descripcion,
            ml = cp.ml,
            presentacion = cp.presentacion,
            categoria_id = cp.categoria_id,
            marca_id = cp.marca_id,
            nombre_categoria = cp.nom_categoria,
            nombre_marca = cp.nom_marca,
            stock = cp.stock,
            disponible = cp.disponible,
            habilitado = cp.habilitado,
            precio_detalle = cp.precio_detalle,
            precio_vip = cp.precio_vip,
            precio_mayorista = cp.precio_mayorista,
            costo_referencia = cp.costo,
            url_imagen_principal = cp.url_imagen?.url,
            url_imagen_extras = emptyList()
        )
    }

    private fun aplicarFiltro() {
        val q = busquedaActual
        val filtrada = if (q.isBlank()) todosProductos else filtrarLista(todosProductos, q)
        productos.clear(); productos.addAll(filtrada)
        adapter.submitList(productos.toList())
    }

    private fun filtrarLista(source: List<XanoProducInvItem>, query: String): List<XanoProducInvItem> {
        val q = query.lowercase()
        return source.filter { p ->
            p.nombre_producto.lowercase().contains(q) ||
            (p.descripcion?.lowercase()?.contains(q) == true) ||
            (p.nombre_marca?.lowercase()?.contains(q) == true) ||
            (p.nombre_categoria?.lowercase()?.contains(q) == true)
        }
    }

    // etiquetaOrden ya no se utiliza; orden mostrado directamente en spinner

    private fun precioDinamico(p: XanoProducInvItem): Double? = when (tipoClienteActual) {
        "vip" -> p.precio_vip ?: p.precio_detalle
        "mayorista" -> p.precio_mayorista ?: p.precio_detalle
        else -> p.precio_detalle
    }

    private fun reordenarYRefrescar(orden: String) {
        fun comp(): Comparator<XanoProducInvItem> = when (orden) {
            "precio_asc" -> compareBy<XanoProducInvItem> { precioDinamico(it) ?: Double.MAX_VALUE }
            "precio_desc" -> compareByDescending<XanoProducInvItem> { precioDinamico(it) ?: Double.MIN_VALUE }
            "nombre" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre_producto }
            "categoria" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre_categoria ?: "" }
            "habilitado" -> compareByDescending<XanoProducInvItem> { (it.habilitado == true).compareTo(false) }
            else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre_marca ?: "" }
        }
        val c = comp()
        todosProductos.sortWith(c)
        productos.sortWith(c)
        adapter.submitList(productos.toList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        observadorBusqueda?.let { binding.etSearch.removeTextChangedListener(it) }
        observadorBusqueda = null
        _binding = null
    }
}
