"""Kinfolk transcription worker (ADR 0005), run on RunPod Serverless in Singapore.

Input (from supabase/functions/transcribe): audio_url, language, provider, members, medications, questions.
Output (saved by public.finish_recording): segments [{t, speaker, text, flagged}], qa [{question, answer, segments}],
next_steps [{text, owner, due}], medication {name, change, segment} or null.

Whisper large-v3 with WhisperX alignment and pyannote diarisation, then an open-weight LLM (vLLM) labels the
speakers and pulls out the rest. Nothing leaves the worker.
"""
import datetime
import json
import os
import tempfile
import urllib.request

SPEAKERS = {"provider", "recipient", "attendee"}
LLM = os.environ.get("LLM_MODEL", "Qwen/Qwen2.5-32B-Instruct-AWQ")
_models = {}


def _load():
    # Loaded once per warm worker, not at import, so shape() can be checked without a GPU.
    if not _models:
        import whisperx
        from vllm import LLM as VLLM
        _models["whisper"] = whisperx.load_model("large-v3", "cuda", compute_type="float16")
        _models["diarize"] = whisperx.DiarizationPipeline(use_auth_token=os.environ["HF_TOKEN"], device="cuda")
        # ponytail: both models share one 48 GB GPU; split into two endpoints if memory runs out.
        _models["llm"] = VLLM(LLM, gpu_memory_utilization=0.6, max_model_len=16384)
    return _models


def transcribe(path, language):
    import whisperx
    m = _load()
    audio = whisperx.load_audio(path)
    result = m["whisper"].transcribe(audio, language=language, batch_size=16)
    align, meta = whisperx.load_align_model(language_code=language, device="cuda")
    result = whisperx.align(result["segments"], align, meta, audio, "cuda")
    result = whisperx.assign_word_speakers(m["diarize"](audio), result)
    return [{"start": s["start"], "speaker": s.get("speaker", "?"), "text": s["text"].strip()} for s in result["segments"]]


def prompt(segments, job, today):
    lines = "\n".join(f"[{i}] {s['speaker']}: {s['text']}" for i, s in enumerate(segments))
    return f"""Ini transkrip kunjungan dokter dalam bahasa Indonesia. Hari ini {today}.
Dokter/tenaga kesehatan: {job.get('provider') or '-'}. Anggota keluarga: {', '.join(job.get('members', [])) or '-'}.
Obat yang sedang diminum: {', '.join(job.get('medications', [])) or '-'}.
Pertanyaan keluarga untuk kunjungan ini: {json.dumps(job.get('questions', []), ensure_ascii=False)}

Transkrip (nomor baris dalam kurung siku):
{lines}

Jawab hanya dengan JSON:
{{"speakers": {{"<label pembicara>": "provider" | "recipient" (pasien) | "attendee" (keluarga yang hadir)}},
 "flagged": [nomor baris yang kurang jelas atau perlu dicek ulang],
 "qa": [{{"question": pertanyaan keluarga di atas atau yang ditanyakan saat kunjungan, "answer": jawaban dokter singkat, "segments": [nomor baris]}}],
 "next_steps": [{{"text": langkah berikutnya, "owner": nama anggota keluarga yang disebut atau null, "due": "YYYY-MM-DD" atau null}}],
 "medication": {{"name": nama obat, "change": perubahan dosis singkat, "segment": nomor baris}} atau null}}"""


def ask(text):
    from vllm import SamplingParams
    out = _load()["llm"].chat([{"role": "user", "content": text}], SamplingParams(temperature=0, max_tokens=4096))
    reply = out[0].outputs[0].text
    return json.loads(reply[reply.find("{"):reply.rfind("}") + 1])


def shape(segments, answer):
    """The worker's output from the diarised segments and the LLM's answer, keeping only what the app can read."""
    n = len(segments)
    lines = lambda xs: [i for i in xs if isinstance(i, int) and 0 <= i < n] if isinstance(xs, list) else []
    speakers = answer.get("speakers") or {}
    flagged = set(lines(answer.get("flagged")))
    text = lambda v: v.strip() if isinstance(v, str) else ""

    def due(v):
        try:
            return datetime.date.fromisoformat(v).isoformat()
        except (TypeError, ValueError):
            return None

    med = answer.get("medication")
    med = {"name": text(med.get("name")), "change": text(med.get("change")),
           "segment": (lines([med.get("segment")]) or [None])[0]} if isinstance(med, dict) else None
    return {
        # ponytail: an unlabelled voice counts as the family member present; the Attendee checks before sharing.
        "segments": [{"t": round(s["start"], 1), "speaker": speakers.get(s["speaker"]) if speakers.get(s["speaker"]) in SPEAKERS else "attendee",
                      "text": s["text"], "flagged": i in flagged} for i, s in enumerate(segments)],
        "qa": [{"question": text(q.get("question")), "answer": text(q.get("answer")), "segments": lines(q.get("segments"))}
               for q in answer.get("qa") or [] if isinstance(q, dict) and text(q.get("question"))],
        "next_steps": [{"text": text(s.get("text")), "owner": text(s.get("owner")) or None, "due": due(s.get("due"))}
                       for s in answer.get("next_steps") or [] if isinstance(s, dict) and text(s.get("text"))],
        "medication": med if med and med["name"] and med["change"] else None,
    }


def handler(job):
    job = job["input"]
    with tempfile.NamedTemporaryFile(suffix=".m4a") as f:
        f.write(urllib.request.urlopen(job["audio_url"], timeout=300).read())
        f.flush()
        segments = transcribe(f.name, job.get("language", "id"))
    if not segments:
        return {"segments": [], "qa": [], "next_steps": [], "medication": None}
    today = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=7)).date().isoformat()  # WIB
    return shape(segments, ask(prompt(segments, job, today)))


if __name__ == "__main__":
    import runpod
    runpod.serverless.start({"handler": handler})
