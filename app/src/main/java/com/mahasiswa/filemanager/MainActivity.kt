package com.mahasiswa.filemanager

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ==========================================================================
 *  MAIN ACTIVITY - FILE MANAGER
 * ==========================================================================
 *  Struktur file ini dibagi jadi beberapa region agar mudah di-troubleshoot:
 *   1. STATE & VIEW BINDING
 *   2. LIFECYCLE & PERMISSION
 *   3. NAVIGASI FOLDER (breadcrumb, back, load direktori)
 *   4. SEARCH
 *   5. SELECTION MODE (multi-select + action bar: rename/copy/move/zip/hapus)
 *   6. CLIPBOARD (copy/move -> paste di folder tujuan)
 *   7. PREVIEW FILE (gambar, teks, pdf, lainnya)
 *   8. MENU TOOLBAR (folder baru, ukuran folder, sort)
 * ==========================================================================
 */
class MainActivity : AppCompatActivity() {

    // ---------------------------------------------------------------------
    // 1. STATE & VIEW BINDING
    // ---------------------------------------------------------------------
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var breadcrumb: TextView
    private lateinit var searchBox: EditText
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var selectionBar: View
    private lateinit var pasteBar: View
    private lateinit var pasteInfo: TextView

    private lateinit var adapter: FileAdapter

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private var currentEntries: List<FileEntry> = emptyList()
    private var sortByDateDesc: Boolean = false

    // Clipboard sederhana untuk operasi copy/move
    private var clipboardFiles: List<File> = emptyList()
    private var clipboardIsCut: Boolean = false

    // Job pencarian aktif - dibatalkan tiap kali user mengetik lagi (debounce)
    private var searchJob: Job? = null

    // Job listing folder aktif - dibatalkan kalau user pindah folder lagi sebelum selesai load
    private var dirLoadJob: Job? = null

    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
            checkPermissionAndLoad()
        }

    // ---------------------------------------------------------------------
    // 2. LIFECYCLE & PERMISSION
    // ---------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupSelectionBar()
        setupPasteBar()
        checkPermissionAndLoad()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        toolbar = findViewById(R.id.toolbar)
        breadcrumb = findViewById(R.id.breadcrumb)
        searchBox = findViewById(R.id.searchBox)
        progressBar = findViewById(R.id.progressBar)
        selectionBar = findViewById(R.id.selectionBar)
        pasteBar = findViewById(R.id.pasteBar)
        pasteInfo = findViewById(R.id.pasteInfo)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        toolbar.title = getString(R.string.app_name)
        toolbar.setNavigationOnClickListener { handleBackAction() }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionAndLoad() {
        if (hasStoragePermission()) {
            loadDirectory(currentDir)
        } else {
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            requestPermissionLauncher.launch(intent)
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                100
            )
        }
    }

    // ---------------------------------------------------------------------
    // 3. NAVIGASI FOLDER
    // ---------------------------------------------------------------------
    private fun loadDirectory(dir: File) {
        currentDir = dir
        updateBreadcrumb()
        dirLoadJob?.cancel()
        progressBar.visibility = View.VISIBLE
        dirLoadJob = lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                val list = dir.listFiles()?.toList() ?: emptyList()
                list.map { FileEntry(it) }.sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }
                        .thenBy { if (sortByDateDesc) 0 else it.name.lowercase() }
                        .thenByDescending { if (sortByDateDesc) it.file.lastModified() else 0 }
                )
            }
            if (!isActive) return@launch
            progressBar.visibility = View.GONE
            currentEntries = entries
            adapter.updateData(entries)
            adapter.clearSelection()
            updateSelectionBarVisibility()
        }
    }

    private fun updateBreadcrumb() {
        breadcrumb.text = currentDir.absolutePath
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            items = emptyList(),
            onClick = { entry -> onEntryClick(entry) },
            onLongClick = { entry -> onEntryLongClick(entry) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun onEntryClick(entry: FileEntry) {
        if (entry.isDirectory) {
            loadDirectory(entry.file)
        } else {
            previewFile(entry)
        }
    }

    private fun onEntryLongClick(entry: FileEntry) {
        adapter.toggleSelection(entry)
        updateSelectionBarVisibility()
    }

    private fun goBackOneLevel(): Boolean {
        val parent = currentDir.parentFile
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (parent != null && currentDir.absolutePath != rootPath) {
            loadDirectory(parent)
            return true
        }
        return false
    }

    override fun onBackPressed() {
        handleBackAction()
    }

    /** Dipakai baik oleh tombol back fisik maupun tombol back di toolbar. */
    private fun handleBackAction() {
        if (adapter.selectionMode) {
            adapter.clearSelection()
            updateSelectionBarVisibility()
            return
        }
        if (pasteBar.visibility == View.VISIBLE) {
            clipboardFiles = emptyList()
            pasteBar.visibility = View.GONE
            return
        }
        if (!goBackOneLevel()) {
            super.onBackPressed()
        }
    }

    // ---------------------------------------------------------------------
    // 4. SEARCH
    // ---------------------------------------------------------------------
    private fun setupSearch() {
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    searchJob?.cancel()
                    progressBar.visibility = View.GONE
                    adapter.updateData(currentEntries)
                } else {
                    runSearch(query)
                }
            }
        })
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300) // debounce - tunggu user berhenti mengetik sebelum scan folder
            if (!isActive) return@launch
            progressBar.visibility = View.VISIBLE
            val entries = withContext(Dispatchers.IO) {
                val results = mutableListOf<File>()
                FileOperations.searchRecursively(currentDir, query, results)
                results.map { FileEntry(it) }
            }
            if (!isActive) return@launch
            progressBar.visibility = View.GONE
            adapter.updateData(entries)
        }
    }

    // ---------------------------------------------------------------------
    // 5. SELECTION MODE
    // ---------------------------------------------------------------------
    private fun setupSelectionBar() {
        findViewById<TextView>(R.id.btnRenameBatch).setOnClickListener { showRenameBatchDialog() }
        findViewById<TextView>(R.id.btnDelete).setOnClickListener { confirmDeleteSelected() }
        findViewById<TextView>(R.id.btnCopy).setOnClickListener { setClipboard(cut = false) }
        findViewById<TextView>(R.id.btnMove).setOnClickListener { setClipboard(cut = true) }
        findViewById<TextView>(R.id.btnZip).setOnClickListener { zipSelected() }
    }

    private fun updateSelectionBarVisibility() {
        selectionBar.visibility = if (adapter.selectionMode) View.VISIBLE else View.GONE
    }

    private fun showRenameBatchDialog() {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_batch, null)
        val patternInput = dialogView.findViewById<EditText>(R.id.patternInput)
        val startNumberInput = dialogView.findViewById<EditText>(R.id.startNumberInput)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Terapkan") { _, _ ->
                val pattern = patternInput.text.toString().ifBlank { "File_{n}" }
                val start = startNumberInput.text.toString().toIntOrNull() ?: 1
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val successCount = withContext(Dispatchers.IO) {
                        FileOperations.batchRename(selected, pattern, start).count { it.second }
                    }
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "$successCount/${selected.size} file berhasil di-rename", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDir)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteSelected() {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Hapus ${selected.size} item?")
            .setMessage("Item yang dihapus tidak bisa dikembalikan.")
            .setPositiveButton("Hapus") { _, _ ->
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val successCount = withContext(Dispatchers.IO) {
                        selected.count { FileOperations.deleteRecursively(it) }
                    }
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "$successCount/${selected.size} item dihapus", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDir)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun zipSelected() {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return
        val zipName = "Archive_${System.currentTimeMillis()}.zip"
        val destZip = File(currentDir, zipName)
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                FileOperations.zipFiles(selected, destZip)
            }
            progressBar.visibility = View.GONE
            Toast.makeText(
                this@MainActivity,
                if (success) "Berhasil membuat $zipName" else "Gagal membuat ZIP",
                Toast.LENGTH_SHORT
            ).show()
            loadDirectory(currentDir)
        }
    }

    // ---------------------------------------------------------------------
    // 6. CLIPBOARD (copy / move -> paste di folder tujuan)
    // ---------------------------------------------------------------------
    private fun setClipboard(cut: Boolean) {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return
        clipboardFiles = selected
        clipboardIsCut = cut
        adapter.clearSelection()
        updateSelectionBarVisibility()
        pasteInfo.text = "${selected.size} item siap ditempel (${if (cut) "pindah" else "salin"})"
        pasteBar.visibility = View.VISIBLE
    }

    private fun setupPasteBar() {
        findViewById<TextView>(R.id.btnPasteHere).setOnClickListener { pasteHere() }
        findViewById<TextView>(R.id.btnCancelPaste).setOnClickListener {
            clipboardFiles = emptyList()
            pasteBar.visibility = View.GONE
        }
    }

    private fun pasteHere() {
        if (clipboardFiles.isEmpty()) return
        val targetDir = currentDir
        val filesToProcess = clipboardFiles
        val isCut = clipboardIsCut
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val successCount = withContext(Dispatchers.IO) {
                var count = 0
                filesToProcess.forEach { file ->
                    val ok = if (isCut) FileOperations.moveFile(file, targetDir)
                             else FileOperations.copyRecursively(file, targetDir)
                    if (ok) count++
                }
                count
            }
            progressBar.visibility = View.GONE
            pasteBar.visibility = View.GONE
            clipboardFiles = emptyList()
            Toast.makeText(this@MainActivity, "$successCount/${filesToProcess.size} item berhasil ditempel", Toast.LENGTH_SHORT).show()
            loadDirectory(currentDir)
        }
    }

    // ---------------------------------------------------------------------
    // 7. PREVIEW FILE
    // ---------------------------------------------------------------------
    private fun previewFile(entry: FileEntry) {
        when (entry.typeCategory()) {
            FileType.IMAGE -> previewImage(entry.file)
            FileType.TEXT -> previewText(entry.file)
            FileType.PDF -> previewPdf(entry.file)
            else -> openWithExternalApp(entry.file)
        }
    }

    private fun previewImage(file: File) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.imagePreview)
        imageView.setImageURI(Uri.fromFile(file))
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun previewText(file: File) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_preview, null)
        val contentView = dialogView.findViewById<TextView>(R.id.textPreviewContent)
        try {
            val text = file.readText().take(5000)
            contentView.text = text
        } catch (e: Exception) {
            contentView.text = "Gagal membaca file: ${e.message}"
        }
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setView(dialogView)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun previewPdf(file: File) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val bitmap = android.graphics.Bitmap.createBitmap(
                page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.imagePreview)
            imageView.setImageBitmap(bitmap)

            AlertDialog.Builder(this)
                .setTitle("${file.name} (halaman 1 dari ${renderer.pageCount})")
                .setView(dialogView)
                .setPositiveButton("Tutup") { _, _ ->
                    page.close()
                    renderer.close()
                    pfd.close()
                }
                .setOnCancelListener {
                    page.close()
                    renderer.close()
                    pfd.close()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            openWithExternalApp(file)
        }
    }

    private fun openWithExternalApp(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------------
    // 8. MENU TOOLBAR
    // ---------------------------------------------------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_folder -> { showNewFolderDialog(); true }
            R.id.action_folder_size -> { showFolderSizeDialog(); true }
            R.id.action_sort -> { toggleSort(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNewFolderDialog() {
        val input = EditText(this)
        input.hint = "Nama folder baru"
        AlertDialog.Builder(this)
            .setTitle("Buat Folder Baru")
            .setView(input)
            .setPositiveButton("Buat") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newFolder = File(currentDir, name)
                    if (newFolder.mkdirs()) loadDirectory(currentDir)
                    else Toast.makeText(this, "Gagal membuat folder", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showFolderSizeDialog() {
        progressBar.visibility = View.VISIBLE
        val target = currentDir
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) { FileOperations.folderSize(target) }
            progressBar.visibility = View.GONE
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Ukuran Folder")
                .setMessage("${target.name}: ${FileOperations.formatSize(size)}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun toggleSort() {
        sortByDateDesc = !sortByDateDesc
        Toast.makeText(
            this,
            if (sortByDateDesc) "Diurutkan berdasarkan tanggal terbaru" else "Diurutkan berdasarkan nama",
            Toast.LENGTH_SHORT
        ).show()
        loadDirectory(currentDir)
    }
}
