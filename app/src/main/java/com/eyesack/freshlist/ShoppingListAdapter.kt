package com.eyesack.freshlist

import android.graphics.Paint
import android.graphics.Color // Import Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShoppingListAdapter(
    private val items: MutableList<String>,
    private val crossedOffItems: MutableList<String>
) : RecyclerView.Adapter<ShoppingListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.itemTextView)

        init {
            itemView.setOnClickListener {
                val item = items[adapterPosition]
                if (item in crossedOffItems) {
                    // If the item is already in crossedOffItems (from API), remove it on the first tap
                    crossedOffItems.remove(item)
                    moveItemToTop(item)
                } else {
                    // Otherwise, add it to crossedOffItems to cross it off
                    crossedOffItems.add(item)
                    moveItemToBottom(adapterPosition)
                }
                notifyDataSetChanged()
                // notifyItemChanged(adapterPosition)
                // Save the updated list to SharedPreferences
                (itemView.context as MainActivity).saveData()
            }
        }
    }

    private fun moveItemToBottom(position: Int) {
        val item = items.removeAt(position)
        items.add(item) // Add item to the end of the list
    }

    private fun moveItemToTop(item: String) {
        if (items.contains(item)) {
            items.remove(item)
            items.add(0, item) // Add item back to the top of the list
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shopping_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item
        holder.textView.setTextColor(Color.WHITE)

        // Apply or remove the strike-through effect based on cross-off status
        if (item in crossedOffItems) {
            holder.textView.paintFlags = holder.textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.textView.paintFlags = holder.textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun getItemCount(): Int = items.size

    private fun toggleCrossOffStatus(position: Int) {
        val item = items[position]
        if (item in crossedOffItems) {
            crossedOffItems.remove(item)
        } else {
            crossedOffItems.add(item)
        }
        notifyItemChanged(position)
    }
}

