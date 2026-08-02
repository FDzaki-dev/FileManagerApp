package com.mahasiswa.filemanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * ==========================================================================
 *  ARCHIVE ENTRY ADAPTER (Batch-5: Browse Arsip Tanpa Ekstrak)
 * ==========================================================================
 *  Adapter TERPISAH dari FileAdapter (bukan modifikasi FileAdapter), sengaja
 *  dibuat kelas baru supaya listing folder biasa di disk (FileAdapter) tidak
 *  tersentuh sama sekali - nol risiko regresi ke fitur yang sudah ada.
 *
 *  Menampilkan isi arsip (ArchiveRepository.ArchiveNode) satu level, memakai
 *  layout item_file.xml yang sama seperti listing folder biasa supaya
 *  tampilannya konsisten. Belum ada mode seleksi multi-item di batch ini
 *  (checkIcon selalu disembunyikan) - menyusul kalau dibutuhkan di batch
 *  berikutnya.
 * ==========================================================================
 */
class ArchiveEntryAdapter(
    private var items: List<ArchiveRepository.ArchiveNode>,
    private val onClick: (ArchiveRepository.ArchiveNode) -> Unit
) : RecyclerView.Adapter<ArchiveEntryAdapter.ViewHolder>() {

    fun updateData(newItems: List<ArchiveRepository.ArchiveNode>) {
        items = newItems
        notifyDataSetChanged()
    }

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
        val node = items[position]
        holder.fileName.text = node.name
        holder.fileMeta.text = if (node.isDirectory) {
            "Folder di dalam arsip"
        } else {
            FileOperations.formatSize(node.uncompressedSize)
        }

        // Batch-5: belum ada mode seleksi untuk browse arsip.
        holder.checkIcon.visibility = View.GONE
        holder.thumbnail.visibility = View.GONE
        holder.iconType.visibility = View.VISIBLE
        holder.iconType.setImageResource(
            if (node.isDirectory) R.drawable.ic_folder else iconForArchivedFile(node.name)
        )

        holder.itemView.setOnClickListener { onClick(node) }
    }

    private fun iconForArchivedFile(name: String): Int {
        // Sama persis dengan pemetaan ekstensi -> ikon di FileEntry.typeCategory(),
        // supaya ikon file di dalam arsip konsisten dengan ikon file di disk.
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> R.drawable.ic_image
            "mp4", "mkv", "webm", "3gp", "mov" -> R.drawable.ic_video
            "mp3", "wav", "ogg", "m4a", "flac" -> R.drawable.ic_audio
            "pdf" -> R.drawable.ic_pdf
            "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz", "xz" -> R.drawable.ic_archive
            else -> R.drawable.ic_file
        }
    }

    override fun getItemCount(): Int = items.size
}
