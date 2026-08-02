# File Manager (Offline)

Aplikasi file manager Android offline-only. Dibangun dengan prompt-driven development.

## Fitur MVP
- Browser folder dengan breadcrumb, akses penuh storage (MANAGE_EXTERNAL_STORAGE)
- Preview cepat: thumbnail gambar, quick-view teks, render halaman pertama PDF
- Rename batch dengan pola `{n}` untuk nomor urut
- Search rekursif di folder aktif (berjalan di background thread)
- Operasi dasar: copy, move (via clipboard + "Tempel di Sini"), delete, kompres ke ZIP
- Kalkulasi ukuran folder, buat folder baru, sort nama/tanggal

## Struktur Proyek
```
FileManagerApp/
├── app/
│   ├── src/main/java/com/mahasiswa/filemanager/
│   │   ├── MainActivity.kt      (UI + logic utama, dibagi per region)
│   │   ├── FileAdapter.kt       (RecyclerView adapter + selection mode)
│   │   ├── FileOperations.kt    (copy/move/delete/zip/rename/search)
│   │   └── FileEntry.kt         (model data + kategori tipe file)
│   ├── src/main/res/            (layout, drawable, values)
│   └── build.gradle             (signing config release)
├── release.keystore             (keystore asli, JANGAN dihapus)
├── .github/workflows/build.yml  (CI build APK release otomatis)
└── build.gradle / settings.gradle
```

## Build
CI (GitHub Actions) otomatis build APK release setiap push ke `main`, memakai
secret: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. APK hasil build ada di tab
Actions > Artifacts.
