package com.example.teacherday

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class LessonAdapter : RecyclerView.Adapter<LessonAdapter.VH>() {

    private val items = mutableListOf<LessonBlock>()

    fun submitList(list: List<LessonBlock>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.tvNumber)
        val content: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lesson, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.number.text = item.number.toString()
        holder.content.text = item.content

        // ✨ анимация
        /*holder.itemView.alpha = 0f
        holder.itemView.translationY = 30f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setStartDelay((position * 50).toLong())
            .start()*/
    }

    override fun getItemCount() = items.size

}