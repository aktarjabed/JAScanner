package com.aktarjabed.jascanner.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aktarjabed.jascanner.batch.BatchScanManager
import com.aktarjabed.jascanner.databinding.ItemPageBinding

class PageAdapter(
    private val onDeleteClick: (BatchScanManager.ScannedPage) -> Unit,
    private val onEnhanceClick: (BatchScanManager.ScannedPage) -> Unit
) : ListAdapter<BatchScanManager.ScannedPage, PageAdapter.PageViewHolder>(PageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val list = currentList.toMutableList()
        val movedItem = list.removeAt(fromPosition)
        list.add(toPosition, movedItem)
        submitList(list)
    }

    inner class PageViewHolder(private val binding: ItemPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: BatchScanManager.ScannedPage) {
            binding.pageNumberText.text = "Page ${adapterPosition + 1}"
            binding.enhancementTypeText.text = page.enhancementType.name

            val displayFile = page.enhancedFile ?: page.originalFile
            val bitmap = BitmapFactory.decodeFile(displayFile.absolutePath)
            binding.pageImageView.setImageBitmap(bitmap)

            binding.deleteButton.setOnClickListener {
                onDeleteClick(page)
            }

            binding.enhanceButton.setOnClickListener {
                onEnhanceClick(page)
            }
        }
    }

    class PageDiffCallback : DiffUtil.ItemCallback<BatchScanManager.ScannedPage>() {
        override fun areItemsTheSame(
            oldItem: BatchScanManager.ScannedPage,
            newItem: BatchScanManager.ScannedPage
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: BatchScanManager.ScannedPage,
            newItem: BatchScanManager.ScannedPage
        ): Boolean = oldItem == newItem
    }
}