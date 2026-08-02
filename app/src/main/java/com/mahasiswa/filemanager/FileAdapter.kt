package com.mahasiswa.filemanager

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private var items: List<FileEntry>,
    private val onClick: (FileEntry) -> Unit,
    private val onLongClick: (FileEntry) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    var selectionMode: Boolean = false
    val selectedItems: MutableSet<File2> = mutableSetOf()

    fun updateData(newItems: List<FileEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        selectionMode = false
        notifyDataSetChanged()
    }

    fun toggleSelection(entry: FileEntry) {
        val key = File2(entry.file.absolutePath)
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        selectionMode = selectedItems.isNotEmpty()
        notifyDataSetChanged()
    }

    fun getSelectedFiles(): List<java.io.File> = selectedItems.map { java.io.File(it.path) }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkIcon: ImageView = view.findViewById(R.id.checkIcon)
        val iconType: ImageView = view.findViewById(R.id.iconType)
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val fileName: TextView = view.findViewById(R.id.fileName)
        val fileMeta: TextView = view.findViewById(R.id.fileMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.fileName.text = entry.name

        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            .format(Date(entry.file.lastModified()))

        holder.fileMeta.text = if (entry.isDirectory) {
            val count = entry.file.listFiles()?.size ?: 0
            "$count item · $dateStr"
        } else {
            "${FileOperations.formatSize(entry.file.length())} · $dateStr"
        }

        holder.thumbnail.visibility = View.GONE
        holder.iconType.visibility = View.VISIBLE

        when (entry.typeCategory()) {
            FileType.FOLDER -> holder.iconType.setImageResource(R.drawable.ic_folder)
            FileType.IMAGE -> {
                holder.iconType.visibility = View.GONE
                holder.thumbnail.visibility = View.VISIBLE
                loadThumbnail(entry.file.absolutePath, holder.thumbnail)
            }
            FileType.VIDEO -> holder.iconType.setImageResource(R.drawable.ic_video)
            FileType.AUDIO -> holder.iconType.setImageResource(R.drawable.ic_audio)
            FileType.PDF -> holder.iconType.setImageResource(R.drawable.ic_pdf)
            FileType.TEXT -> holder.iconType.setImageResource(R.drawable.ic_file)
            FileType.ARCHIVE -> holder.iconType.setImageResource(R.drawable.ic_archive)
            FileType.OTHER -> holder.iconType.setImageResource(R.drawable.ic_file)
        }

        val key = File2(entry.file.absolutePath)
        if (selectionMode) {
            holder.checkIcon.visibility = View.VISIBLE
            holder.checkIcon.setImageResource(
                if (selectedItems.contains(key)) R.drawable.ic_check_circle else R.drawable.circle_uncheck
            )
        } else {
            holder.checkIcon.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(entry) else onClick(entry)
        }
        holder.itemView.setOnLongClickListener {
            onLongClick(entry)
            true
        }
    }

    private fun loadThumbnail(path: String, imageView: ImageView) {
        try {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(path, options)
            if (bitmap != null) imageView.setImageBitmap(bitmap)
            else imageView.setImageResource(R.drawable.ic_image)
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.ic_image)
        }
    }

    override fun getItemCount(): Int = items.size

    data class File2(val path: String)
}
