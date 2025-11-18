package com.aktarjabed.jascanner.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.aktarjabed.jascanner.databinding.ItemGalleryImageBinding

class GalleryImageAdapter(
    private val onSelectionChanged: (Uri, Boolean) -> Unit
) : ListAdapter<Uri, GalleryImageAdapter.ImageViewHolder>(UriDiffCallback()) {

    private val selectedUris = mutableSetOf<Uri>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemGalleryImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectedUris(uris: Set<Uri>) {
        selectedUris.clear()
        selectedUris.addAll(uris)
        notifyDataSetChanged()
    }

    fun getAllUris(): List<Uri> = currentList

    inner class ImageViewHolder(private val binding: ItemGalleryImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri) {
            val isSelected = selectedUris.contains(uri)

            Glide.with(binding.root.context)
                .load(uri)
                .centerCrop()
                .into(binding.imageView)

            binding.selectionOverlay.isVisible = isSelected
            binding.checkIcon.isVisible = isSelected

            binding.root.setOnClickListener {
                val newState = !selectedUris.contains(uri)
                if (newState) selectedUris.add(uri) else selectedUris.remove(uri)

                binding.selectionOverlay.isVisible = newState
                binding.checkIcon.isVisible = newState

                onSelectionChanged(uri, newState)
            }
        }
    }

    class UriDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
    }
}