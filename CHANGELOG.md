# Changelog

## v1.7-batch7
Batch-7: Palet Warna Asli (dari APK ZArchiver Pro yang diupload user)
- **Sumber data**: user upload file APK asli ZArchiver Donate. AI membongkar
  APK (unzip, murni membaca resource - TIDAK menjalankan/mendekompilasi
  kode program apapun) dan melakukan sampling piksel langsung dari
  `mipmap/ic_launcher.png` dan `drawable-nodpi/fs_*.png` pakai Pillow.
  Ini data warna AKURAT, bukan tebakan seperti Batch-6.
- `colors.xml`: `primary` #1F6F54→**#00B2D9** (cyan, warna brand asli dari
  ikon launcher), `primary_dark` →**#00829F**, `accent`/folder
  →**#D97800** (amber, konsisten di semua varian folder yang disample).
- Warna ikon per tipe file baru (`icon_folder`, `icon_pdf`, `icon_audio`,
  `icon_image`, `icon_video`, `icon_archive`, `icon_file`), masing-masing
  dicocokkan ke hasil sampling: PDF merah #9C0F0F, audio hijau #0E650E,
  gambar biru #347ADB, video abu netral (ikon asli tidak berwarna
  mencolok), arsip abu-kebiruan (ikon asli ZArchiver untuk arsip ternyata
  netral, BUKAN oranye seperti asumsi Batch-4/desain awal kita).
- **Batas yang disadari & disengaja**: bentuk/path vector ikon (`ic_folder.
  xml` dkk.) TETAP desain original proyek ini, TIDAK menjiplak
  path/artwork dari PNG asli ZArchiver - itu akan jadi pelanggaran hak
  cipta aset. Yang diambil HANYA nilai warna (fakta/data, bukan karya
  cipta). Ikon launcher aplikasi ini (mipmap/ic_launcher project sendiri)
  JUGA tidak diganti dengan punya ZArchiver - itu identitas brand/logo
  mereka, di luar cakupan "warna UI" yang wajar untuk direplikasi.
- Tidak ada perubahan logika/fungsi - murni warna.

## v1.6-batch6
Batch-6: Redesign List File Utama — tahap 1 dari cakupan "identik ZArchiver Pro"
- **Catatan jujur soal cakupan**: batch ini HANYA elemen struktural yang bisa
  ditentukan tanpa referensi visual presisi (garis pemisah, kepadatan row,
  ukuran ikon). Warna/hex eksak TIDAK diklaim identik 100% karena AI tidak
  bisa mengekstrak nilai piksel dari hasil pencarian gambar - kalau user
  kirim screenshot asli ZArchiver Pro, batch berikutnya bisa jauh lebih
  presisi soal warna/ikon.
- `item_file.xml`: padding row 10dp→8dp, tambah `minHeight=56dp` (list
  lebih dense/rapat, konsisten dengan gaya file manager minimalis).
- `drawable/divider_line.xml` baru: garis pemisah tipis 1dp antar row.
- `MainActivity.setupRecyclerView()`: pasang `DividerItemDecoration` yang
  dipakai bersama oleh listing folder disk MAUPUN browse arsip (Batch-5) -
  satu RecyclerView yang sama, jadi otomatis konsisten di kedua mode tanpa
  perubahan di FileAdapter/ArchiveEntryAdapter.
- Tidak ada perubahan logika/behavior sama sekali - murni visual.

## v1.5.1-batch5.1
Batch-5.1: Muncul di "Buka dengan" (Suggested App) untuk ZIP & berkas mainstream
- `AndroidManifest.xml`: tambah 3 intent-filter `ACTION_VIEW` di `MainActivity`
  supaya app terdaftar sebagai pilihan di dialog "Buka dengan" sistem Android
  saat user tap file dari app lain (file manager lain, browser, dll):
  1. Arsip: `application/zip`, `application/x-zip-compressed`,
     `application/octet-stream` (banyak downloader kasih mimeType generik
     untuk zip, jadi octet-stream disertakan supaya tetap kedeteksi).
  2. Teks/dokumen: `text/plain`, `text/xml`, `application/xml`,
     `application/json`, `application/pdf`.
  3. Media: `image/*`, `video/*`, `audio/*`.
- App sekarang benar-benar MEMBUKA file yang di-tap (bukan cuma terdaftar
  lalu tampil folder kosong): intent `ACTION_VIEW` masuk di-resolve jadi
  `java.io.File` asli (scheme `file` langsung, scheme `content` best-effort
  lewat kolom `MediaStore.MediaColumns.DATA`), folder induknya dimuat, lalu
  file tersebut otomatis di-"klik" via `onEntryClick()` yang sudah ada
  (preview biasa untuk gambar/teks/PDF, browse mode Batch-5 untuk zip) -
  tidak ada logika buka-file baru yang duplikat.
- **Batasan yang disadari & didokumentasikan (bukan bug)**: resolusi
  `content://` lewat kolom DATA tidak dijamin berhasil di semua provider
  (kolom ini sudah deprecated di API modern, beberapa provider pihak
  ketiga tidak mengekspos path fisik sama sekali). Kalau gagal resolve,
  app menampilkan pesan graceful ("cari manual lewat File Manager") -
  tidak crash, cuma tidak auto-buka.
- Perilaku buka app dari launcher (ikon di home screen) 100% tidak
  berubah - hanya jalur baru (dibuka dari app lain) yang ditambah.

## v1.5-batch5
Batch-5: Browse Isi Arsip Tanpa Ekstrak (virtual folder ala ZArchiver)
- **Perubahan perilaku yang disengaja**: tap file `.zip` sekarang membuka
  isi arsip sebagai folder virtual (browse), BUKAN langsung membuka dialog
  ekstrak seperti Batch-4. Ini permintaan eksplisit di batch ini.
- Breadcrumb saat browsing arsip menampilkan path di dalam arsip
  (`.../nama_arsip.zip/folder/sub`), tap folder virtual masuk ke
  dalamnya, tombol back naik satu level lalu keluar total ke listing
  folder biasa saat sudah di akar arsip.
- Tap file di dalam arsip menawarkan "Ekstrak Item Ini" (ekstrak SATU
  file/folder saja ke folder aktif saat ini di storage asli), tanpa
  perlu mengekstrak seluruh arsip.
- **Tidak ada fitur Batch-4 yang hilang**: dialog "Buat Arsip"
  (nama/password/level kompresi) dan dialog ekstrak-semua-dengan-pilihan-
  tujuan tetap ada 100% sama, kini diakses lewat menu toolbar
  "Ekstrak Semua ke Folder Ini" saat browsing (memanggil ulang fungsi
  dialog Batch-4 yang sama, tidak ditulis ulang).
- Format arsip lain (7z/rar/tar/dll) masih belum didukung sama sekali
  (sesuai roadmap), termasuk untuk browsing - tetap dapat info "belum
  didukung" seperti sebelumnya.
- Adapter baru `ArchiveEntryAdapter` dibuat TERPISAH dari `FileAdapter`
  supaya listing folder biasa di disk 0% tersentuh/berisiko regresi.
- `ArchiveRepository`/`FileRepository`/`MainViewModel` Batch-3/4
  (Repository Pattern, state persistence, createZip/extractZip) tidak
  diubah sama sekali - browse ini murni penambahan fungsi baru
  (`listDirectory`, `extractEntry`) di sampingnya.
- Search box otomatis nonaktif selama mode browse arsip (belum ada
  pencarian di dalam arsip di batch ini).

## v1.4-batch4
Batch-4: Archive Engine - langkah pertama menuju "identik ZArchiver Pro"
- Tambah `ArchiveRepository` baru (mengikuti pola Repository Pattern
  Batch-3) sebagai satu-satunya lapisan yang menyentuh library zip4j.
- **Buat Arsip**: tombol "Kompres ZIP" sekarang membuka dialog buat arsip
  dengan nama custom, opsi password (dienkripsi AES-256), dan 4 level
  kompresi ala ZArchiver: Simpan (tanpa kompresi)/Cepat/Normal/Maksimal.
- **Ekstrak Arsip**: fitur baru - tap file `.zip` sekarang membuka dialog
  ekstrak (pilihan folder baru sesuai nama arsip atau ekstrak ke folder
  ini), otomatis minta password kalau arsip terkunci, dan validasi
  password salah.
- Ikon baru untuk tipe file arsip (`FileType.ARCHIVE`) supaya file
  zip/7z/rar/tar/dll langsung kebeda visualnya dari file biasa di listing.
- **Cakupan sengaja dibatasi** (sesuai aturan pecah batch kecil): baru
  format **ZIP** yang bisa dibuat & diekstrak penuh. Format 7z/rar/tar/
  gzip/dll baru dikenali ikonnya saja - tap file itu akan kasih info
  "belum didukung" (bukan crash), menyusul di batch berikutnya.
- Belum ada browse isi arsip tanpa ekstrak (virtual folder ala ZArchiver)
  - masih di roadmap batch selanjutnya.
- Tidak ada fitur v1.3 yang dihapus/berubah: browser folder, breadcrumb,
  preview, rename batch, search, copy/move/delete, ukuran folder, buat
  folder, sort - semua tetap sama persis.
- `FileOperations.zipFiles()` (zip tanpa opsi, Batch-1) tetap dipertahankan
  di kode, hanya sudah tidak dipanggil dari tombol UI (digantikan alur
  dialog baru yang lebih lengkap).

## v1.3-batch3
Batch-3: Repository Pattern
- Tambah `FileRepository` sebagai satu-satunya lapisan yang menyentuh disk/File
  API (listing folder, search, rename batch, hapus, copy/move, zip, ukuran
  folder, buat folder).
- `MainActivity` tidak lagi memanggil `FileOperations` atau File I/O mentah
  sama sekali - semua aksi (rename, delete, zip, paste, buat folder, ukuran
  folder) sekarang lewat fungsi baru di `MainViewModel` yang meneruskan ke
  `FileRepository`, lalu melapor hasilnya ke Activity lewat callback untuk
  ditampilkan sebagai Toast/dialog.
- `MainViewModel` juga tidak lagi menulis logika listing+sort secara inline;
  semua lewat `repository.listDirectory()` / `repository.search()`.
- Efeknya murni arsitektural (Separation of Concerns) - tidak ada perubahan
  perilaku yang terlihat user, semua fitur dari v1.2-batch2 dipertahankan
  identik: state persistence saat rotasi/process death, listing, sort,
  search, preview, rename batch, copy/move/delete/zip, ukuran folder, buat
  folder.
- Logika inti `FileOperations` (Batch-1) tidak diubah sama sekali, hanya
  dibungkus oleh Repository.

## v1.2-batch2
Batch-2: State Persistence (ViewModel + SavedStateHandle)
- Seluruh state navigasi (folder aktif, breadcrumb, mode sort, query search,
  isi clipboard copy/move) dipindah dari field mentah di `MainActivity` ke
  `MainViewModel` baru yang memakai `SavedStateHandle`.
- Efek yang terlihat user: folder aktif, hasil sort, teks di kolom search,
  dan clipboard "Tempel di Sini" sekarang TIDAK reset lagi saat layar
  dirotasi atau saat Activity dipulihkan sistem dari background (process
  death). Sebelumnya semua state ini otomatis kembali ke folder
  penyimpanan utama setiap kali itu terjadi.
- Listing folder tidak di-scan ulang dari disk pada rotasi layar (hanya
  di-scan ulang kalau memang berpindah folder/process death), agar lebih
  hemat.
- Semua fitur Batch-1 (coroutine lifecycle-safe, search debounce
  300ms+cancel job lama) dipertahankan utuh, hanya dipindah host-nya dari
  `lifecycleScope` (Activity) ke `viewModelScope` (ViewModel) untuk
  bagian listing folder & search.
- Tidak ada fitur yang dihapus: browser folder, preview, rename batch,
  search, copy/move/delete/zip, ukuran folder, buat folder, sort — semua
  masih sama seperti v1.1-batch1.

## v1.1-batch1
Batch-1: Threading & Lifecycle Safety
- Semua operasi background (list folder, search, delete, rename batch, copy/move, zip,
  hitung ukuran folder) dipindah dari `Thread{}.start()` + `runOnUiThread` mentah ke
  coroutine `lifecycleScope` — otomatis berhenti aman kalau Activity di-destroy di tengah proses.
- Search sekarang debounce 300ms + cancel job lama tiap ketikan baru, mencegah hasil
  muncul tidak urut (race condition).
- Tidak ada perubahan behavior yang terlihat user.

## v1.0
Setup awal - MVP File Manager
- Browser folder + breadcrumb, akses penuh storage (MANAGE_EXTERNAL_STORAGE)
- Preview cepat: thumbnail gambar, quick-view teks, render halaman pertama PDF
- Rename batch dengan pola `{n}`
- Search rekursif di folder aktif
- Copy, move (clipboard + "Tempel di Sini"), delete, kompres ke ZIP
- Kalkulasi ukuran folder, buat folder baru, sort nama/tanggal
