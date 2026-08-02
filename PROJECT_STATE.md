# PROJECT_STATE.md — File Manager (Offline)

> Wajib dibaca AI sebelum melanjutkan proyek ini di sesi baru mana pun.

## Status Terakhir
- **Versi/Batch selesai:** v1.4-batch4 (Archive Engine)
- **Package:** `com.mahasiswa.filemanager`
- **Belum ada file ini di repo sebelumnya** — dibuat pada sesi ini (retroaktif,
  disusun ulang dari CHANGELOG.md v1.0 s/d v1.4-batch4). Riwayat di bawah
  adalah rekonstruksi dari changelog, bukan log insiden real-time asli.

## Riwayat Kronologis (dari CHANGELOG.md, jangan dihapus)
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
- **Format arsip**: hanya ZIP yang didukung penuh (create+extract, AES-256
  optional password, 4 level kompresi via zip4j). Format lain by design
  belum dikerjakan — bukan bug.

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
├── FileAdapter.kt        — RecyclerView adapter + selection mode
└── FileEntry.kt          — model data + kategori tipe file (termasuk
                             FileType.ARCHIVE, baru di v1.4-batch4)
```

## Belum Dikerjakan / Roadmap
- Browse isi arsip tanpa ekstrak (virtual folder ala ZArchiver)
- Dukungan penuh format 7z/rar/tar/gzip (saat ini hanya ikon dikenali)
