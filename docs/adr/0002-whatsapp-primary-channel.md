# WhatsApp sebagai channel utama, SMS cadangan

Brief memilih SMS karena kebiasaan di AS: anggota keluarga yang lebih tua tidak memasang app. Di Indonesia, orang tua pun sudah memakai WhatsApp. Karena itu notifikasi, undangan, dan OTP login dikirim lewat WhatsApp Business Cloud API, dengan SMS sebagai cadangan. Identitas Member adalah nomor HP, bukan email.

## Consequences

- Pesan keluar wajib memakai template yang disetujui Meta dan dikenai biaya per percakapan. Balasan bebas hanya bisa dikirim dalam 24 jam sejak pesan terakhir dari user.
- Member bisa ikut di Care Circle tanpa app (lewat balasan WhatsApp dan link web), jadi setiap alur penting harus punya jalur non-app.
