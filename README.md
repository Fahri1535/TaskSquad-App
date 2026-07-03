# TaskSquad

Aplikasi manajemen tugas kelompok berbasis Android, dibuat untuk memudahkan sebuah grup/tim mendistribusikan, melacak, dan menyelesaikan tugas bersama secara real-time. Dibuat sebagai proyek akhir mata kuliah Mobile Programming.

**Dibuat oleh:** Mohamad Fahri Akbar

## Fitur Utama

- **Autentikasi pengguna** — Register & login menggunakan Firebase Authentication.
- **Manajemen grup** — Membuat grup baru, bergabung ke grup lewat kode undangan (join code), atau menambahkan anggota langsung lewat email.
- **Manajemen tugas** — Membuat, mengedit, dan menghapus tugas dalam sebuah grup, lengkap dengan judul, deskripsi, deadline, dan status (`Belum`, `Proses`, `Selesai`).
- **Penugasan ke anggota** — Setiap tugas dapat ditugaskan (assign) ke salah satu anggota grup tertentu.
- **Bukti progres berupa foto** — Melampirkan foto lewat kamera sebagai bukti pengerjaan tugas.
- **Reminder deadline** — Notifikasi otomatis mendekati deadline tugas menggunakan `AlarmManager`/`WorkManager`, tetap berjalan setelah device di-reboot.
- **Dashboard & statistik** — Ringkasan jumlah tugas dan persentase tugas yang sudah selesai per pengguna.
- **Sinkronisasi real-time** — Semua data tugas & grup tersinkronisasi otomatis antar-device melalui Cloud Firestore.

## Teknologi yang Digunakan

| Komponen | Teknologi |
|---|---|
| Bahasa | Java |
| Platform | Android (native) |
| Backend / Database | Firebase Authentication & Cloud Firestore |
| Image loading | Glide |
| Background task | WorkManager |
| Build system | Gradle (Kotlin DSL) |

**Spesifikasi:**
- `minSdk`: 26 (Android 8.0)
- `targetSdk` / `compileSdk`: 35 (Android 15)

## Konfigurasi Firestore

Proyek ini menggunakan beberapa composite index pada koleksi `tasks` (kombinasi `groupId`, `assignedToUid`, `deadline`) agar query penugasan tugas per pengguna berjalan dengan baik. Index sudah dikonfigurasi pada project Firebase terkait; jika project dipindahkan ke akun Firebase lain, index tersebut perlu dibuat ulang melalui menu **Firestore Database → Indexes** di Firebase Console.

## Izin (Permission) yang Digunakan

| Permission | Kegunaan |
|---|---|
| `CAMERA` | Mengambil foto bukti progres tugas |
| `POST_NOTIFICATIONS` | Menampilkan notifikasi reminder deadline |
| `SCHEDULE_EXACT_ALARM` | Menjadwalkan reminder tepat waktu |
| `RECEIVE_BOOT_COMPLETED` | Menjadwalkan ulang reminder setelah device restart |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Komunikasi dengan Firebase |
