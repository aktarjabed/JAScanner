package com.aktarjabed.jascanner.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aktarjabed.jascanner.databinding.ItemDocumentBinding
import java.io.File

class DocumentAdapter(
    private val onOpenClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : ListAdapter<File, DocumentAdapter.DocumentViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DocumentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DocumentViewHolder(private val binding: ItemDocumentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            binding.documentNameText.text = file.name
            binding.documentSizeText.text = formatFileSize(file.length())
            binding.documentDateText.text = DateUtils.getRelativeTimeSpanString(
                file.lastModified(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            binding.root.setOnClickListener {
                onOpenClick(file)
            }

            binding.deleteButton.setOnClickListener {
                onDeleteClick(file)
            }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
            oldItem.absolutePath == newItem.absolutePath

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
            oldItem.lastModified() == newItem.lastModified()
    }
}