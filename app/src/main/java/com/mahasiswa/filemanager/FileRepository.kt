package com.mahasiswa.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ==========================================================================
 *  FILE REPOSITORY (Batch-3: Repository Pattern)
 * ==========================================================================
 *  Satu-satunya lapisan yang boleh menyentuh disk/File API secara langsung
 *  untuk semua operasi data: listing folder, search, rename batch, hapus,
 *  copy/move, zip, ukuran folder, dan buat folder baru.
 *
 *  Sebelumnya (Batch-1/2) operasi ini tersebar: sebagian di MainActivity
 *  (rename/delete/zip/paste/buat folder/ukuran folder memanggil FileOperations
 *  langsung dari lifecycleScope), sebagian di MainViewModel (listing+sort
 *  ditulis inline). Mulai Batch-3, MainViewModel & MainActivity TIDAK BOLEH
 *  lagi memanggil `FileOperations` atau File I/O mentah secara langsung -
 *  semua wajib lewat FileRepository ini.
 *
 *  Manfaat:
 *   - Satu titik untuk troubleshooting bug I/O, bukan tersebar di 2 kelas.
 *   - Setiap fungsi suspend & otomatis pindah ke Dispatchers.IO sendiri,
 *     jadi pemanggil (ViewModel) tidak perlu withContext(Dispatchers.IO)
 *     berulang-ulang seperti sebelumnya.
 *   - Kalau nanti butuh sumber data lain (mis. cache index pencarian, atau
 *     dukungan storage lain), cukup ubah/extend di sini tanpa menyentuh
 *     ViewModel maupun Activity.
 *
 *  Catatan: kelas ini murni membungkus `FileOperations` (logika inti disk
 *  I/O di Batch-1 tetap dipertahankan apa adanya, tidak ditulis ulang) +
 *  menambahkan logika listing folder yang sebelumnya ada di MainViewModel.
 * ==========================================================================
 */
class FileRepository {

    // Batch-4: engine arsip (ZIP password + level kompresi) dipisah ke kelas
    // sendiri (ArchiveRepository) karena beda concern dari I/O file biasa,
    // tapi tetap diakses lewat FileRepository ini supaya ViewModel/Activity
    // tetap punya satu pintu akses saja (konsisten dgn pola Batch-3).
    private val archiveRepository = ArchiveRepository()

    /** Listing isi folder + sorting (folder dulu, lalu sesuai preferensi sort). */
    suspend fun listDirectory(dir: File, sortByDateDesc: Boolean): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val files = dir.listFiles()?.toList() ?: emptyList()
            files.map { FileEntry(it) }.sortedWith(
                compareByDescending<FileEntry> { it.isDirectory }
                    .thenBy { if (sortByDateDesc) 0 else it.name.lowercase() }
                    .thenByDescending { if (sortByDateDesc) it.file.lastModified() else 0 }
            )
        }

    suspend fun search(dir: File, query: String): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val found = mutableListOf<File>()
            FileOperations.searchRecursively(dir, query, found)
            found.map { FileEntry(it) }
        }

    /** @return jumlah file yang berhasil di-rename */
    suspend fun batchRename(files: List<File>, pattern: String, startNumber: Int): Int =
        withContext(Dispatchers.IO) {
            FileOperations.batchRename(files, pattern, startNumber).count { it.second }
        }

    /** @return jumlah item yang berhasil dihapus */
    suspend fun deleteFiles(files: List<File>): Int =
        withContext(Dispatchers.IO) {
            files.count { FileOperations.deleteRecursively(it) }
        }

    /** @return jumlah item yang berhasil disalin/dipindah */
    suspend fun copyOrMoveFiles(files: List<File>, targetDir: File, isCut: Boolean): Int =
        withContext(Dispatchers.IO) {
            var count = 0
            files.forEach { file ->
                val ok = if (isCut) FileOperations.moveFile(file, targetDir)
                         else FileOperations.copyRecursively(file, targetDir)
                if (ok) count++
            }
            count
        }

    suspend fun zipFiles(files: List<File>, destZip: File): Boolean =
        withContext(Dispatchers.IO) { FileOperations.zipFiles(files, destZip) }

    // ---------------------------------------------------------------------
    // Batch-4: ZIP dengan password + level kompresi (menggantikan tombol
    // "Kompres ZIP" lama yang tanpa opsi - zipFiles() di atas TETAP ada,
    // tidak dihapus, hanya sudah tidak dipanggil dari UI).
    // ---------------------------------------------------------------------
    suspend fun createArchive(
        files: List<File>,
        destZip: File,
        password: String?,
        level: ArchiveRepository.Level
    ): Boolean = archiveRepository.createZip(files, destZip, password, level)

    suspend fun isArchivePasswordProtected(archiveFile: File): Boolean =
        archiveRepository.isPasswordProtected(archiveFile)

    suspend fun extractArchive(
        archiveFile: File,
        destDir: File,
        password: String?
    ): ArchiveRepository.ExtractStatus = archiveRepository.extractZip(archiveFile, destDir, password)

    suspend fun folderSize(dir: File): Long =
        withContext(Dispatchers.IO) { FileOperations.folderSize(dir) }

    fun formatSize(bytes: Long): String = FileOperations.formatSize(bytes)

    suspend fun createFolder(parent: File, name: String): Boolean =
        withContext(Dispatchers.IO) { File(parent, name).mkdirs() }
}
