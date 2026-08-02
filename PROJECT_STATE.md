# PROJECT_STATE.md — File Manager (Offline)

> Wajib dibaca AI sebelum melanjutkan proyek ini di sesi baru mana pun.

## Status Terakhir
- **Versi/Batch selesai:** v1.5.1-batch5.1 (Suggested App / ACTION_VIEW)
- **Package:** `com.mahasiswa.filemanager`
- File ini pertama dibuat retroaktif di sesi sebelumnya (disusun ulang dari
  CHANGELOG.md v1.0 s/d v1.4-batch4). Mulai v1.5-batch5, riwayat ditulis
  live per batch.

## Riwayat Kronologis (dari CHANGELOG.md, jangan dihapus)
- **v1.5.1-batch5.1** — Suggested App: `AndroidManifest.xml` dapat 3
  intent-filter `ACTION_VIEW` baru (arsip zip, teks/dokumen, media) supaya
  app muncul di dialog "Buka dengan" sistem saat user tap file dari app
  lain. Intent masuk di-resolve jadi `File` asli lalu dibuka otomatis lewat
  `onEntryClick()` yang sudah ada. Resolusi `content://` best-effort lewat
  kolom `MediaStore.MediaColumns.DATA` - didokumentasikan sebagai batasan
  yang disadari (bukan bug) kalau provider pihak ketiga tidak mengekspos
  path fisik. Tidak ada perubahan pada alur buka-dari-launcher.
- **v1.5-batch5** — Browse Isi Arsip Tanpa Ekstrak: tap file `.zip` sekarang
  membuka isi arsip sebagai folder virtual (browse), bukan langsung dialog
  ekstrak. Breadcrumb & navigasi back khusus mode arsip. Tap file di dalam
  arsip -> "Ekstrak Item Ini" (satu file/folder saja). Dialog "Buat Arsip"
  dan "Ekstrak Semua (pilihan tujuan)" Batch-4 dipertahankan 100%, kini
  diakses lewat menu toolbar "Ekstrak Semua ke Folder Ini" saat browsing.
  Adapter baru `ArchiveEntryAdapter` (terpisah dari `FileAdapter`) supaya
  listing folder di disk tidak tersentuh sama sekali. Ini permulaan dari
  konteks yang lebih besar: cakupan "identik ZArchiver Pro" yang mencakup
  redesign UI/UX seluruh aplikasi, direncanakan bertahap di Batch-6 dst
  (lihat bagian Roadmap).
- **v1.0** — Setup awal MVP: browser folder+breadcrumb (akses penuh via
  MANAGE_EXTERNAL_STORAGE), preview gambar/teks/PDF (halaman pertama),
  rename batch pola `{n}`, search rekursif, copy/move (clipboard+"Tempel di
  Sini")/delete/kompres ZIP, kalkulasi ukuran folder, buat folder, sort.
- **v1.1-batch1** — Threading & Lifecycle Safety: operasi background pindah
  dari `Thread{}.start()`+`runOnUiThread` mentah ke coroutine
  `lifecycleScope`; search debounce 300ms + cancel job lama. Tidak ada
  perubahan behavior terlihat user.
- **v1.2-batch2** — State Persistence: state navigasi (folder aktif,
  breadcrumb, sort, query search, clipboard) pindah ke `MainViewModel`
  (`SavedStateHandle`) — tidak reset lagi saat rotasi/process death.
  Listing folder pindah host ke `viewModelScope`.
- **v1.3-batch3** — Repository Pattern: `FileRepository` jadi satu-satunya
  lapisan yang menyentuh disk/File API. `MainActivity` tidak lagi memanggil
  `FileOperations`/File I/O mentah — semua lewat `MainViewModel` →
  `FileRepository` → callback. Murni arsitektural, tanpa perubahan perilaku.
- **v1.4-batch4** — Archive Engine: `ArchiveRepository` baru (satu-satunya
  lapisan yang menyentuh library zip4j). Dialog "Buat Arsip" (nama custom,
  password AES-256 opsional, 4 level kompresi: Simpan/Cepat/Normal/
  Maksimal). Dialog "Ekstrak Arsip" (tap file `.zip`, folder baru/ekstrak
  di sini, auto-minta password kalau terkunci). Ikon baru untuk
  `FileType.ARCHIVE`. **Cakupan sengaja dibatasi ke format ZIP saja** —
  7z/rar/tar/gzip baru dikenali ikonnya, tap file itu kasih info "belum
  didukung" (bukan crash). Belum ada browse isi arsip tanpa ekstrak
  (virtual folder ala ZArchiver) — di roadmap batch berikutnya.
  `FileOperations.zipFiles()` (zip tanpa opsi, dari Batch-1) tetap ada di
  kode tapi sudah tidak dipanggil dari UI (digantikan alur dialog baru).

## Keputusan Arsitektur Utama
- **Repository Pattern** (sejak v1.3): semua akses disk/File API HANYA lewat
  `FileRepository` (operasi file umum) dan `ArchiveRepository` (khusus
  arsip/zip4j). `MainActivity` dan `MainViewModel` tidak pernah menyentuh
  File API mentah.
- **MVVM dengan SavedStateHandle** (sejak v1.2): semua state UI/navigasi
  disimpan di `MainViewModel`, bukan field mentah di Activity, agar survive
  rotasi layar & process death.
- **Coroutine lifecycle-safe** (sejak v1.1): semua operasi background pakai
  `lifecycleScope`/`viewModelScope`, tidak ada `Thread{}.start()` mentah.
- **Format arsip**: hanya ZIP yang didukung penuh (create+extract+browse,
  AES-256 optional password, 4 level kompresi via zip4j). Format lain by
  design belum dikerjakan — bukan bug.
- **Browse arsip via adapter terpisah** (sejak v1.5): `ArchiveEntryAdapter`
  tidak menyentuh/mewarisi `FileAdapter` sama sekali, supaya perubahan di
  satu tidak bisa meregresi yang lain. Struktur folder virtual arsip
  dihitung dari `FileHeader.fileName` (bukan ekstraksi fisik).
- **UI/UX target "identik ZArchiver Pro"** (disepakati saat kickoff
  Batch-5): berlaku untuk SELURUH aplikasi (file browser utama, toolbar,
  ikon, dll), dikerjakan bertahap per batch terpisah (bukan sekaligus),
  behavior/fungsi yang ada wajib tetap identik - hanya tampilan yang
  berubah. Referensi visual dicari sendiri (belum ada screenshot dari
  user).

## Struktur Package/Modul Singkat
```
com.mahasiswa.filemanager/
├── MainActivity.kt       — UI + logic utama (dibagi per region), tidak lagi
│                           akses File API langsung (semua lewat ViewModel)
├── MainViewModel.kt      — state (SavedStateHandle) + orkestrasi aksi user,
│                           delegasi ke FileRepository/ArchiveRepository
├── FileRepository.kt     — satu-satunya lapisan akses disk/File API umum
│                           (listing, search, rename batch, hapus,
│                           copy/move, ukuran folder, buat folder)
├── ArchiveRepository.kt  — satu-satunya lapisan akses zip4j (buat/ekstrak
│                           arsip ZIP, password AES-256, level kompresi)
├── FileOperations.kt     — logika inti I/O (dari Batch-1), kini dibungkus
│                           oleh Repository; zipFiles() masih ada tapi
│                           sudah tidak dipanggil dari UI
├── FileAdapter.kt        — RecyclerView adapter listing folder DISK +
│                             selection mode (TIDAK disentuh oleh v1.5)
├── ArchiveEntryAdapter.kt — RecyclerView adapter khusus isi ARSIP (baru
│                             v1.5-batch5), terpisah total dari FileAdapter
└── FileEntry.kt          — model data + kategori tipe file (termasuk
                             FileType.ARCHIVE, baru di v1.4-batch4)
```

## Belum Dikerjakan / Roadmap
- Dukungan penuh format 7z/rar/tar/gzip (saat ini hanya ikon dikenali) -
  termasuk browsing-nya
- Search di dalam isi arsip (search box otomatis nonaktif saat browse
  arsip di v1.5)
- Mode seleksi multi-item di dalam browse arsip (ArchiveEntryAdapter belum
  ada checkbox aktif)
- **Redesign UI/UX seluruh aplikasi supaya identik ZArchiver Pro** —
  disepakati dikerjakan bertahap mulai Batch-6: list file utama (row/
  ikon/warna), lalu toolbar & menu, lalu dialog-dialog, lalu tema warna
  & typography global. Behavior/fungsi wajib tetap identik di semua
  batch ini.
