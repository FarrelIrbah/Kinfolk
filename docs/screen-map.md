# Peta layar: desain v3 → v1

Sumber: `design/Kinfolk_ Family Care Coordination/Kinfolk v3.dc.html`. Nama layar = nilai `screen` di prototype. Copy UI pakai string Bahasa Indonesia dari prototype.

## Dibuat di v1

| Layar v3 | v1 | Catatan |
|---|---|---|
| `onb0`–`onb3` | Onboarding: buat Care Circle, tambah Care Recipient, kirim Invitation | |
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
