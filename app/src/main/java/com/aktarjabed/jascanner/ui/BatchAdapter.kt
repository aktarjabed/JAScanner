package com.aktarjabed.jascanner.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.aktarjabed.jascanner.R
import com.aktarjabed.jascanner.processing.BatchScanManager
import java.io.File
import java.util.concurrent.Executors

class BatchAdapter(
    private val pages: MutableList<BatchScanManager.Page>,
    private val onClick: (BatchScanManager.Page) -> Unit
) : RecyclerView.Adapter<BatchAdapter.VH>() {

    private val io = Executors.newSingleThreadExecutor()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = pages[position]
        holder.thumb.setImageResource(android.R.color.darker_gray) // placeholder

        // Load enhanced file if present, else original as low-res thumbnail (background thread)
        val fileToLoad = page.enhancedFile ?: page.originalFile
        io.execute {
            val bmp = BitmapFactory.decodeFile(fileToLoad.absolutePath)
            holder.thumb.post {
                holder.thumb.setImageBitmap(bmp)
            }
        }

        holder.itemView.setOnClickListener { onClick(page) }
    }

    override fun getItemCount(): Int = pages.size

    fun update(newPages: List<BatchScanManager.Page>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }
}