# Laporan Implementasi Fitur Terjemahan Manga pada Mihon

Dokumen ini merinci implementasi fitur terjemahan manga pada aplikasi Android open source Mihon (`https://github.com/MuhamadSyabitHidayattulloh/mihon.git`). Fitur ini memungkinkan pengguna untuk menerjemahkan teks yang terdeteksi pada halaman komik menggunakan ML Kit Text Recognition v2 dan Google Translate berbasis web.

## 1. Analisis Struktur Kode

Proyek Mihon di-clone dari repositori GitHub yang diberikan. Struktur proyek dianalisis untuk mengidentifikasi lokasi yang sesuai untuk penambahan fitur baru. Fokus utama adalah pada modul `app` dan `ui/reader` karena fitur terjemahan akan diintegrasikan langsung ke dalam tampilan pembaca komik.

## 2. Implementasi ML Kit Text Recognition v2

ML Kit Text Recognition v2 digunakan untuk mendeteksi teks dari gambar halaman komik. Sebuah kelas baru bernama `TextRecognitionManager.kt` dibuat di `eu.kanade.tachiyomi.mlkit` untuk mengelola proses pengenalan teks. Dependensi yang diperlukan untuk ML Kit ditambahkan ke `app/build.gradle.kts`.

## 3. Implementasi Google Translate Web-Based Integration

Untuk menerjemahkan teks yang terdeteksi, integrasi dengan Google Translate berbasis web dilakukan. Sebuah kelas `GoogleTranslateManager.kt` dibuat di `eu.kanade.tachiyomi.util` yang bertanggung jawab untuk membuat permintaan HTTP ke endpoint Google Translate dan mengurai responsnya. Dependensi OkHttp dan Kotlin Coroutines ditambahkan untuk penanganan jaringan dan asinkron.

## 4. Integrasi Fitur Terjemahan ke dalam UI Aplikasi

Fitur terjemahan diintegrasikan ke dalam `ReaderActivity.kt` dan `ReaderAppBars.kt`. Sebuah opsi "Translate Page" ditambahkan ke menu overflow di `ReaderAppBars`. Ketika opsi ini dipilih, `ReaderActivity` akan mengambil bitmap halaman saat ini, meneruskannya ke `TextRecognitionManager` untuk pengenalan teks, dan kemudian menggunakan `GoogleTranslateManager` untuk menerjemahkan teks yang terdeteksi. Hasil terjemahan ditampilkan dalam dialog pop-up.

## 5. Modifikasi GitHub Actions

File GitHub Actions (`.github/workflows/build_push.yml` dan `.github/workflows/build_pull_request.yml`) dimodifikasi untuk membangun versi debug aplikasi. Ini menghilangkan kebutuhan akan secret GitHub dan proses penandatanganan APK untuk build CI/CD, sehingga memudahkan proses pengembangan dan pengujian.

## 6. Push ke GitHub

Semua perubahan di-commit ke branch baru bernama `feature/translate-manga` dan di-push ke repositori GitHub. Ini memungkinkan tinjauan kode dan penggabungan ke branch utama setelah verifikasi lebih lanjut.

## Kesimpulan

Fitur terjemahan manga telah berhasil diimplementasikan pada aplikasi Mihon, memungkinkan pengguna untuk menerjemahkan teks dalam komik secara langsung. Integrasi ML Kit dan Google Translate berbasis web menyediakan solusi yang efektif dan gratis. Perubahan pada GitHub Actions juga telah dilakukan untuk menyederhanakan proses build debug.

