package com.example.apptest.ui.empleado

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.apptest.R
import com.example.apptest.empleado.models.XanoEmpleado

class EmpleadosAdapter(
    private var items: List<XanoEmpleado>,
    private val onDetalle: (XanoEmpleado) -> Unit
): RecyclerView.Adapter<EmpleadosAdapter.VH>() {

    fun submit(list: List<XanoEmpleado>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cliente, parent, false)
        return VH(v)
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View): RecyclerView.ViewHolder(view) {
        private val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        private val tvEmail: TextView = view.findViewById(R.id.tvEmail)
        private val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        fun bind(e: XanoEmpleado) {
            val nombre = listOfNotNull(e.primer_nombre, e.segundo_nombre, e.apellido_paterno, e.apellido_materno).joinToString(" ").trim()
            tvNombre.text = if (nombre.isNotBlank()) nombre else "(Sin nombre)"
            tvEmail.text = e.email_contacto ?: "-"
            tvEstado.text = if (e.habilitado == true) "Habilitado" else "Bloqueado"
            itemView.setOnClickListener { onDetalle(e) }
        }
    }
}

