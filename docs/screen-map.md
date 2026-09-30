# Peta layar: desain v3 → v1

Sumber: `design/Kinfolk_ Family Care Coordination/Kinfolk v3.dc.html`. Nama layar = nilai `screen` di prototype. Copy UI pakai string Bahasa Indonesia dari prototype.

## Dibuat di v1

| Layar v3 | v1 | Catatan |
|---|---|---|
| `onb0`–`onb3` | Onboarding: buat Care Circle, tambah Care Recipient, kirim Invitation | `onb0` dapat tautan "Sudah bergabung? Masuk" (lihat Masuk). Ketiga jalur `onb0` lewat L1 → L2. Setelah masuk: Home kalau sudah punya Care Circle, selain itu `onb1`. `onb1` → Home langsung sampai tiket Invitation membangun `onb2`/`onb3`. "Saya diundang" sementara lewat jalur yang sama; tiket Invitation (#4) wajib mengarahkannya ke `invitee`. Nama Care Circle = nama Care Recipient pertama, seperti di prototype |
| _(tidak ada)_ L1 Nomor, L2 Kode | Masuk dengan nomor HP + kode OTP WhatsApp (SMS cadangan) | Deviasi disetujui pemilik di #2. Hanya komponen v3: layout `onb1`, tombol "‹ Kembali" `handoff`, judul/subjudul `onb2`, input `onb1`, tombol utama `onb0`, tautan gaya "Lihat semua", teks galat `#9E3B1E`. Nilai yang tak ada di prototype: "+62" di dalam input dengan jarak 12, angka dikelompokkan `812 3456 7890`; kode OTP satu input rata tengah dengan letter-spacing .3em; "Kirim ulang (0:30)" `#6B6A60` saat hitung mundur; "Kirim lewat SMS" muncul setelah dua kali kode salah, bersama "Kirim ulang" saat hitung mundur habis; "Kirim ulang" memakai saluran terakhir. Copy tambahan (diputuskan atas nama pemilik, #2): subjudul L2 setelah SMS "Dikirim lewat SMS ke +62 …."; galat jaringan di L1, L2, dan `onb1` "Tidak tersambung. Coba lagi." dengan gaya teks galat. Pesan kode (WhatsApp dan SMS) di `docs/whatsapp-templates.md` (#3) |
| `invitee` | Terima Invitation | Web tipis untuk yang tanpa app |
| `home` | Beranda | Satu versi (kartu "sebelum kunjungan"), tanpa varian waktu. Disembunyikan: lonceng inbox, pencarian, baris Tugas, baris ringkasan mingguan. Lingkaran obat penuh + jumlah obat hari ini (tanpa progres dosis). Tombol "Rekam" → "Tulis catatan"; badge jadi "Catatan siap" setelah Visit Note tersimpan. Tab bar tanpa backdrop blur (Compose tak punya) |
| `appt` | Detail Appointment | Driver + Attendee, daftar Question, kolom teks "Bawa" |
| `summary` | Visit Note | Tata letak dipakai sebagai form isian manual (tanpa transkrip) |
| `rotation` | Duty + tukar giliran | Tampilan minggu saja |
| `timeline` | Linimasa Appointment + Visit Note | |
| `records` | Tab Obat saja | |
| `circle`, `member` | Member, Role, pembatasan Data Category | |
| `contacts` | Care Contact | |
| `emergency`, `qr` | Emergency Info + QR | Halaman web statis dari Edge Function |
| `sms` | Template balasan WhatsApp | Bukan layar app |
| `empty`, offline | State kosong dan offline | |

## Ditunda

- Transkripsi: `consent`, `recording`, `processing`, `recfail`, `paywall`
- `checkin`, `bapak`, `handoff`, `tasks`, `away`, `notes`, `export`, `inbox`, `search`
- `records`: tab dokumen, tren, biaya; `rotation`: tampilan bulan; centang dosis obat
- `a11y`: ukuran font sistem sudah diikuti Compose
