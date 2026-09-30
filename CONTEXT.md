# Kinfolk

Ruang koordinasi untuk saudara kandung yang bersama-sama merawat orang tua. Pasar: Indonesia.

## Language

### Lingkaran

**Care Circle**:
Satu keluarga yang mengoordinasikan perawatan; unit penagihan dan batas akses data. UI: "Lingkaran Perawatan".
_Avoid_: family, group, team

**Care Recipient**:
Orang yang dirawat; semua data kesehatan melekat padanya. Satu Care Circle bisa punya lebih dari satu. UI: "Penerima Perawatan".
_Avoid_: patient, parent, elder

**Member**:
Orang dengan identitas di sebuah Care Circle, baik lewat app maupun hanya lewat SMS/link. Care Recipient boleh juga menjadi Member.
_Avoid_: user, sibling, account

**Invitation**:
Ajakan bergabung ke Care Circle yang dikirim ke nomor HP. Penerimanya belum menjadi Member, dan belum ikut giliran Duty, sampai Invitation diterima. UI: "Undangan".
_Avoid_: pending member, invite link

**Former Member**:
Orang yang pernah menjadi Member lalu keluar atau dikeluarkan. Tidak punya akses lagi, tapi namanya tetap tercatat pada yang pernah ditulisnya. UI: "Mantan anggota".
_Avoid_: deleted user, inactive member

**Role**:
Posisi Member di Care Circle (admin, sibling, parent, viewer) yang menentukan akses dasar.
_Avoid_: permission level, type

**Data Category**:
Kelompok data kesehatan yang aksesnya bisa dibatasi per Member di luar akses dasar Role-nya. Ada enam: janji dokter (beserta Question), rekaman kunjungan (Recording, Transcript, Visit Note), Medication, Document, dokumen keinginan & hukum, Expense. UI: "Janji dokter", "Rekaman kunjungan", "Obat", "Dokumen", "Keinginan & hukum", "Tagihan & uang".
_Avoid_: tag, section

### Perawatan

**Appointment**:
Satu janji temu Care Recipient dengan Provider pada waktu tertentu, dengan paling banyak satu Driver dan satu Attendee. UI: "Janji Dokter".
_Avoid_: visit, schedule, booking

**Driver**:
Member yang ditugaskan mengantar Care Recipient ke sebuah Appointment. UI: "Mengantar".
_Avoid_: escort, chauffeur

**Attendee**:
Member yang ikut masuk ke ruang periksa di sebuah Appointment dan menulis Visit Note-nya. Boleh orang yang sama dengan Driver. UI: "Di ruangan".
_Avoid_: companion, note-taker

**Provider**:
Dokter atau tenaga kesehatan perorangan yang ditemui Care Recipient. Klinik atau RS hanya lokasi, bukan Provider. UI: "Dokter".
_Avoid_: hospital, clinic, facility

**Medication**:
Obat yang dikonsumsi Care Recipient: nama, dosis, jadwal, dan status aktif. UI: "Obat".
_Avoid_: prescription, drug

**Care Contact**:
Orang di luar Care Circle yang relevan untuk perawatan; sebagian ditandai sebagai kontak darurat. UI: "Kontak".
_Avoid_: contact person, emergency contact (sebagai istilah terpisah)

**Visit Note**:
Catatan hasil satu Appointment: Questions beserta jawabannya, Next Steps, dan catatan bebas. Dihasilkan dari Transcript lalu dicek dan dibagikan Attendee, atau ditulis Attendee dengan tangan. UI: "Ringkasan" (dari rekaman), "Catatan kunjungan" (manual).
_Avoid_: summary, report, notes

**Question**:
Hal yang ingin ditanyakan ke tenaga kesehatan. Jika tak terjawab, terbawa ke Appointment berikutnya dengan tenaga kesehatan yang sama.
_Avoid_: agenda item, topic

**Next Step**:
Tindak lanjut yang disepakati di sebuah Appointment, dengan satu Member pemilik dan tenggat; setiap Next Step juga menjadi Task. UI: "Langkah Berikutnya".
_Avoid_: action item, follow-up

**Duty**:
Tugas perawatan berulang yang bergilir antar Member setiap hari (mis. telepon cek malam). Satu hari dipegang satu Member, dan bisa ditukar per hari. Berbeda dari penugasan mengantar ke Appointment yang sekali jalan. UI: "Giliran".
_Avoid_: task, chore, rota

**Emergency Info**:
Ringkasan tetap tentang Care Recipient untuk paramedis (alergi, kondisi, obat aktif, pengencer darah, umur, berat badan, keinginan tindakan, kontak darurat), bisa dibuka siapa saja yang memegang link-nya, tanpa login. Tidak tunduk pada Data Category.
_Avoid_: SOS page, medical ID

**Dose Log**:
Tanda bahwa satu jadwal Medication sudah diberikan pada hari tertentu, beserta siapa yang menandai. UI: "Diberikan".
_Avoid_: intake, adherence record

**Check-in**:
Catatan harian tetap tentang Care Recipient setelah telepon malam: tekanan darah, makan malam, berjalan, suasana hati, catatan bebas. Kumpulannya menjadi tren kondisi. UI: "Cek malam".
_Avoid_: vitals, daily log

**Task**:
Pekerjaan sekali jalan dengan satu Member pemilik dan tenggat; berasal dari Next Step, pengingat isi ulang Medication, atau ditambahkan Member. UI: "Tugas".
_Avoid_: to-do, action item, Duty

**Expense**:
Catatan siapa membayar apa untuk perawatan, dalam Rupiah. Kinfolk tidak menagih siapa pun. UI: "Biaya".
_Avoid_: bill, payment, reimbursement

**Document**:
Berkas tentang Care Recipient (laporan, asuransi, identitas, surat hukum) yang disimpan terenkripsi per Care Circle; setiap unggahan ulang menjadi versi baru. UI: "Dokumen".
_Avoid_: file, attachment

**Note**:
Tulisan bebas seorang Member, dibagikan ke Care Circle atau hanya untuk penulisnya. UI: "Catatan".
_Avoid_: memo, Visit Note

**Recording**:
Audio sebuah Appointment yang direkam dengan izin semua orang di ruangan; hasilnya Transcript, lalu Visit Note. UI: "Rekaman".
_Avoid_: audio file, voice memo

**Transcript**:
Teks Recording per segmen berlabel pembicara dan waktu, menjadi rujukan setiap baris Visit Note. UI: "Transkrip".
_Avoid_: captions

**Handoff**:
Ringkasan pendek sebuah Appointment untuk Member yang tidak ada di ruangan, dikirim lewat WhatsApp. UI: "Serah terima".
_Avoid_: update, recap

**Away**:
Status Member yang tidak bisa memegang Duty sampai akhir minggu; hari-harinya ditawarkan ke Member lain. UI: "Berhalangan".
_Avoid_: vacation, leave

**Subscription**:
Langganan satu Care Circle (paket keluarga dan add-on transkripsi), dibayar oleh Member mana pun. UI: "Paket".
_Avoid_: plan per user, account

