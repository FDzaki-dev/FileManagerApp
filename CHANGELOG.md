# Changelog

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
