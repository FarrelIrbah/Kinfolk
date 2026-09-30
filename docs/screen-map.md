# Peta layar: desain v3 → v1

Sumber: `design/Kinfolk_ Family Care Coordination/Kinfolk v3.dc.html`. Nama layar = nilai `screen` di prototype. Copy UI pakai string Bahasa Indonesia dari prototype.

## Dibuat di v1

| Layar v3 | v1 | Catatan |
|---|---|---|
| `onb0`–`onb3` | Onboarding: buat Care Circle, tambah Care Recipient, kirim Invitation | `onb0` dapat tautan "Sudah bergabung? Masuk" (lihat Masuk). Ketiga jalur `onb0` lewat L1 → L2. Setelah masuk: Home kalau sudah punya Care Circle, selain itu `onb1`. `onb1` → Home langsung sampai tiket Invitation membangun `onb2`/`onb3`. "Saya diundang" sementara lewat jalur yang sama; tiket Invitation (#4) wajib mengarahkannya ke `invitee`. Nama Care Circle = nama Care Recipient pertama, seperti di prototype |
| _(tidak ada)_ L1 Nomor, L2 Kode | Masuk dengan nomor HP + kode OTP WhatsApp (SMS cadangan) | Deviasi disetujui pemilik di #2. Hanya komponen v3: layout `onb1`, tombol "‹ Kembali" `handoff`, judul/subjudul `onb2`, input `onb1`, tombol utama `onb0`, tautan gaya "Lihat semua", teks galat `#9E3B1E`. Nilai yang tak ada di prototype: "+62" di dalam input dengan jarak 12, angka dikelompokkan `812 3456 7890`; kode OTP satu input rata tengah dengan letter-spacing .3em; "Kirim ulang (0:30)" `#6B6A60` saat hitung mundur; "Kirim lewat SMS" muncul setelah dua kali kode salah, bersama "Kirim ulang" saat hitung mundur habis; "Kirim ulang" memakai saluran terakhir. Copy tambahan (diputuskan atas nama pemilik, #2): subjudul L2 setelah SMS "Dikirim lewat SMS ke +62 …."; galat jaringan di L1, L2, dan `onb1` "Tidak tersambung. Coba lagi." dengan gaya teks galat. Pesan kode (WhatsApp dan SMS) di `docs/whatsapp-templates.md` (#3) |
| `invitee` | Terima Invitation | Web tipis untuk yang tanpa app |
| `home` | Beranda | Satu versi (kartu "sebelum kunjungan"), tanpa varian waktu. Disembunyikan: lonceng inbox, pencarian, baris Tugas, baris ringkasan mingguan. Lingkaran obat penuh + jumlah obat hari ini (tanpa progres dosis). Tombol "Rekam" → "Tulis catatan"; badge jadi "Catatan siap" setelah Visit Note tersimpan. Sampai #7 tombol pertanyaan tampil "0 pertanyaan". Tab bar tanpa backdrop blur (Compose tak punya) |
| `appt` | Detail Appointment | Driver + Attendee, daftar Question, kolom teks "Bawa". Deviasi disetujui pemilik di #6: pil "Ubah" (gaya tombol "‹ Kembali") di kanan atas; daftar Question dan kartu "Rekam kunjungan ini" disembunyikan sampai #7; "Di ruangan" = nama Attendee lalu nama Care Recipient ("Anda, Tukiman"), "Belum ada" tanpa Attendee; "Bawa" disembunyikan kalau kosong; baris penyedia = nama Provider · tempat |
| _(tidak ada)_ Form Janji dokter | Buat, ubah, batalkan Appointment | Deviasi disetujui pemilik di #6. Hanya komponen v3: layout, input, chip pilihan tunggal `onb1`, pil "‹ Kembali", tombol utama. Judul "Janji dokter"; kolom "Keperluan", "Dokter" (chip Provider + "+ Dokter baru" yang membuka input tanpa label), "Tempat", "Tanggal" + "Jam" berdampingan (gap 8), "Berangkat", "Mengantar" dan "Di ruangan" (chip "Anda" / "Belum ada"), "Bawa" (input yang memanjang), tombol "Simpan"; "Batalkan janji" (gaya tautan, warna SOS) hanya saat mengubah. Tanggal lewat kalender Material tanpa judul/tombol (memilih hari langsung menutup), warna Kinfolk, font Instrument Sans, nama bulan/hari mengikuti bahasa perangkat. Jam diketik empat angka, tampil "14.30". Pintu masuk: baris pertama layar `empty` ("Tambah janji dokter berikutnya", lingkaran "1") di Beranda selama tidak ada Appointment mendatang, dan pil "Ubah" di `appt`. Setelah simpan/batal kembali ke Beranda |
| _(tidak ada)_ Teks tanggal | Label waktu Appointment | Disetujui di #6. Label: "Hari ini · 14.30", "Besok · 09.00", "Kam, 1 Okt · 09.00". Pil hitung mundur: "40 mnt lagi", "3 jam 10 mnt lagi", "3 jam lagi", "2 hari lagi", "Sedang berlangsung". Baris Driver: "Anda mengantar · berangkat 13.45", "Anda mengantar", "Belum ada yang mengantar". Kartu Beranda tetap tampil sampai hari berganti. Sampai #4/#5 memberi nama Member, diri sendiri tampil "Anda" dengan warna avatar Budi (`#B0643A`) karena hijau Sri tak terlihat di kartu hijau |
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
