package com.mahasiswa.filemanager

import java.io.File

data class FileEntry(
    val file: File,
    val isDirectory: Boolean = file.isDirectory
) {
    val name: String get() = file.name

    fun extension(): String = file.extension.lowercase()

    fun typeCategory(): FileType {
        if (isDirectory) return FileType.FOLDER
        return when (extension()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> FileType.IMAGE
            "mp4", "mkv", "webm", "3gp", "mov" -> FileType.VIDEO
            "mp3", "wav", "ogg", "m4a", "flac" -> FileType.AUDIO
            "pdf" -> FileType.PDF
            "txt", "md", "json", "xml", "kt", "java", "log", "csv", "gradle" -> FileType.TEXT
            // Batch-4: baru ZIP yang didukung penuh (buat+ekstrak). Format lain
            // di bawah ini baru dikenali sebagai "arsip" secara visual/ikon -
            // ekstraknya menyusul di batch berikutnya (lihat ArchiveRepository).
            "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz", "xz" -> FileType.ARCHIVE
            else -> FileType.OTHER
        }
    }

    /** Batch-4: dipakai untuk cek apakah arsip ini sudah bisa diekstrak (baru ZIP). */
    fun isExtractableArchive(): Boolean = typeCategory() == FileType.ARCHIVE && extension() == "zip"
}

enum class FileType { FOLDER, IMAGE, VIDEO, AUDIO, PDF, TEXT, ARCHIVE, OTHER }
