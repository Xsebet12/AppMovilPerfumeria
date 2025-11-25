package com.example.apptest.ui.empleado

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.apptest.core.network.ApiClient
import com.example.apptest.cliente.services.XanoVentaService
import com.example.apptest.cliente.services.XanoSeguimientoPedidoService
import com.example.apptest.cliente.services.VentaInfo
import com.example.apptest.databinding.FragmentManageOrdersBinding
import com.example.apptest.ui.cliente.OrderSummaryActivity
import kotlinx.coroutines.launch

class ManageOrdersFragment : Fragment() {
    private var _binding: FragmentManageOrdersBinding? = null
    private val binding get() = _binding!!

    private val items = mutableListOf<VentaInfo>()
    private val adapter by lazy {
        AdminOrdersAdapter(
            items,
            onVerResumen = { id -> abrirResumen(id) },
            onAceptar = { id -> aceptarPago(id) },
            onMarcarEnviado = { id -> marcarEnviado(id) },
            onMarcarEntregado = { id -> marcarEntregado(id) },
            onRechazar = { id -> rechazarPago(id) }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentManageOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerOrdenes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOrdenes.adapter = adapter

        val estados = resources.getStringArray(com.example.apptest.R.array.estado_pago_filtro)
        val spAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, estados).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spFiltroEstado.adapter = spAdapter
        binding.spFiltroEstado.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                cargarPorEstado(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        binding.spFiltroEstado.setSelection(0, false)
        cargarPorEstado(0)
    }

    private fun cargarPorEstado(idx: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressOrdenes.visibility = View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val servicio = ApiClient.getRetrofit(requireContext()).create(XanoVentaService::class.java)
                val estado = when (idx) {
                    1 -> "aceptado"
                    2 -> "rechazado"
                    3 -> "despachado"
                    4 -> "entregado"
                    else -> "pendiente"
                }
                val lista = servicio.ventasPorEstado(estado)
                items.clear()
                items.addAll(lista)
                adapter.update(items.toList())
                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                items.clear(); adapter.update(items)
            } finally {
                binding.progressOrdenes.visibility = View.GONE
            }
        }
    }

    private fun abrirResumen(id: Long) {
        val i = Intent(requireContext(), OrderSummaryActivity::class.java)
        i.putExtra("venta_id", id)
        startActivity(i)
    }

    private fun aceptarPago(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressOrdenes.visibility = View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val servicio = ApiClient.getRetrofit(requireContext()).create(XanoVentaService::class.java)
                val res = servicio.aceptar(id, mapOf("venta_id" to id))
                Toast.makeText(requireContext(), "Pago aceptado #${res.id}", Toast.LENGTH_SHORT).show()
                cargarPorEstado(binding.spFiltroEstado.selectedItemPosition)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error aceptar: ${e.message}", Toast.LENGTH_SHORT).show()
                adapter.resetLoadingStates()
            } finally { binding.progressOrdenes.visibility = View.GONE }
        }
    }

    private fun rechazarPago(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressOrdenes.visibility = View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val servicio = ApiClient.getRetrofit(requireContext()).create(XanoVentaService::class.java)
                val res = servicio.rechazar(id, mapOf("id" to id))
                Toast.makeText(requireContext(), "Pago rechazado #${res.id}", Toast.LENGTH_SHORT).show()
                cargarPorEstado(binding.spFiltroEstado.selectedItemPosition)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error rechazar: ${e.message}", Toast.LENGTH_SHORT).show()
                adapter.resetLoadingStates()
            } finally { binding.progressOrdenes.visibility = View.GONE }
        }
    }

    private fun marcarEnviado(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressOrdenes.visibility = View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val servicio = ApiClient.getRetrofit(requireContext()).create(XanoVentaService::class.java)
                val res = servicio.marcarDespachado(id)
                Toast.makeText(requireContext(), "Despachado #${res.venta_id}", Toast.LENGTH_SHORT).show()
                cargarPorEstado(binding.spFiltroEstado.selectedItemPosition)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                adapter.resetLoadingStates()
            } finally { binding.progressOrdenes.visibility = View.GONE }
        }
    }

    private fun marcarEntregado(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressOrdenes.visibility = View.VISIBLE
                kotlinx.coroutines.delay(2000)
                val servicio = ApiClient.getRetrofit(requireContext()).create(XanoVentaService::class.java)
                val res = servicio.marcarEntregado(id)
                Toast.makeText(requireContext(), "Entregado #${res.venta_id}", Toast.LENGTH_SHORT).show()
                cargarPorEstado(binding.spFiltroEstado.selectedItemPosition)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error entregado: ${e.message}", Toast.LENGTH_SHORT).show()
                adapter.resetLoadingStates()
            } finally { binding.progressOrdenes.visibility = View.GONE }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
