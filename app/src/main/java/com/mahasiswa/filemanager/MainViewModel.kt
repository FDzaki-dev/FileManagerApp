package com.mahasiswa.filemanager

import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * ==========================================================================
 *  MAIN VIEWMODEL - STATE PERSISTENCE (Batch-2) + REPOSITORY (Batch-3)
 * ==========================================================================
 *  Batch-2: state navigasi & preferensi user bertahan saat:
 *   - Rotasi layar (configuration change) -> ViewModel instance dipertahankan
 *     otomatis oleh Android, jadi tidak perlu SavedStateHandle sama sekali.
 *   - Activity dimatikan sistem di background lalu dipulihkan (process death)
 *     -> di sini ViewModel instance BARU dibuat, tapi SavedStateHandle
 *     memulihkan nilai primitif (path folder, mode sort, query search,
 *     isi clipboard) sehingga user tidak kembali ke folder root / kehilangan
 *     clipboard begitu saja.
 *
 *  Batch-3: seluruh akses disk (listing, search, rename, delete, copy/move,
 *  zip, ukuran folder, buat folder) TIDAK lagi ditulis inline atau memanggil
 *  FileOperations langsung dari sini/Activity - semua lewat FileRepository.
 *  ViewModel ini sekarang murni "penghubung" antara UI dan repository, tidak
 *  ada satu pun `java.io.File` I/O mentah yang dieksekusi langsung di kelas
 *  ini (kecuali FileEntry.file yang cuma referensi objek/path, bukan I/O).
 *
 *  Yang SENGAJA TIDAK disimpan di SavedStateHandle: daftar entry hasil
 *  listing (currentEntries/entries LiveData). Alasan: bisa besar & berubah-
 *  ubah (representasi File di disk), jadi cukup dihitung ulang dari disk
 *  memakai currentDir yang sudah dipulihkan. Ini konsisten dengan pola
 *  Batch-1 (listing tetap async & cancellable lewat viewModelScope).
 * ==========================================================================
 */
class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val repository = FileRepository()

    companion object {
        private const val KEY_CURRENT_DIR = "key_current_dir"
        private const val KEY_SORT_DATE_DESC = "key_sort_date_desc"
        private const val KEY_SEARCH_QUERY = "key_search_query"
        private const val KEY_CLIPBOARD_PATHS = "key_clipboard_paths"
        private const val KEY_CLIPBOARD_IS_CUT = "key_clipboard_is_cut"
    }

    // -----------------------------------------------------------------
    // Direktori aktif (dipulihkan setelah process death)
    // -----------------------------------------------------------------
    var currentDir: File
        get() = File(
            savedStateHandle.get<String>(KEY_CURRENT_DIR)
                ?: Environment.getExternalStorageDirectory().absolutePath
        )
        private set(value) { savedStateHandle[KEY_CURRENT_DIR] = value.absolutePath }

    private val _currentDirPath = MutableLiveData(currentDir.absolutePath)
    val currentDirPath: LiveData<String> get() = _currentDirPath

    // -----------------------------------------------------------------
    // Preferensi sort
    // -----------------------------------------------------------------
    var sortByDateDesc: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_SORT_DATE_DESC) ?: false
        private set(value) { savedStateHandle[KEY_SORT_DATE_DESC] = value }

    // -----------------------------------------------------------------
    // Query search terakhir (untuk restore teks di search box)
    // -----------------------------------------------------------------
    var searchQuery: String
        get() = savedStateHandle.get<String>(KEY_SEARCH_QUERY) ?: ""
        private set(value) { savedStateHandle[KEY_SEARCH_QUERY] = value }

    // -----------------------------------------------------------------
    // Clipboard (copy / move) - ikut dipulihkan supaya "Tempel di Sini"
    // tidak hilang percuma kalau layar rotasi di tengah proses.
    // -----------------------------------------------------------------
    private var clipboardPaths: List<String>
        get() = savedStateHandle.get<ArrayList<String>>(KEY_CLIPBOARD_PATHS) ?: emptyList()
        set(value) { savedStateHandle[KEY_CLIPBOARD_PATHS] = ArrayList(value) }

    var clipboardIsCut: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_CLIPBOARD_IS_CUT) ?: false
        private set(value) { savedStateHandle[KEY_CLIPBOARD_IS_CUT] = value }

    val clipboardFiles: List<File> get() = clipboardPaths.map { File(it) }

    fun setClipboard(files: List<File>, isCut: Boolean) {
        clipboardPaths = files.map { it.absolutePath }
        clipboardIsCut = isCut
    }

    fun clearClipboard() {
        clipboardPaths = emptyList()
    }

    // -----------------------------------------------------------------
    // Listing folder aktif / hasil search
    // -----------------------------------------------------------------
    private val _entries = MutableLiveData<List<FileEntry>>(emptyList())
    val entries: LiveData<List<FileEntry>> get() = _entries

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    // Cache listing folder biasa (tanpa filter search) supaya saat search box
    // dikosongkan, tampilan bisa balik instan tanpa scan ulang disk.
    private var lastDirEntries: List<FileEntry> = emptyList()

    private var dirLoadJob: Job? = null
    private var searchJob: Job? = null

    // Flag in-memory biasa (bukan SavedStateHandle): true selama ViewModel
    // masih hidup (survive rotasi). Kalau process death, ViewModel dibuat
    // baru -> flag ini balik false -> listing folder otomatis di-load ulang
    // dari currentDir yang sudah dipulihkan.
    private var initialized = false

    fun ensureLoaded() {
        if (!initialized) {
            initialized = true
            loadDirectory(currentDir)
        }
    }

    fun loadDirectory(dir: File) {
        currentDir = dir
        _currentDirPath.value = dir.absolutePath
        dirLoadJob?.cancel()
        _isLoading.value = true
        dirLoadJob = viewModelScope.launch {
            val list = repository.listDirectory(dir, sortByDateDesc)
            if (!isActive) return@launch
            _isLoading.value = false
            lastDirEntries = list
            _entries.value = list
        }
    }

    fun reloadCurrentDirectory() = loadDirectory(currentDir)

    fun runSearch(query: String) {
        searchQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _isLoading.value = false
            _entries.value = lastDirEntries
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce - sama seperti Batch-1
            if (!isActive) return@launch
            _isLoading.value = true
            val results = repository.search(currentDir, query)
            if (!isActive) return@launch
            _isLoading.value = false
            _entries.value = results
        }
    }

    /** Dipanggil saat search box dikosongkan user. */
    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        _isLoading.value = false
        _entries.value = lastDirEntries
    }

    /** @return status sort yang baru (true = tanggal terbaru, false = nama) */
    fun toggleSort(): Boolean {
        sortByDateDesc = !sortByDateDesc
        reloadCurrentDirectory()
        return sortByDateDesc
    }

    // -----------------------------------------------------------------
    // Aksi file (Batch-3): semuanya lewat FileRepository, lalu reload
    // listing folder aktif. Activity hanya perlu tahu HASIL (lewat
    // callback onResult) untuk urusan UI (Toast/dialog), tidak lagi
    // menyentuh File I/O sama sekali.
    // -----------------------------------------------------------------

    fun renameSelected(files: List<File>, pattern: String, startNumber: Int, onResult: (successCount: Int, total: Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.batchRename(files, pattern, startNumber)
            onResult(count, files.size)
            reloadCurrentDirectory()
        }
    }

    fun deleteSelected(files: List<File>, onResult: (successCount: Int, total: Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.deleteFiles(files)
            onResult(count, files.size)
            reloadCurrentDirectory()
        }
    }

    // -----------------------------------------------------------------
    // Batch-4: Archive Engine - buat arsip ZIP (nama custom + password
    // opsional + level kompresi) dan ekstrak arsip ZIP. Menggantikan
    // zipSelected() lama yang langsung zip tanpa opsi apa pun.
    // -----------------------------------------------------------------
    fun createArchive(
        files: List<File>,
        archiveName: String,
        password: String?,
        level: ArchiveRepository.Level,
        onResult: (success: Boolean, zipName: String) -> Unit
    ) {
        val safeName = if (archiveName.endsWith(".zip", ignoreCase = true)) archiveName else "$archiveName.zip"
        val destZip = File(currentDir, safeName)
        viewModelScope.launch {
            val success = repository.createArchive(files, destZip, password, level)
            onResult(success, safeName)
            reloadCurrentDirectory()
        }
    }

    /** Cek dulu apakah arsip terkunci password, sebelum tampilkan dialog ekstrak. */
    fun checkArchivePassword(archiveFile: File, onResult: (needsPassword: Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.isArchivePasswordProtected(archiveFile))
        }
    }

    fun extractArchive(
        archiveFile: File,
        destDir: File,
        password: String?,
        onResult: (status: ArchiveRepository.ExtractStatus) -> Unit
    ) {
        viewModelScope.launch {
            val status = repository.extractArchive(archiveFile, destDir, password)
            onResult(status)
            if (status == ArchiveRepository.ExtractStatus.SUCCESS) reloadCurrentDirectory()
        }
    }

    /** @return false kalau clipboard sedang kosong (tidak ada yang dieksekusi) */
    fun pasteClipboard(onResult: (successCount: Int, total: Int) -> Unit): Boolean {
        val files = clipboardFiles
        if (files.isEmpty()) return false
        val targetDir = currentDir
        val isCut = clipboardIsCut
        viewModelScope.launch {
            val count = repository.copyOrMoveFiles(files, targetDir, isCut)
            clearClipboard()
            onResult(count, files.size)
            reloadCurrentDirectory()
        }
        return true
    }

    fun createFolder(name: String, onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.createFolder(currentDir, name)
            onResult(success)
            if (success) reloadCurrentDirectory()
        }
    }

    fun computeFolderSize(onResult: (formattedSize: String, folderName: String) -> Unit) {
        val target = currentDir
        viewModelScope.launch {
            val size = repository.folderSize(target)
            onResult(repository.formatSize(size), target.name)
        }
    }
}
