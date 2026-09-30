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
Kelompok data kesehatan (mis. medis, psikiatri, keuangan, dokumen) yang aksesnya bisa dibatasi per Role di luar akses dasar.
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
Catatan hasil satu Appointment, ditulis Attendee: Questions beserta jawabannya, Next Steps, dan catatan bebas. UI: "Catatan Kunjungan".
_Avoid_: summary, report, notes

**Question**:
Hal yang ingin ditanyakan ke tenaga kesehatan. Jika tak terjawab, terbawa ke Appointment berikutnya dengan tenaga kesehatan yang sama.
_Avoid_: agenda item, topic

**Next Step**:
Tindak lanjut yang disepakati di sebuah Appointment. UI: "Langkah Berikutnya".
_Avoid_: action item, follow-up

**Duty**:
Tugas perawatan berulang yang bergilir antar Member (mis. cek malam, belanja obat). Berbeda dari penugasan mengantar ke Appointment yang sekali jalan. UI: "Giliran".
_Avoid_: task, chore, rota

**Emergency Info**:
Ringkasan tetap tentang Care Recipient untuk paramedis (alergi, obat aktif, kondisi, kontak darurat), bisa dibuka siapa saja yang memegang link-nya, tanpa login. Tidak tunduk pada Data Category.
_Avoid_: SOS page, medical ID
