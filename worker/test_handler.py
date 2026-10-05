# Checks the worker's output shape without a GPU: python worker/test_handler.py
from handler import prompt, shape

segments = [
    {"start": 0.04, "speaker": "SPEAKER_00", "text": "Clopidogrel diturunkan jadi 37,5 mg."},
    {"start": 6.52, "speaker": "SPEAKER_01", "text": "Boleh berhenti sebelum cabut gigi?"},
    {"start": 9.0, "speaker": "SPEAKER_02", "text": "Hmm."},
]
answer = {
    "speakers": {"SPEAKER_00": "provider", "SPEAKER_01": "attendee", "SPEAKER_02": "dokter"},
    "flagged": [1, 7, "x"],
    "qa": [{"question": "Boleh berhenti clopidogrel?", "answer": "Jangan.", "segments": [1, 9]}, {"question": ""}, "junk"],
    "next_steps": [{"text": "Antar fisioterapi", "owner": "Budi", "due": "2026-10-13", "segments": [0, 5]}, {"text": "MRI ulang", "owner": "", "due": "Selasa"}],
    "medication": {"name": "Clopidogrel", "change": "75 mg → 37,5 mg", "segment": 0},
}
out = shape(segments, answer)
assert [(s["t"], s["speaker"], s["flagged"]) for s in out["segments"]] == [(0.0, "provider", False), (6.5, "attendee", True), (9.0, "attendee", False)]
assert out["qa"] == [{"question": "Boleh berhenti clopidogrel?", "answer": "Jangan.", "segments": [1]}]
assert out["next_steps"] == [{"text": "Antar fisioterapi", "owner": "Budi", "due": "2026-10-13", "segments": [0]}, {"text": "MRI ulang", "owner": None, "due": None, "segments": []}]
assert out["medication"] == {"name": "Clopidogrel", "change": "75 mg → 37,5 mg", "segment": 0}
assert shape(segments, {"medication": {"name": "X"}, "qa": None})["medication"] is None
assert "[1] SPEAKER_01: Boleh berhenti" in prompt(segments, {"members": ["Sri", "Budi"]}, "2026-10-05")
print("ok")
