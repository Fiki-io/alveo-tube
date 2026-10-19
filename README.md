# Alveo Tube

Alveo Tube adalah pemutar media Android minimalis dan ringan yang mengimplementasikan arsitektur **Hybrid Attestation Provider** berbasis *Headless WebView Sniffer*.

## Arsitektur Inti

* **Zero Regex Obfuscation**: Tidak lagi mem-parsing string `base.js` YouTube yang rapuh terhadap update.
* **Resilient Native Sniffer**: Memanfaatkan Chromium engine bawaan Android (`android.webkit.WebView`) di memori terisolasi untuk menyelesaikan kalkulasi signature dan token kriptografis secara organik.
* **Low Overhead & Battery Saving**: Menggunakan antarmuka XML View tradisional dengan ViewBinding (tanpa overhead Compose), serta mendukung mode hemat daya *Audio-Only*.
* **Modern Media Engine**: Menggunakan Google AndroidX Media3 ExoPlayer resmi untuk streaming dan buffering adaptif.

## Fitur Versi Uji Coba (Lean MVP)

1. **Pencarian Media**:
   - Mendukung kata kunci pencarian (judul lagu, artis, video).
   - Mendukung input langsung berupa URL YouTube (`youtu.be/...` atau `youtube.com/watch?v=...`) atau Video ID 11 karakter.
2. **Pemutaran Fleksibel**:
   - Mode Video (MP4 player view).
   - Mode Audio Saja (layar hemat baterai).
3. **Panel Diagnostik Stream**:
   - Menampilkan status koneksi, latensi ekstraksi (ms), MIME type, dan ITAG secara transparan.

## Lisensi

Didistribusikan di bawah lisensi MIT.
