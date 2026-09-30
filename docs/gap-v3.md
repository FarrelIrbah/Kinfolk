# Celah: app saat ini vs desain v3

Acuan: `design/Kinfolk_ Family Care Coordination/Kinfolk v3.dc.html`. `Kinfolk_standalone.html` berisi markup, copy, dan logika yang identik dengan v3 (hanya bundel: font inline, `onClick` → `sc-camel-on-click`), jadi tidak menambah apa pun.

Target: app 1:1 dengan v3. Dokumen ini masukan untuk to-spec → to-tickets. Status per 1 Okt 2026, setelah #1–#16.

Tanda:
- **[Layar]** layar/sheet v3 yang belum ada sama sekali
- **[Elemen]** kartu, baris, tombol, atau copy v3 yang disembunyikan/diganti di layar yang sudah ada
- **[Fitur]** perilaku/alur v3 yang belum ada
- **[Data]** butuh tabel/kolom/Edge Function baru
- **[Bentrok]** bertentangan dengan ADR atau deviasi yang sudah disetujui; perlu keputusan pemilik sebelum spec

Nama layar = nilai `screen` di prototype.

---

## 1. Lintas layar

| # | Celah | Tanda | Catatan |
|---|---|---|---|
| G1 | Aksesibilitas: ukuran teks (Standar/Besar/Terbesar = zoom 1 / 1.1 / 1.2), kontras tinggi (`contrast(1.22) saturate(1.08)`), kurangi animasi (matikan transisi layar, sheet, toast) | [Layar] [Fitur] | `a11y`. Screen-map menunda ("ukuran font sistem sudah diikuti Compose"). Pilihan disimpan per ponsel |
| G2 | Pil offline: copy v3 "Offline · dikirim saat tersambung lagi" + antrean tulis offline | [Elemen] [Fitur] [Bentrok] | #14 memutuskan hanya-baca, copy "Offline". 1:1 berarti antrean tulis + sinkronisasi |
| G3 | Tab bar `backdrop-filter: blur(14px)` | [Elemen] | Dilewati karena Compose tak punya backdrop blur. Butuh solusi platform (Android 12+ `RenderEffect`, iOS `UIVisualEffectView`) atau diterima sebagai deviasi permanen |
| G4 | Nama "Bapak" di copy | [Bentrok] | v3 menulis "Bapak" di banyak copy; app memakai nama Care Recipient (#9). Pertahankan deviasi (Care Recipient bisa Ibu/Kakek) |
| G5 | Bahasa Inggris | [Fitur] | v3 punya string EN lengkap; app hanya `values/strings.xml` (ID). Toggle bahasa ada di panel prototype, bukan di ponsel. Perlu keputusan: ID saja, atau ikut bahasa perangkat |
| G6 | Toast + "Urungkan" dipakai v3 juga untuk: terapkan perubahan dosis, tandai tugas selesai | [Fitur] | Mekanismenya sudah ada (#9); tinggal dipakai fitur baru |
| G7 | Pesan SMS vs WhatsApp | [Bentrok] | v3 menyebut SMS di mana-mana ("Dikonfirmasi via SMS", "Mereka menerima SMS"). ADR 0002 = WhatsApp utama. Copy sudah diganti ke WhatsApp; pertahankan |

## 2. Onboarding & undangan

| # | Layar | Celah | Tanda |
|---|---|---|---|
| O1 | `invitee` | Kartu hijau "Kunjungan terakhir · Fisioterapi, 24 Sept" / judul / isi (Visit Note terakhir) | [Elemen] [Bentrok] #8: Invitee belum Member, jadi belum boleh membaca data kesehatan. 1:1 = tampilkan setelah menerima, atau buka akses baca terbatas untuk Invitee |
| O2 | `invitee` | Copy kartu "Satu hal kecil": v3 = "Telepon Bapak hari Minggu jam 19.00 masih kosong. Hanya 10 menit, dari mana saja." (slot kosong spesifik); app = Duty minggu depan (#10) | [Elemen] [Bentrok] Model Duty mingguan (#10) tidak punya slot harian kosong |
| O3 | `onb0`, `onb1`, `onb2`, `onb3` | Tautan "Masuk", input "Nama Anda", daftar kosong + form tambah, copy WhatsApp, copy `onb3` | [Bentrok] Deviasi #2/#4/#9 yang dibutuhkan (login, data nyata). Rekomendasi: pertahankan |

## 3. Beranda (`home`)

| # | Celah | Tanda | Catatan |
|---|---|---|---|
| H1 | Lonceng inbox + badge jumlah (`#9E3B1E`, 18px) di kiri SOS | [Elemen] | Butuh `inbox` (bagian 9) |
| H2 | Bilah cari "Cari obat, kunjungan, dokumen…" | [Elemen] | Butuh `search` |
| H3 | Varian kartu utama per waktu: **Pagi** (08.00 · obat pagi, daftar dosis Diberikan/Belum, "Tandai semua diberikan"); **Sesudah kunjungan** (ringkasan siap, pil "N baris perlu dicek" kuning, "1 pertanyaan belum terjawab"); **Malam** (kartu gelap `#22261F`, "Giliran Anda malam ini", "Telepon Bapak", "Catat cek") | [Elemen] [Fitur] | Di v3 dipilih lewat panel "Beranda pada…". Perlu aturan nyata: jam berapa tiap varian muncul, prioritasnya dengan kartu Appointment |
| H4 | Kartu Appointment: badge "Ringkasan siap", tombol "Rekam", sub "Rekam kunjungan · coba gratis" | [Elemen] [Bentrok] | Diganti "Catatan siap"/"Tulis catatan" (#7). Bergantung pada transkripsi (bagian 5) |
| H5 | Baris Tugas "N tugas belum selesai" / "1 terlambat · izin parkir (Budi)" merah | [Elemen] | Butuh `tasks` |
| H6 | Baris "Ringkasan hari Minggu" / "Dikirim ke 5 anak · lihat seperti yang Rina terima" (`#E9E2D4`) | [Elemen] [Fitur] | Butuh ringkasan mingguan WhatsApp + tampilan pesannya (bagian 10) |
| H7 | Cincin obat: progres `conic-gradient` = dosis diberikan / total, teks "2/4"; sub "Omeprazole · sebelum sarapan" / "Atorvastatin jam 21.00" berdasar dosis berikutnya yang belum diberikan | [Elemen] [Fitur] | Butuh log dosis (R1) |
| H8 | "Minggu ini": v3 = pemegang telepon malam per hari (bisa beda tiap hari) + keterangan "Malam ini giliran Anda." | [Bentrok] | #10: giliran mingguan, satu pemegang tujuh hari. Kalau 1:1, model Duty harus per hari |
| H9 | Linimasa "Terbaru" berisi semua jenis entri (obat, cek, dokumen, giliran) | [Fitur] | Mengikuti T1 |
| H10 | State kosong: v3 punya layar `empty` sendiri (kepala "Mari siapkan lingkaran Bapak", sub "Empat hal membuat Kinfolk berguna sejak hari pertama. Urutan bebas.", 4 baris, kotak linimasa putus-putus); app menaruh baris 1–4 di Beranda | [Bentrok] | Deviasi #6/#14. 1:1 = Beranda diganti layar `empty` penuh saat lingkaran baru |

## 4. Janji dokter (`appt`)

| # | Celah | Tanda |
|---|---|---|
| A1 | Kartu "Rekam kunjungan ini" / "Pertanyaan, jawaban dokter, dan langkah berikutnya ditulis untuk semua. Transkrip lengkap tetap terlampir." + tombol "Rekam kunjungan · coba gratis" / "Rekam kunjungan" / "Buka ringkasan" | [Elemen] [Bentrok] Diganti "Catatan kunjungan" (#7). Bergantung pada bagian 5 |
| A2 | Pil "Ubah", "Belum ada", baris "Dari kunjungan 24 Sept" | [Bentrok] Tambahan #6/#7 yang dibutuhkan. Pertahankan |

## 5. Rekaman & ringkasan kunjungan (transkripsi)

Seluruh alur ditunda di screen-map. v3: `appt` → (`paywall` bila belum langganan/add-on mati) → `consent` → `recording` → `processing` → `summary` (+ drawer transkrip) → `handoff`; gagal → `recfail`.

| # | Layar | Celah | Tanda |
|---|---|---|---|
| V1 | `consent` | "Semua yang di ruangan setuju": centang per orang (dokter, pasien, Anda), tombol abu `#A9ADA2` sampai semua dicentang, toast "Centang semua yang di ruangan dulu.", catatan server sendiri | [Layar] [Data] daftar orang di ruangan = Provider + Care Recipient + Attendee |
| V2 | `recording` | Titik merah berdenyut + "Merekam"/"Dijeda", timer 64px Newsreader, gelombang 36 batang, "Ditranskripsi sambil berjalan" (3 baris terakhir live), "Jeda"/"Lanjutkan", "Stop & ringkas" | [Layar] [Fitur] rekam audio native + transkripsi streaming |
| V3 | `processing` | Spinner + "Menulis catatan kunjungan" + sub | [Layar] |
| V4 | `recfail` | "Rekaman berhenti di 2:14", audio tersimpan terenkripsi di ponsel, "Lanjutkan merekam", "Ringkas yang sudah ada", "Catat manual saja" | [Layar] [Fitur] simpan lokal terenkripsi + lanjutkan |
| V5 | `summary` | Kepala "Ditranskripsi secara privat", petunjuk verifikasi `#E9E2D4`, pil waktu transkrip per baris (mis. "00:41"), ketuk baris → sorot segmen di drawer | [Elemen] [Bentrok] #7 memakai `summary` sebagai form manual |
| V6 | `summary` | Penanda kuning `#F4E4B0` "Cek: …" + "Sudah benar"; tombol bagikan terkunci "Konfirmasi N baris bertanda untuk membagikan" + toast "Cek dulu baris kuning dengan transkrip." | [Elemen] [Fitur] AI menandai baris berisiko |
| V7 | `summary` | Bagian "Perubahan obat": Amlodipine ~~5 mg~~ → 10 mg, status "Pengingat belum diperbarui · tab Obat" / "Pengingat sudah 10 mg" | [Elemen] [Fitur] ekstraksi perubahan dosis |
| V8 | `summary` | Pil pemilik langkah (ketuk untuk ganti Member), "· ketuk nama untuk ganti" | [Elemen] [Fitur] Next Step punya pemilik → jadi Tugas (bagian 9) |
| V9 | `summary` | "Pindah ke kunjungan berikut" manual + toast "Ditambahkan ke pertanyaan fisioterapi 1 Okt. Budi diberi tahu via SMS." | [Elemen] [Bentrok] #7 memilih carry-over otomatis |
| V10 | `summary` | Tombol "Bagikan ke lingkaran" → "Dibagikan · SMS ke 4 saudara" + toast; Member yang Catatan kunjungannya disembunyikan menerima tanpa rekaman | [Elemen] [Fitur] |
| V11 | `summary` | Drawer transkrip gelap (`#22261F`, r24, tinggi 120/430 dengan transisi .28s), "Perbesar"/"Perkecil", segmen tersorot `rgba(201,167,124,.28)`, lainnya opacity .55 | [Elemen] [Fitur] |
| V12 | `handoff` | "Tulis serah terima untuk yang tidak hadir" → layar: "Yang terjadi" (jawaban), kotak perubahan obat (hijau/kuning), "Siapa mengerjakan apa" + tenggat, "Dikirim ke" (yang tidak di ruangan), "Kirim serah terima" → "Terkirim ke …" | [Layar] [Fitur] [Data] template WhatsApp baru |
| V13 | `paywall` | "Kinfolk Family $15 / bulan", add-on transkripsi +$8 dengan sakelar, total, "Mulai uji coba gratis 14 hari", catatan halus | [Layar] [Fitur] [Data] langganan per Care Circle (IAP Google/Apple), harga Rupiah? |
| V14 | `circle` | Baris "Paket" + sub "Gratis · tingkatkan untuk ringkasan" / "Keluarga + transkripsi · uji coba, sisa 14 hari" | [Elemen] bergantung pada V13 |

Keputusan besar: mesin transkripsi (v3: "ditranskripsi di server Kinfolk sendiri, tidak dikirim ke layanan AI luar"). ADR 0001 = Supabase tanpa server sendiri. Butuh ADR baru: self-host Whisper + LLM (GPU) vs API luar (bertentangan dengan copy v3).

## 6. Giliran (`rotation`)

| # | Celah | Tanda |
|---|---|---|
| R-1 | Pemilih "Minggu ini" / "September" | [Elemen] |
| R-2 | Tampilan bulan: bar bertumpuk per Member (Antar `#2F5D4A` / Telepon malam `#C9A77C` / Obat, dokumen, biaya `#8FA79A`), "N tugas", "N antar · N telepon · N lainnya", legenda, catatan "Dihitung dari giliran, catatan obat, dan biaya…" | [Fitur] [Data] butuh log dosis, dokumen, biaya untuk "lainnya" |
| R-3 | "Saya tidak bisa minggu ini" → sheet away: alasan (Sakit/Bepergian/Pekerjaan/Lainnya, "hanya saudara yang melihat"), usulan pengganti otomatis (yang tugasnya paling sedikit) per slot, "Kirim N permintaan" → spanduk kuning "Anda ditandai berhalangan sampai Minggu 4 Okt…" | [Fitur] [Data] status away + permintaan tukar massal |
| R-4 | Giliran per hari (7 pemegang berbeda per minggu), "Tukar" per hari, judul sheet "Tukar telepon malam Anda · Min 4" | [Bentrok] #10 = giliran mingguan |
| R-5 | Toast kirim tukar: SMS ke Budi: "Bisa gantikan telepon malam Bapak hari Min? Balas YA." | [Elemen] #10 menghapus toast ini |

## 7. Linimasa (`timeline`)

| # | Celah | Tanda |
|---|---|---|
| T1 | Jenis entri: Obat (`#9A7A2F`: dosis diberikan, isi ulang, perubahan dosis), Cek (`#3E6E8E`: cek malam, tombol Mode Bapak, perubahan akses), Dokumen (`#6C5A8E`: unggah, ekspor PDF), Giliran (`#B0643A`: konfirmasi antar) | [Fitur] [Data] tabel peristiwa umum |
| T2 | Pil filter: Semua / Kunjungan / Obat / Cek / Dokumen | [Elemen] |
| T3 | Pil "Catatan" (ikon pensil) di kanan judul → `notes` | [Elemen] |

## 8. Catatan (`records`)

| # | Tab | Celah | Tanda |
|---|---|---|---|
| R1 | — | Pemilih 4 tab: Obat / Dokumen / Biaya / Kondisi | [Elemen] |
| R2 | Obat | Petunjuk "Ketuk saat dosis diberikan. Semua bisa melihat.", tombol "Tandai" / "Diberikan ✓" per obat + toast "Clopidogrel ditandai oleh Sri · terlihat oleh lingkaran", baris catatan berwarna (mis. "Pengencer darah. Jangan berhenti tanpa Dr. Rao." merah, "Isi ulang 4 hari lagi · Agus") | [Elemen] [Fitur] [Data] log dosis per hari, catatan obat, isi ulang |
| R3 | Obat | Spanduk kuning perubahan dosis: "Dosis diubah di kunjungan hari ini", Amlodipine ~~5 mg~~ → 10 mg, "Dr. Rao mengatakannya di menit 02:10 rekaman…", "Perbarui pengingat" (+ Urungkan), "Lihat di transkrip" | [Elemen] [Fitur] bergantung pada V7 |
| R4 | Dokumen | Catatan "Dienkripsi per lingkaran. Setiap perubahan menyimpan versi lama.", daftar (ikon ext PDF/JPG, nama, meta "12 Juni · v2 · Rina", pil visibilitas "Semua"/"4 orang"/"3 orang" berwarna), "+ Unggah dokumen", "Ekspor untuk dokter baru" | [Fitur] [Data] Supabase Storage, versi, visibilitas per dokumen, Data Category "Dokumen" |
| R5 | Biaya | Catatan "Kinfolk tidak pernah menagih siapa pun.", total "September sejauh ini" + bar per Member, daftar pengeluaran, form "Tambah pengeluaran" (Untuk apa?, jumlah, chip "Dibayar oleh", "Simpan", galat "Isi keperluan dan jumlahnya.") | [Fitur] [Data] tabel pengeluaran, Data Category "Tagihan & uang". Mata uang: `$` → Rp? |
| R6 | Kondisi | Grafik tensi 30 hari (sistolik/diastolik, garis batas 140 putus-putus merah), angka terakhir, "Rata-rata 30 hari … · N kali 140 ke atas"; grid 30 hari "Makan malam"/"Berjalan"/"Suasana hati"; "Lampirkan ke kunjungan Dr. Rao (PDF)" | [Fitur] [Data] dari cek malam (C1) |

## 9. Fitur harian baru

| # | Layar | Isi v3 | Tanda |
|---|---|---|---|
| C1 | `checkin` | "Cek malam": tensi sistolik/diastolik mmHg, Sudah makan malam? (Ya/Sedikit/Tidak), Berjalan hari ini? (Ya/Tidak), Suasana hati (Baik/Biasa/Murung), "Ada hal lain?", "Simpan cek"; galat "Isi tekanan darah."; ≥140 → toast "Tersimpan. Tensi 140 ke atas; Budi dan Dewi diberi tahu via SMS." + WhatsApp peringatan; entri linimasa "Telepon malam: tensi …" | [Layar] [Data] tabel check-in, aturan siapa diberi tahu |
| K1 | `tasks` | "Tugas": sumber Next Step (dengan tenggat), pengingat isi ulang, tugas manual; "Belum selesai · N" / "Selesai"; centang (+ Urungkan), "Ingatkan" → "Diingatkan" (WhatsApp), tenggat terlambat merah | [Layar] [Data] tabel tugas, pemilik + tenggat pada Next Step. Form tambah tugas tidak ada di v3 ("Ditambahkan Budi") → perlu keputusan |
| N1 | `notes` | "Catatan": tab Bersama / Hanya saya, petunjuk privat, input + sakelar "Hanya saya" + "Simpan", daftar (avatar, teks, "Nama · tanggal · hanya Anda") | [Layar] [Data] tabel catatan dengan flag privat (RLS: hanya penulis) |
| I1 | `inbox` | "Menunggu Anda": permintaan tukar (Terima/Tolak), baris ringkasan perlu dicek, perubahan dosis perlu diterapkan, pertanyaan baru dari saudara, tugas terlambat; kosong "Semua sudah beres." | [Layar] [Fitur] turunan data lain; badge di Beranda (H1) |
| S1 | `search` | Input autofokus + "Batal", saran (clopidogrel, MRI, Budi, Dr. Rao), hasil berjenis (Obat, Transkrip, Dokumen, Linimasa, Kontak, Tugas) maks 14, "N hasil", "Tidak ditemukan." | [Layar] [Fitur] min 2 huruf |
| E1 | `export` | "Ekspor untuk dokter baru": "Disiapkan untuk", 6 bagian bercentang + jumlah halaman, pratinjau kertas, "Buat PDF · N halaman", tautan berlaku 7 hari, entri linimasa | [Layar] [Fitur] [Data] PDF + tautan bertoken kedaluwarsa (mirip Emergency Info) |
| B1 | `bapak` | Mode Bapak: "Selamat sore, Pak Tukiman", "Hari ini" (Appointment + Driver), 3 tombol 92px ("Saya baik" / "Butuh bantuan" / "Telepon Sri") → pesan ke anak + entri linimasa, "Siapa melihat apa" (daftar anak → `member`), "Keluar" | [Layar] [Fitur] Care Recipient sebagai Member di ponselnya sendiri (Role parent, #9 hanya lewat API). Perlu alur masuk parent |

## 10. Lingkaran, anggota, kontak, darurat

| # | Layar | Celah | Tanda |
|---|---|---|---|
| L1 | `circle` | "Cara lain" v3 berisi 8 baris: Mode Bapak, Kontak perawatan ✓, Catatan, Ekspor untuk dokter baru, Tampilan SMS Rina, Kartu darurat dompet, Yang dilihat saudara baru, Tampilan & aksesibilitas | [Elemen] 7 baris belum ada. "Tampilan SMS Rina" dan "Yang dilihat saudara baru" adalah demo prototype → perlu keputusan |
| L2 | `circle` | "Paket" (V14), "Ulangi onboarding", `permIntro` kalimat kedua "Perubahan tercatat di linimasa." | [Elemen] |
| L3 | `member` | 6 Data Category: + "Dokumen" (Laporan, asuransi, identitas), "Keinginan & hukum" (Surat wasiat medis, surat kuasa), "Tagihan & uang" (Tagihan RS, pensiun); "Rekaman kunjungan" (Audio, transkrip, ringkasan) vs "Catatan kunjungan" | [Data] enum `data_category` 3 → 6. Kolom akses "N/6" |
| L4 | `member` | "Notifikasi" / "Saluran" (SMS / SMS + app) | [Elemen] [Bentrok] #13: semua lewat WhatsApp |
| L5 | `member` | "Riwayat perubahan": "Rekaman kunjungan disembunyikan · 19 Sept · atas permintaan Bapak", "Baru saja · oleh Sri", kosong "Belum ada perubahan." | [Elemen] [Data] log perubahan akses |
| L6 | `member` | Catatan kaki "Hal yang disembunyikan tidak muncul di mana pun bagi orang ini, termasuk linimasa dan SMS."; toast "… · tercatat" | [Elemen] bergantung pada L5 |
| L7 | `contacts` | Baris catatan per kontak (mis. "Memegang kunci cadangan rumah", `#E9E2D4`) | [Elemen] [Data] kolom catatan |
| L8 | `circle` | Sub kanal per Member: v3 "App + SMS" / "Hanya SMS" (jadi "App + WhatsApp" / "Hanya WhatsApp"); app menulis Role ("Anak", "Pengatur") | [Elemen] [Data] tahu siapa sudah pernah login di app |
| L9 | `circle` | Baris Care Recipient ("Bapak Tukiman", "Penerima perawatan · menentukan akses") selalu tampil paling atas dan membuka Mode Bapak; app hanya menampilkannya saat Care Recipient menjadi Member | [Elemen] bergantung pada B1 |
| L10 | `contacts` | Pil "Darurat" di baris kontak tidak ada di v3 (deviasi #11) | [Bentrok] |
| M3 | `emergency` | Kontak darurat v3 memuat Member ("Sri · Anak · utama", "Budi · Anak · 10 menit") + dokter; app hanya Care Contact bertanda darurat | [Elemen] [Data] |
| M1 | `emergency` | Baris "78 · lahir 12 Mar 1948 · 64 kg", spanduk merah "Minum pengencer darah: Clopidogrel", kotak "Keinginan" ("Tindakan penuh") di samping Alergi | [Elemen] [Data] [Bentrok] ADR 0003 membatasi bidang; menambah bidang = tinjau ulang ADR |
| M2 | `qr` | Baris umur + spanduk pengencer darah di kartu, "Cetak kartu" (2 kartu dompet + 1 lembar kulkas) vs "Bagikan tautan" | [Elemen] [Bentrok] |
| M4 | `emergency` | Pil "Ubah" (deviasi #12) di samping "Tutup" | [Bentrok] dipertahankan bersama form (Q9 ronde 1) |

## 11. Pesan WhatsApp (`sms`)

v3 `sms` adalah demo layar ponsel Rina, bukan layar app. Template yang belum dibuat (#13 sengaja tidak membuatnya):

| # | Pesan | Tanda |
|---|---|---|
| W1 | Ringkasan mingguan Minggu 18.00 (telepon malam, rata-rata tensi, dosis tercatat, fisioterapi, janji berikutnya, slot kosong) | [Fitur] [Data] butuh C1, R2 |
| W2 | "Malam ini: atorvastatin jam 21.00. Balas 1 jika sudah diberikan." → "Tercatat: atorvastatin diberikan, 21.02." | [Fitur] butuh R2 |
| W3 | "T: …" → "Ditambahkan ke pertanyaan untuk kunjungan … berikutnya dengan Dr. Rao." | [Fitur] |
| W4 | Serah terima (V12), pengingat tugas (K1), peringatan tensi ≥140 (C1), ringkasan dibagikan (V10) | [Fitur] |

## 12. Panel prototype (bukan fitur app)

Kolom kiri v3 ("Kinfolk · iOS prototype", Bahasa, "Beranda pada…", "Keadaan", "Langsung ke") adalah alat demo. Tidak dibuat, kecuali pemilihan bahasa (G5).

---

## Istilah domain baru (untuk `CONTEXT.md` saat spec)

Belum ada di glosarium dan perlu diputuskan namanya saat to-spec: dosis diberikan (Dose Log), Check-in malam, Task, Expense, Document (+ versi), Note (bersama/privat), Handoff, Away, Recording/Transcript, Subscription, Access Change Log. "Rekaman kunjungan" vs "Catatan kunjungan" sebagai Data Category.

## Keputusan (1 Okt 2026)

Target 1:1 dengan v3. Deviasi yang dipertahankan, deviasi baru, dan yang dibatalkan: `docs/screen-map.md` bagian "Target".

1. Transkripsi dibangun, self-host di RunPod Serverless Singapura (ADR 0005). Visit Note manual jadi cadangan.
2. Paywall dibangun lewat RevenueCat, harga lokal toko, hanya rekaman dan transkripsi yang dikunci, satu langganan per Care Circle (ADR 0006).
3. Giliran per hari, tukar per hari (membatalkan #10 mingguan).
4. Antrean tulis offline untuk tandai dosis, cek malam, catatan, tandai tugas, tambah pertanyaan; form lain tetap galat.
5. Info darurat + tanggal lahir, berat badan, keinginan tindakan, spanduk pengencer darah dari Medication (ADR 0003 diperbarui). Kartu QR "Cetak kartu" (2 kartu dompet + 1 lembar kulkas). Member bertanda kontak darurat tampil lebih dulu.
6. Data Category 6 dengan label v3; enum `visit_notes` berlabel "Rekaman kunjungan".
7. Mode Bapak di ponsel Member mana pun; "Siapa melihat apa" mengikuti ADR 0004.
8. Varian Beranda: sesudah kunjungan > sebelum kunjungan > malam (≥17.00, pemegang giliran) > pagi (<11.00, dosis pagi belum lengkap) > default.
9. Bahasa Indonesia saja.
10. Ringkasan AI hanya terlihat Attendee sampai "Bagikan"; Visit Note manual terlihat saat disimpan.
11. Tambahan: hapus rekaman, "+ Tambah tugas", tenggat usulan AI (ubah di form tugas), pengingat "Balas 1" ke pemegang giliran 15 menit setelah jadwal, peringatan tensi ≥140 ke semua Member lain, ringkasan Minggu 18.00 WIB (dilewati kalau tanpa aktivitas), "T: …" jadi Question.

## Urutan kasar yang disarankan

Tidak bergantung keputusan besar, bisa langsung di-spec: R1 tab + R2 log dosis (+ H7), T1–T3, C1 + R6, K1, N1, I1, S1, L5–L7, L1, G1, E1, R-1/R-2/R-3, H1/H2/H5/H6, W1–W4. Setelah keputusan 1–7: transkripsi, paywall, giliran harian, offline tulis, Emergency Info, Data Category, Mode Bapak.
