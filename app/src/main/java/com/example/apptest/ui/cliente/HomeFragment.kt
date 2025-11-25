package com.example.apptest.ui.cliente

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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.example.apptest.user.services.XanoProducInvService
import com.example.apptest.user.services.XanoProducInvItem
import com.example.apptest.core.network.ApiClient
import com.example.apptest.core.storage.CartManager
import com.example.apptest.core.storage.SessionManager
import com.example.apptest.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val productos = mutableListOf<XanoProducInvItem>()
    // Mantiene todos los productos cargados para poder filtrar sin perder datos
    private val todosProductos = mutableListOf<XanoProducInvItem>()
    private var busquedaActual: String = ""
    private var observadorBusqueda: TextWatcher? = null
    // Estado de carga
    private var cargando = false
    private var cargaInicialHecha = false
    // Preferencias de orden para la lista
    private val nombrePrefs = "preferencias"
    private val claveOrden = "orden_lista_cliente"
    private val opcionesOrden = listOf(
        "marca" to "Marca",
        "categoria" to "Categoría",
        "nombre" to "Nombre",
        "precio_asc" to "Precio ↑",
        "precio_desc" to "Precio ↓"
    )
    private var ordenActual = "marca"

    // --- Refresco periódico ---
    private val intervaloRefrescoMs = 60_000L // 1 minuto

    // Flag para evitar refrescar mientras el usuario hace scroll rápido
    private var scrollActivo = false

    private val adapter by lazy { com.example.apptest.ui.comun.ProductAdapter(productos) { product ->
        val i = Intent(requireContext(), ProductDetailActivity::class.java)
        i.putExtra("product_id", product.id)
        startActivity(i)
    } }

    private var tipoClienteActual: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter

        // Cargar orden guardado
        val sp = requireContext().getSharedPreferences(nombrePrefs, android.content.Context.MODE_PRIVATE)
        ordenActual = sp.getString(claveOrden, ordenActual) ?: ordenActual
        // Spinner orden
        val adapterOrden = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opcionesOrden.map { it.second }).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spOrden.adapter = adapterOrden
        ordenActual = sp.getString(claveOrden, ordenActual) ?: ordenActual
        val idxInicial = opcionesOrden.indexOfFirst { it.first == ordenActual }.let { if (it == -1) 0 else it }
        binding.spOrden.setSelection(idxInicial)
        binding.spOrden.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val nuevoOrden = opcionesOrden[position].first
                if (nuevoOrden != ordenActual) {
                    ordenActual = nuevoOrden
                    sp.edit().putString(claveOrden, ordenActual).apply()
                    reordenarYRefrescar()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Restaurar búsqueda previa (SharedPreferences)
        val claveBusqueda = "busqueda_actual"
        binding.etSearch.setText(sp.getString(claveBusqueda, "") ?: "")
        busquedaActual = binding.etSearch.text?.toString()?.trim().orEmpty()

        // Configurar búsqueda por texto y guardar en preferencias
        observadorBusqueda = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                busquedaActual = s?.toString()?.trim().orEmpty()
                sp.edit().putString(claveBusqueda, busquedaActual).apply()
                aplicarFiltro()
            }
        }
        binding.etSearch.addTextChangedListener(observadorBusqueda)
        if (!cargaInicialHecha) {
            cargarProductos(reset = true)
            cargaInicialHecha = true
        }

        binding.recyclerView.addOnScrollListener(object: androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                scrollActivo = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (isActive) {
                    kotlinx.coroutines.delay(intervaloRefrescoMs)
                    if (!cargando && !scrollActivo && busquedaActual.isBlank()) {
                        cargarProductos(reset = true)
                    }
                }
            }
        }
    }

    // Eliminado cache legacy: ahora siempre usamos endpoint combinado directamente.

    private fun cargarProductos(reset: Boolean = false) {
        if (cargando) return
        if (reset) {
            todosProductos.clear()
            productos.clear()
            adapter.notifyDataSetChanged()
        }
        cargando = true
        val retrofit = ApiClient.getRetrofit(requireContext())
        val servicioCombinado = retrofit.create(XanoProducInvService::class.java)
        val sesion = SessionManager.getInstance(requireContext())
        tipoClienteActual = sesion.getUser()?.tipo_cliente?.lowercase()
        lifecycleScope.launch {
            try {
                _binding?.pbHome?.visibility = android.view.View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val listaFull = kotlinx.coroutines.withTimeoutOrNull(12000) {
                    servicioCombinado.listar()
                } ?: emptyList()
                todosProductos.clear()
                todosProductos.addAll(listaFull)
                productos.clear()
                if (busquedaActual.isBlank()) {
                    productos.addAll(listaFull)
                } else {
                    productos.addAll(filtrarLista(listaFull, busquedaActual))
                }
                reordenarYRefrescar()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Cancelación normal (fragment destruido o reinicio de refresco); no mostrar error.
                } else {
                    android.widget.Toast.makeText(requireContext(), "Error cargando productos: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                cargando = false
                _binding?.pbHome?.visibility = android.view.View.GONE
            }
        }
    }

    private fun aplicarFiltro() {
        val q = busquedaActual
        productos.clear()
        if (q.isBlank()) {
            productos.addAll(todosProductos)
        } else {
            productos.addAll(filtrarLista(todosProductos, q))
        }
        adapter.notifyDataSetChanged()
    }

    private fun filtrarLista(source: List<XanoProducInvItem>, query: String): List<XanoProducInvItem> {
        if (query.isBlank()) return source
        val q = query.lowercase()
        return source.filter { p ->
            p.nombre_producto.lowercase().contains(q) ||
            (p.descripcion?.lowercase()?.contains(q) == true) ||
            (p.nombre_marca?.lowercase()?.contains(q) == true) ||
            (p.nombre_categoria?.lowercase()?.contains(q) == true)
        }
    }

    // Reordena listas en memoria y refresca el adapter
    private fun precioDinamico(p: XanoProducInvItem): Double? = when (tipoClienteActual) {
        "vip" -> p.precio_vip ?: p.precio_detalle
        "mayorista" -> p.precio_mayorista ?: p.precio_detalle
        else -> p.precio_detalle
    }

    private fun reordenarYRefrescar() {
        fun ordenProductoComparator(): Comparator<XanoProducInvItem> = when (ordenActual) {
            "precio_asc" -> compareBy<XanoProducInvItem> { precioDinamico(it) ?: Double.MAX_VALUE }
            "precio_desc" -> compareByDescending<XanoProducInvItem> { precioDinamico(it) ?: Double.MIN_VALUE }
            "nombre" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre_producto }
            "categoria" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre_categoria ?: "" }
            else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre_marca ?: "" } // marca (default)
        }
        val comp = ordenProductoComparator()
        todosProductos.sortWith(comp)
        productos.sortWith(comp)
        adapter.notifyDataSetChanged()
    }

    // Etiqueta para el UI
    // Etiquetas ahora se manejan vía Spinner, función no requerida


    

    override fun onDestroyView() {
        super.onDestroyView()
        observadorBusqueda?.let { binding.etSearch.removeTextChangedListener(it) }
        observadorBusqueda = null
        _binding = null
    }
}
