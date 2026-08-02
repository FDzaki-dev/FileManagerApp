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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    // Batch-2: seluruh state navigasi (folder aktif, sort, query search,
    // clipboard) sekarang dipegang MainViewModel + SavedStateHandle supaya
    // bertahan saat rotasi layar maupun process death. Activity hanya
    // membaca/menampilkan, bukan sumber kebenaran state lagi.
    private val viewModel: MainViewModel by viewModels()

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
        observeViewModel()
        restorePasteBarIfNeeded()
        checkPermissionAndLoad()
    }

    // ---------------------------------------------------------------------
    // OBSERVASI STATE DARI VIEWMODEL (Batch-2)
    // ---------------------------------------------------------------------
    private fun observeViewModel() {
        viewModel.entries.observe(this) { entries ->
            adapter.updateData(entries)
        }
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.currentDirPath.observe(this) { path ->
            breadcrumb.text = path
            // Selection mode selalu berakhir tiap kali pindah folder (perilaku sama seperti sebelumnya)
            adapter.clearSelection()
            updateSelectionBarVisibility()
        }
    }

    /** Kalau clipboard masih terisi dari sebelum rotasi/process death, tampilkan lagi paste bar-nya. */
    private fun restorePasteBarIfNeeded() {
        val files = viewModel.clipboardFiles
        if (files.isNotEmpty()) {
            pasteInfo.text = "${files.size} item siap ditempel (${if (viewModel.clipboardIsCut) "pindah" else "salin"})"
            pasteBar.visibility = View.VISIBLE
        }
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
            viewModel.ensureLoaded()
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
    // Catatan Batch-2: logika listing folder (loadDirectory) & update breadcrumb
    // sudah dipindah ke MainViewModel supaya cancellable job & hasilnya
    // survive rotasi layar. Activity tinggal panggil viewModel.loadDirectory(dir)
    // dan observe hasilnya lewat observeViewModel().

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
            viewModel.loadDirectory(entry.file)
        } else {
            previewFile(entry)
        }
    }

    private fun onEntryLongClick(entry: FileEntry) {
        adapter.toggleSelection(entry)
        updateSelectionBarVisibility()
    }

    private fun goBackOneLevel(): Boolean {
        val current = viewModel.currentDir
        val parent = current.parentFile
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (parent != null && current.absolutePath != rootPath) {
            viewModel.loadDirectory(parent)
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
            viewModel.clearClipboard()
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
        // Kembalikan teks query yang tersimpan (kalau ada) SEBELUM listener
        // dipasang, supaya tidak memicu pencarian ulang yang tidak perlu.
        if (viewModel.searchQuery.isNotEmpty()) {
            searchBox.setText(viewModel.searchQuery)
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    viewModel.clearSearch()
                } else {
                    viewModel.runSearch(query)
                }
            }
        })
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
                viewModel.renameSelected(selected, pattern, start) { successCount, total ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "$successCount/$total file berhasil di-rename", Toast.LENGTH_SHORT).show()
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
                viewModel.deleteSelected(selected) { successCount, total ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "$successCount/$total item dihapus", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Batch-4: dialog buat arsip dengan nama custom, password opsional (AES),
    // dan pilihan level kompresi - menggantikan zip instan tanpa opsi.
    private fun zipSelected() {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_archive, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.archiveNameInput)
        val checkPassword = dialogView.findViewById<android.widget.CheckBox>(R.id.checkUsePassword)
        val passwordInput = dialogView.findViewById<EditText>(R.id.archivePasswordInput)
        val levelGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.compressionLevelGroup)

        val defaultName = if (selected.size == 1) selected[0].nameWithoutExtension else "Archive_${System.currentTimeMillis()}"
        nameInput.setText(defaultName)

        checkPassword.setOnCheckedChangeListener { _, checked ->
            passwordInput.visibility = if (checked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_create_archive) { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { defaultName }
                val password = if (checkPassword.isChecked) passwordInput.text.toString() else null
                val level = when (levelGroup.checkedRadioButtonId) {
                    R.id.levelSimpan -> ArchiveRepository.Level.SIMPAN
                    R.id.levelCepat -> ArchiveRepository.Level.CEPAT
                    R.id.levelMaksimal -> ArchiveRepository.Level.MAKSIMAL
                    else -> ArchiveRepository.Level.NORMAL
                }
                progressBar.visibility = View.VISIBLE
                viewModel.createArchive(selected, name, password, level) { success, zipName ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        if (success) "Berhasil membuat $zipName" else "Gagal membuat arsip",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Batch-4: dialog ekstrak - dipanggil saat user tap file .zip. Cek dulu
    // apakah arsip terkunci password sebelum menampilkan kolom password.
    private fun showExtractArchiveDialog(archiveFile: File) {
        viewModel.checkArchivePassword(archiveFile) { needsPassword ->
            val dialogView = layoutInflater.inflate(R.layout.dialog_extract_archive, null)
            val nameView = dialogView.findViewById<TextView>(R.id.extractArchiveName)
            val destGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.extractDestGroup)
            val passwordInput = dialogView.findViewById<EditText>(R.id.extractPasswordInput)

            nameView.text = "Ekstrak: ${archiveFile.name}"
            passwordInput.visibility = if (needsPassword) View.VISIBLE else View.GONE

            AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_extract) { _, _ ->
                    val destDir = if (destGroup.checkedRadioButtonId == R.id.optExtractHere) {
                        archiveFile.parentFile ?: viewModel.currentDir
                    } else {
                        File(archiveFile.parentFile ?: viewModel.currentDir, archiveFile.nameWithoutExtension)
                    }
                    val password = if (needsPassword) passwordInput.text.toString() else null
                    progressBar.visibility = View.VISIBLE
                    viewModel.extractArchive(archiveFile, destDir, password) { status ->
                        progressBar.visibility = View.GONE
                        val message = when (status) {
                            ArchiveRepository.ExtractStatus.SUCCESS -> "Berhasil diekstrak ke ${destDir.name}"
                            ArchiveRepository.ExtractStatus.WRONG_PASSWORD -> "Password salah"
                            ArchiveRepository.ExtractStatus.NEEDS_PASSWORD -> "Arsip ini butuh password"
                            ArchiveRepository.ExtractStatus.FAILED -> "Gagal mengekstrak arsip"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    // ---------------------------------------------------------------------
    // 6. CLIPBOARD (copy / move -> paste di folder tujuan)
    // ---------------------------------------------------------------------
    private fun setClipboard(cut: Boolean) {
        val selected = adapter.getSelectedFiles()
        if (selected.isEmpty()) return
        viewModel.setClipboard(selected, cut)
        adapter.clearSelection()
        updateSelectionBarVisibility()
        pasteInfo.text = "${selected.size} item siap ditempel (${if (cut) "pindah" else "salin"})"
        pasteBar.visibility = View.VISIBLE
    }

    private fun setupPasteBar() {
        findViewById<TextView>(R.id.btnPasteHere).setOnClickListener { pasteHere() }
        findViewById<TextView>(R.id.btnCancelPaste).setOnClickListener {
            viewModel.clearClipboard()
            pasteBar.visibility = View.GONE
        }
    }

    private fun pasteHere() {
        progressBar.visibility = View.VISIBLE
        val started = viewModel.pasteClipboard { successCount, total ->
            progressBar.visibility = View.GONE
            pasteBar.visibility = View.GONE
            Toast.makeText(this, "$successCount/$total item berhasil ditempel", Toast.LENGTH_SHORT).show()
        }
        if (!started) progressBar.visibility = View.GONE // clipboard ternyata kosong
    }

    // ---------------------------------------------------------------------
    // 7. PREVIEW FILE
    // ---------------------------------------------------------------------
    private fun previewFile(entry: FileEntry) {
        when (entry.typeCategory()) {
            FileType.IMAGE -> previewImage(entry.file)
            FileType.TEXT -> previewText(entry.file)
            FileType.PDF -> previewPdf(entry.file)
            FileType.ARCHIVE -> {
                // Batch-4: baru format ZIP yang bisa diekstrak. Format arsip lain
                // (7z/rar/tar/dll) menyusul di batch berikutnya sesuai roadmap.
                if (entry.isExtractableArchive()) {
                    showExtractArchiveDialog(entry.file)
                } else {
                    Toast.makeText(
                        this,
                        "Format .${entry.extension()} belum didukung untuk ekstrak, menyusul di update berikutnya",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
                    viewModel.createFolder(name) { success ->
                        if (!success) Toast.makeText(this, "Gagal membuat folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showFolderSizeDialog() {
        progressBar.visibility = View.VISIBLE
        viewModel.computeFolderSize { formattedSize, folderName ->
            progressBar.visibility = View.GONE
            AlertDialog.Builder(this)
                .setTitle("Ukuran Folder")
                .setMessage("$folderName: $formattedSize")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun toggleSort() {
        val nowDateDesc = viewModel.toggleSort()
        Toast.makeText(
            this,
            if (nowDateDesc) "Diurutkan berdasarkan tanggal terbaru" else "Diurutkan berdasarkan nama",
            Toast.LENGTH_SHORT
        ).show()
    }
}
