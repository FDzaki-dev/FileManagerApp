package com.mahasiswa.filemanager

import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ==========================================================================
 *  MAIN VIEWMODEL - STATE PERSISTENCE (Batch-2)
 * ==========================================================================
 *  Tujuan batch ini: state navigasi & preferensi user bertahan saat:
 *   - Rotasi layar (configuration change) -> ViewModel instance dipertahankan
 *     otomatis oleh Android, jadi tidak perlu SavedStateHandle sama sekali.
 *   - Activity dimatikan sistem di background lalu dipulihkan (process death)
 *     -> di sini ViewModel instance BARU dibuat, tapi SavedStateHandle
 *     memulihkan nilai primitif (path folder, mode sort, query search,
 *     isi clipboard) sehingga user tidak kembali ke folder root / kehilangan
 *     clipboard begitu saja.
 *
 *  Yang SENGAJA TIDAK disimpan di SavedStateHandle: daftar entry hasil
 *  listing (currentEntries/entries LiveData). Alasan: bisa besar & berubah-
 *  ubah (representasi File di disk), jadi cukup dihitung ulang dari disk
 *  memakai currentDir yang sudah dipulihkan. Ini konsisten dengan pola
 *  Batch-1 (listing tetap async & cancellable lewat viewModelScope).
 * ==========================================================================
 */
class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

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
            val list = withContext(Dispatchers.IO) {
                val files = dir.listFiles()?.toList() ?: emptyList()
                files.map { FileEntry(it) }.sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }
                        .thenBy { if (sortByDateDesc) 0 else it.name.lowercase() }
                        .thenByDescending { if (sortByDateDesc) it.file.lastModified() else 0 }
                )
            }
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
            val results = withContext(Dispatchers.IO) {
                val found = mutableListOf<File>()
                FileOperations.searchRecursively(currentDir, query, found)
                found.map { FileEntry(it) }
            }
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
}
