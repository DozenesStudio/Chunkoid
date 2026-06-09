package com.dozenesstudio.chunkoid.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dozenesstudio.chunkoid.R
import com.dozenesstudio.chunkoid.model.LogEntry

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<LogEntry>()

    fun addLog(entry: LogEntry) {
        logs.add(entry)
        notifyItemInserted(logs.size - 1)
    }

    fun clear() {
        logs.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLevel: TextView = itemView.findViewById(R.id.tv_log_level)
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_log_message)

        fun bind(entry: LogEntry) {
            tvLevel.text = entry.level.name
            tvMessage.text = entry.message

            val colorRes = when (entry.level) {
                LogEntry.Level.DEBUG -> R.color.log_debug
                LogEntry.Level.INFO -> R.color.log_info
                LogEntry.Level.WARNING -> R.color.log_warning
                LogEntry.Level.ERROR -> R.color.log_error
            }
            tvLevel.setTextColor(itemView.context.getColor(colorRes))
        }
    }
}
