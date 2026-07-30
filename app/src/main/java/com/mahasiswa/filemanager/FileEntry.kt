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
            else -> FileType.OTHER
        }
    }
}

enum class FileType { FOLDER, IMAGE, VIDEO, AUDIO, PDF, TEXT, OTHER }
