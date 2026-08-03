package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class HomeRuleAdapter : RecyclerView.Adapter<HomeRuleAdapter.VH>() {
    private val items = mutableListOf<ContextManagerEntry>()

    fun submit(list: List<ContextManagerEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_rule, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.apps.text = item.name.ifBlank { "전체 앱" }
        holder.title.text = when {
            item.content.isNotBlank() -> "${item.content} 관련 알림"
            item.exceptions.isNotBlank() -> "예외 제외 규칙"
            else -> "알림 규칙 #${item.id}"
        }
        holder.progress.progress = if (position % 2 == 0) 70 else 45
        val bg = if (position % 2 == 0) R.drawable.bg_rule_card_blue else R.drawable.bg_rule_card_orange
        val tint = if (position % 2 == 0) R.color.figma_blue else R.color.figma_orange
        holder.itemView.setBackgroundResource(bg)
        holder.progress.progressTintList = ContextCompat.getColorStateList(ctx, tint)
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val apps: TextView = view.findViewById(R.id.ruleApps)
        val title: TextView = view.findViewById(R.id.ruleTitle)
        val progress: ProgressBar = view.findViewById(R.id.ruleProgress)
    }
}
