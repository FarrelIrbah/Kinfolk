# Emergency Info tidak ikut aturan Data Category

Emergency Info dibuka paramedis lewat QR/link bertoken tanpa login, jadi tidak bisa memeriksa Role atau Data Category. Kami sengaja membuatnya melewati pembatasan itu. Isinya dibatasi ke bidang tetap (alergi, obat aktif, kondisi, kontak darurat), dan admin bisa mencabut lalu membuat ulang tokennya. Pemegang QR adalah orang asing dalam keadaan darurat: data minimal yang selalu tersedia lebih berharga daripada data lengkap yang terkunci.

## Consequences

- Siapa saja yang memegang link bisa melihat isinya. Jangan pernah menambah bidang ke Emergency Info tanpa menimbang ulang keputusan ini.
