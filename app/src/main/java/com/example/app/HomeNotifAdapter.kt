package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HomeNotifAdapter : RecyclerView.Adapter<HomeNotifAdapter.VH>() {
    private val items = mutableListOf<NotificationEntry>()

    fun submit(list: List<NotificationEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_notif, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val shortPkg = item.packageName.substringAfterLast('.')
        holder.icon.text = shortPkg.take(1).uppercase()
        holder.title.text = "${shortPkg.ifBlank { "앱" }} · ${item.title.ifBlank { "(제목 없음)" }}"
        holder.body.text = item.text.ifBlank { "(내용 없음)" }
        holder.status.text = "지연됨"
        holder.status.setBackgroundResource(R.drawable.bg_status_sent)
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.notifIcon)
        val title: TextView = view.findViewById(R.id.notifTitle)
        val body: TextView = view.findViewById(R.id.notifBody)
        val status: TextView = view.findViewById(R.id.notifStatus)
    }
}
