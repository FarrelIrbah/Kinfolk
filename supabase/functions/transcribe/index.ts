// Transcription (#45, ADR 0005). POST {appointment_id}, as the Attendee after uploading the audio to `recordings`:
// sends the job to our Whisper + LLM worker on RunPod Serverless (Singapore) and answers 202 at once; the Recording
// stays `processing`. POST ?appointment=…&secret=…, by RunPod when the job ends: saves its output (or the failure).
import { env, rpc } from "../_shared/send.ts";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (req) => {
  const url = new URL(req.url);
  const appointment = url.searchParams.get("appointment");
  if (appointment) {
    if (url.searchParams.get("secret") !== env("TRANSCRIBE_SECRET")) return json(401, { error: "unauthorized" });
    const job = await req.json().catch(() => ({}));
    if (job.status !== "COMPLETED") console.error("transcription failed", appointment, job.status, job.error);
    const res = await rpc("finish_recording", { appointment, output: job.status === "COMPLETED" ? job.output ?? null : null });
    return json(res.ok ? 200 : 500, res.body);
  }

  const { appointment_id } = await req.json().catch(() => ({}));
  const started = await rpc("start_recording", { appointment: appointment_id }, req.headers.get("authorization") ?? "");
  if (!started.ok) return json(started.status === 400 ? 403 : started.status, started.body);
  const { path, ...context } = started.body;

  const fail = async (why: unknown) => {
    console.error("transcription not sent", appointment_id, why);
    await rpc("finish_recording", { appointment: appointment_id, output: null });
    return json(502, { error: "worker unreachable" });
  };
  // ponytail: a day covers RunPod's queue plus a cold start; longer if jobs ever wait that long.
  const signed = await fetch(`${env("SUPABASE_URL")}/storage/v1/object/sign/recordings/${path}`, {
    method: "POST",
    headers: { authorization: `Bearer ${env("SUPABASE_SERVICE_ROLE_KEY")}`, "content-type": "application/json" },
    body: JSON.stringify({ expiresIn: 86400 }),
  });
  if (!signed.ok) return fail(await signed.text());
  const audio_url = `${env("SUPABASE_URL")}/storage/v1${(await signed.json()).signedURL}`;

  const webhook = `${env("TRANSCRIBE_WEBHOOK_URL")}?appointment=${appointment_id}&secret=${env("TRANSCRIBE_SECRET")}`;
  try {
    const res = await fetch(`${env("RUNPOD_API_URL") || "https://api.runpod.ai/v2"}/${env("RUNPOD_ENDPOINT_ID")}/run`, {
      method: "POST",
      headers: { authorization: `Bearer ${env("RUNPOD_API_KEY")}`, "content-type": "application/json" },
      body: JSON.stringify({ input: { audio_url, language: "id", ...context }, webhook }),
    });
    if (!res.ok) return fail(`${res.status} ${await res.text()}`);
  } catch (e) {
    return fail(e);
  }
  return json(202, {});
});
