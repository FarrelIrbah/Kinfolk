# Transkripsi kunjungan di GPU sewaan sendiri, bukan API AI luar

Desain v3 menjanjikan "Audio ditranskripsi di server Kinfolk sendiri dan tidak dikirim ke layanan AI luar." Supaya janji itu benar, rekaman kunjungan ditranskripsi oleh Whisper large-v3 (dengan diarisasi) dan diringkas oleh LLM open-weight (mis. Qwen 2.5 32B instruct) yang kami jalankan sendiri di RunPod Serverless, dipatok ke datacenter Asia (Singapura). Edge Function mengirim tugas ke worker dan menerima hasilnya. Audio disimpan terenkripsi di Supabase Storage. Keluarannya: segmen transkrip berlabel pembicara, pasangan pertanyaan-jawaban dengan rujukan segmen, Next Step dengan usulan pemilik dan tenggat, baris yang perlu dicek (penanda kuning), dan perubahan dosis Medication.

## Considered Options

- **API luar (OpenAI/Anthropic)**: lebih murah dan cepat dibangun, tapi copy consent, "Ditranskripsi secara privat", dan paywall harus diubah. Data kesehatan keluar ke pihak ketiga.
- **Modal**: cold start cepat, tapi region Asia tidak bisa dipastikan.
- **VM GPU menyala 24/7**: mahal untuk pemakaian yang jarang.

## Consequences

- Ada server kedua di luar Supabase (menyimpang dari ADR 0001), terbatas pada worker tanpa state yang dipanggil Edge Function.
- Cold start membuat `processing` bisa berlangsung beberapa menit. Copy memakai durasi nyata.
- Visit Note manual tetap ada sebagai cadangan ("Catat manual saja") saat rekaman gagal atau tanpa langganan.
