package com.mahasiswa.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

/**
 * ==========================================================================
 *  ARCHIVE REPOSITORY (Batch-4: ZIP Engine - password + level kompresi)
 * ==========================================================================
 *  Kelas ini mengikuti pola yang sama seperti FileRepository (Batch-3):
 *  satu-satunya tempat yang boleh memanggil library zip4j secara langsung.
 *  MainViewModel/MainActivity TIDAK BOLEH import zip4j langsung - semua
 *  lewat fungsi suspend di sini via FileRepository.
 *
 *  Cakupan Batch-4 (sengaja dibatasi, sesuai aturan "pecah batch kecil"):
 *   - Format yang didukung penuh (buat + ekstrak): ZIP saja, dengan opsi
 *     password (AES-256) dan 4 level kompresi ala ZArchiver (Simpan/Cepat/
 *     Normal/Maksimal).
 *   - Format lain (7z, rar, tar, gzip, dst - sesuai roadmap "identik
 *     ZArchiver Pro") BELUM diimplementasikan di batch ini, menyusul di
 *     batch berikutnya supaya tiap batch tetap kecil & gampang ditelusuri
 *     kalau ada bug.
 *   - Browse isi arsip tanpa ekstrak (virtual folder ala ZArchiver) juga
 *     belum ada di batch ini - masih ekstrak langsung ke folder.
 *
 *  FileOperations.zipFiles() (Batch-1, zip tanpa password) TETAP dipertahankan
 *  apa adanya di FileOperations.kt - tidak dihapus, hanya sudah tidak dipakai
 *  lagi oleh tombol "Kompres ZIP" di UI (diganti alur dialog baru yang lebih
 *  lengkap di batch ini).
 *
 *  Batch-5: BROWSE ARSIP TANPA EKSTRAK (virtual folder ala ZArchiver)
 *   - listDirectory() membaca seluruh FileHeader arsip ZIP dan mengelompokkan
 *     jadi struktur folder virtual berdasarkan prefix path, tanpa menyentuh
 *     disk sama sekali (tidak ada file yang diekstrak untuk sekadar melihat
 *     isinya).
 *   - extractEntry() melengkapi extractZip() (yang mengekstrak SELURUH
 *     arsip) dengan opsi ekstrak SATU file/folder saja dari dalam arsip -
 *     dipakai saat user browsing lalu memilih ekstrak item tertentu.
 *   - Deteksi password: sama seperti extractZip(), verifikasi password
 *     zip4j baru benar-benar terjadi saat proses ekstrak (bukan saat
 *     listing header), jadi listDirectory() hanya mengecek flag
 *     `isEncrypted` di awal untuk NEEDS_PASSWORD; WRONG_PASSWORD baru bisa
 *     terdeteksi saat entry di dalamnya benar-benar diekstrak.
 *   - createZip()/extractZip()/isPasswordProtected() (Batch-4) TIDAK diubah
 *     sama sekali - browse ini murni penambahan baru.
 * ==========================================================================
 */
class ArchiveRepository {

    enum class Level { SIMPAN, CEPAT, NORMAL, MAKSIMAL }

    enum class ExtractStatus { SUCCESS, NEEDS_PASSWORD, WRONG_PASSWORD, FAILED }

    /** Buat arsip ZIP baru dari daftar file/folder, opsional password + level kompresi. */
    suspend fun createZip(
        files: List<File>,
        destZip: File,
        password: String?,
        level: Level
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val zipFile = if (!password.isNullOrEmpty()) {
                ZipFile(destZip, password.toCharArray())
            } else {
                ZipFile(destZip)
            }
            val params = ZipParameters()
            applyLevel(params, level)
            if (!password.isNullOrEmpty()) {
                params.isEncryptFiles = true
                params.encryptionMethod = EncryptionMethod.AES
                params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            for (file in files) {
                if (file.isDirectory) zipFile.addFolder(file, params) else zipFile.addFile(file, params)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun applyLevel(params: ZipParameters, level: Level) {
        when (level) {
            Level.SIMPAN -> params.compressionMethod = CompressionMethod.STORE
            Level.CEPAT -> {
                params.compressionMethod = CompressionMethod.DEFLATE
                params.compressionLevel = CompressionLevel.FASTEST
            }
            Level.NORMAL -> {
                params.compressionMethod = CompressionMethod.DEFLATE
                params.compressionLevel = CompressionLevel.NORMAL
            }
            Level.MAKSIMAL -> {
                params.compressionMethod = CompressionMethod.DEFLATE
                params.compressionLevel = CompressionLevel.MAXIMUM
            }
        }
    }

    /** @return true kalau arsip ZIP ini terkunci password (dicek tanpa ekstrak). */
    suspend fun isPasswordProtected(archiveFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipFile(archiveFile).isEncrypted
        } catch (e: Exception) {
            false
        }
    }

    /** Ekstrak arsip ZIP ke folder tujuan. Password null/kosong kalau arsip tidak terkunci. */
    suspend fun extractZip(archiveFile: File, destDir: File, password: String?): ExtractStatus =
        withContext(Dispatchers.IO) {
            try {
                val zipFile = ZipFile(archiveFile)
                if (zipFile.isEncrypted) {
                    if (password.isNullOrEmpty()) return@withContext ExtractStatus.NEEDS_PASSWORD
                    zipFile.setPassword(password.toCharArray())
                }
                if (!destDir.exists()) destDir.mkdirs()
                zipFile.extractAll(destDir.absolutePath)
                ExtractStatus.SUCCESS
            } catch (e: ZipException) {
                if (e.type == ZipException.Type.WRONG_PASSWORD) ExtractStatus.WRONG_PASSWORD
                else ExtractStatus.FAILED
            } catch (e: Exception) {
                ExtractStatus.FAILED
            }
        }

    // =======================================================================
    // Batch-5: BROWSE ARSIP TANPA EKSTRAK
    // =======================================================================

    /** Satu entry (file atau folder virtual) di dalam arsip, hasil listing satu level. */
    data class ArchiveNode(
        val name: String,
        /** Path penuh relatif dari akar arsip. Folder selalu diakhiri "/". */
        val internalPath: String,
        val isDirectory: Boolean,
        val uncompressedSize: Long,
        /** Null untuk folder virtual (tidak punya entry header fisik sendiri di zip). */
        val fileHeader: FileHeader?
    )

    enum class ArchiveListStatus { SUCCESS, NEEDS_PASSWORD, FAILED }

    data class ArchiveListResult(
        val status: ArchiveListStatus,
        val entries: List<ArchiveNode> = emptyList()
    )

    /**
     * List satu level isi arsip (bukan rekursif) - setara "ls" di dalam folder virtual.
     * @param internalPath "" untuk akar arsip, atau "folder/sub/" untuk sub-folder.
     */
    suspend fun listDirectory(
        archiveFile: File,
        internalPath: String,
        password: String?
    ): ArchiveListResult = withContext(Dispatchers.IO) {
        try {
            val zipFile = if (!password.isNullOrEmpty()) {
                ZipFile(archiveFile, password.toCharArray())
            } else {
                ZipFile(archiveFile)
            }
            if (zipFile.isEncrypted && password.isNullOrEmpty()) {
                return@withContext ArchiveListResult(ArchiveListStatus.NEEDS_PASSWORD)
            }

            val prefix = internalPath
            val childNames = linkedSetOf<String>()
            val nodes = mutableListOf<ArchiveNode>()

            for (header in zipFile.fileHeaders) {
                val path = header.fileName.replace('\\', '/')
                if (!path.startsWith(prefix) || path == prefix) continue
                val rest = path.removePrefix(prefix)
                val slashIdx = rest.indexOf('/')
                if (slashIdx == -1) {
                    // File langsung di level ini.
                    if (childNames.add(rest)) {
                        nodes.add(ArchiveNode(rest, path, false, header.uncompressedSize, header))
                    }
                } else {
                    // Folder (eksplisit maupun virtual/tersirat dari path file di dalamnya).
                    val folderName = rest.substring(0, slashIdx)
                    if (childNames.add(folderName)) {
                        nodes.add(ArchiveNode(folderName, "$prefix$folderName/", true, 0L, null))
                    }
                }
            }

            val sorted = nodes.sortedWith(
                compareByDescending<ArchiveNode> { it.isDirectory }.thenBy { it.name.lowercase() }
            )
            ArchiveListResult(ArchiveListStatus.SUCCESS, sorted)
        } catch (e: Exception) {
            ArchiveListResult(ArchiveListStatus.FAILED)
        }
    }

    /**
     * Ekstrak SATU file atau SATU folder (beserta isinya) dari dalam arsip ke [destDir],
     * tanpa mengekstrak seluruh arsip. Melengkapi extractZip() (ekstrak semua) di atas.
     */
    suspend fun extractEntry(
        archiveFile: File,
        node: ArchiveNode,
        destDir: File,
        password: String?
    ): ExtractStatus = withContext(Dispatchers.IO) {
        try {
            val zipFile = if (!password.isNullOrEmpty()) {
                ZipFile(archiveFile, password.toCharArray())
            } else {
                ZipFile(archiveFile)
            }
            if (zipFile.isEncrypted && password.isNullOrEmpty()) return@withContext ExtractStatus.NEEDS_PASSWORD
            if (!destDir.exists()) destDir.mkdirs()

            if (node.isDirectory) {
                val headersInFolder = zipFile.fileHeaders.filter {
                    it.fileName.replace('\\', '/').startsWith(node.internalPath)
                }
                headersInFolder.forEach { zipFile.extractFile(it, destDir.absolutePath) }
            } else {
                val header = node.fileHeader ?: zipFile.getFileHeader(node.internalPath)
                zipFile.extractFile(header, destDir.absolutePath)
            }
            ExtractStatus.SUCCESS
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) ExtractStatus.WRONG_PASSWORD
            else ExtractStatus.FAILED
        } catch (e: Exception) {
            ExtractStatus.FAILED
        }
    }
}
