package com.folder2ebook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.folder2ebook.R

data class FileItem(
    val fileName: String,
    val fileSize: Long,
    val fileType: String
)

class FileListAdapter : ListAdapter<FileItem, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val fileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
        private val fileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)

        fun bind(item: FileItem) {
            fileName.text = item.fileName
            fileInfo.text = "${item.fileType.uppercase()} • ${formatFileSize(item.fileSize)}"
            val iconRes = when (item.fileType.lowercase()) {
                "pdf" -> R.drawable.ic_pdf
                "html", "htm", "xhtml" -> R.drawable.ic_html
                else -> R.drawable.ic_text
            }
            fileIcon.setImageResource(iconRes)
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem) =
            oldItem.fileName == newItem.fileName

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem) =
            oldItem == newItem
    }
}
