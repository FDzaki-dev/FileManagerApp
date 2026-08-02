package com.mahasiswa.filemanager

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileOperations {

    fun folderSize(dir: File): Long {
        if (!dir.isDirectory) return dir.length()
        var size = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.add(child) else size += child.length()
            }
        }
        return size
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024.0
            unitIndex++
        }
        return String.format("%.1f %s", value, units[unitIndex])
    }

    fun copyRecursively(src: File, destDir: File): Boolean {
        return try {
            val target = File(destDir, src.name)
            if (src.isDirectory) {
                target.mkdirs()
                src.listFiles()?.forEach { child -> copyRecursively(child, target) }
            } else {
                FileInputStream(src).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun moveFile(src: File, destDir: File): Boolean {
        val target = File(destDir, src.name)
        val renamed = src.renameTo(target)
        if (renamed) return true
        // fallback: copy lalu hapus (beda partisi/storage)
        val copied = copyRecursively(src, destDir)
        if (copied) return deleteRecursively(src)
        return false
    }

    fun deleteRecursively(target: File): Boolean {
        if (target.isDirectory) {
            target.listFiles()?.forEach { deleteRecursively(it) }
        }
        return target.delete()
    }

    /**
     * Batch rename dengan pola berisi {n} untuk nomor urut.
     * Ekstensi asli file dipertahankan otomatis untuk file (bukan folder).
     */
    fun batchRename(files: List<File>, pattern: String, startNumber: Int): List<Pair<File, Boolean>> {
        val results = mutableListOf<Pair<File, Boolean>>()
        var counter = startNumber
        for (file in files) {
            val baseName = if (pattern.contains("{n}")) {
                pattern.replace("{n}", counter.toString())
            } else {
                "$pattern$counter"
            }
            val newName = if (!file.isDirectory && file.extension.isNotEmpty()) {
                "$baseName.${file.extension}"
            } else {
                baseName
            }
            val newFile = File(file.parentFile, newName)
            val success = !newFile.exists() && file.renameTo(newFile)
            results.add(Pair(if (success) newFile else file, success))
            counter++
        }
        return results
    }

    fun zipFiles(files: List<File>, destZip: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                for (file in files) {
                    addToZip(file, file.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                addToZip(child, "$entryName/${child.name}", zos)
            }
        } else {
            FileInputStream(file).use { input ->
                zos.putNextEntry(ZipEntry(entryName))
                input.copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    /**
     * Pencarian rekursif berbasis nama file, case-insensitive.
     * Berjalan di thread pemanggil - jalankan di background thread.
     */
    fun searchRecursively(root: File, query: String, results: MutableList<File>, maxResults: Int = 500) {
        if (results.size >= maxResults) return
        val children = root.listFiles() ?: return
        for (child in children) {
            if (results.size >= maxResults) return
            if (child.name.contains(query, ignoreCase = true)) {
                results.add(child)
            }
            if (child.isDirectory) {
                searchRecursively(child, query, results, maxResults)
            }
        }
    }
}
