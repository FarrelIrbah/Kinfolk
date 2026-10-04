-- Recording and Transcript (#45, ADR 0005; see CONTEXT.md). The Attendee uploads a visit's audio to the private
-- `recordings` bucket at <circle>/<appointment>; the `transcribe` Edge Function sends it to our own worker on RunPod
-- and saves what comes back. Only the Attendee reads the result until they share it; then it follows Rekaman
-- kunjungan (the visit_notes Data Category), like Visit Notes.
-- ponytail: "encrypted" is Storage's encryption at rest; per-file keys if audio must be unreadable to the database too.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('recordings', 'recordings', false, 52428800, array['audio/mp4', 'audio/aac', 'audio/mpeg', 'audio/wav', 'audio/webm']);

-- [result], once ready: segments [{t (seconds), speaker: provider | recipient | attendee, text, flagged}],
-- qa [{question, answer, segments: [segment index]}], next_steps [{text, owner (name), owner_id, due}],
-- medication {name, change, segment, medication_id} or null.
create table public.recordings (
  appointment_id uuid primary key,
  circle_id uuid not null,
  recorded_by uuid not null default auth.uid(),
  path text generated always as (circle_id::text || '/' || appointment_id::text) stored,
  status text not null default 'processing' check (status in ('processing', 'ready', 'failed')),
  result jsonb,
  shared_at timestamptz,
  created_at timestamptz not null default now(),
  foreign key (circle_id, appointment_id) references public.appointments (circle_id, id) on delete cascade,
  foreign key (circle_id, recorded_by) references public.members (circle_id, user_id)
);
alter table public.recordings enable row level security;
revoke insert, update, delete on public.recordings from authenticated;

create policy "members read recordings" on public.recordings
  for select to authenticated using (
    public.is_member(circle_id) and (recorded_by = auth.uid() or shared_at is not null)
    and exists (select 1 from public.appointments a where a.id = appointment_id and public.sees(a.recipient_id, 'visit_notes')));

-- The Attendee uploads, and may upload again unless a job is running or done. Nobody else reads the audio.
create policy "attendee reads recording files" on storage.objects
  for select to authenticated using (bucket_id = 'recordings' and public.is_attendee((storage.filename(name))::uuid));
create policy "attendee uploads recording files" on storage.objects
  for insert to authenticated with check (bucket_id = 'recordings' and public.is_attendee((storage.filename(name))::uuid));
create policy "attendee replaces failed recording files" on storage.objects
  for update to authenticated using (
    bucket_id = 'recordings' and public.is_attendee((storage.filename(name))::uuid)
    and not exists (select 1 from public.recordings r where r.path = objects.name and r.status <> 'failed'));

-- Called by `transcribe` as the Attendee: starts a job, or restarts one that failed or was lost (no webhook within
-- an hour), and returns what the worker needs to know besides the audio, so it can name owners and Medications as
-- the circle does.
create function public.start_recording(appointment uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare a public.appointments;
begin
  select * into a from public.appointments where id = appointment;
  if not public.is_attendee(appointment) or not public.sees(a.recipient_id, 'visit_notes') then
    raise exception 'only the attendee records';
  end if;
  insert into public.recordings (appointment_id, circle_id, recorded_by) values (appointment, a.circle_id, auth.uid())
    on conflict (appointment_id) do update set status = 'processing', result = null, recorded_by = auth.uid(), created_at = now()
    where public.recordings.status = 'failed'
      or (public.recordings.status = 'processing' and public.recordings.created_at < now() - interval '1 hour');
  if not found then raise exception 'already recorded'; end if;
  return jsonb_build_object(
    'path', a.circle_id::text || '/' || appointment::text,
    'provider', (select p.name from public.providers p where p.id = a.provider_id),
    'members', (select coalesce(jsonb_agg(m.name order by m.created_at), '[]') from public.members m
      where m.circle_id = a.circle_id and m.left_at is null),
    'medications', (select coalesce(jsonb_agg(m.name order by m.name), '[]') from public.medications m
      where m.recipient_id = a.recipient_id and m.active),
    'questions', (select coalesce(jsonb_agg(q.text order by q.created_at), '[]') from public.appointment_questions q
      where q.appointment_id = appointment));
end $$;

-- Called by `transcribe` with the worker's output, or null when the job failed. A Next Step's owner and the
-- Medication are matched by name (ignoring case); no single match leaves them null for the Attendee to pick.
create function public.finish_recording(appointment uuid, output jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare r public.recordings; recipient uuid;
begin
  select * into r from public.recordings where appointment_id = appointment and status = 'processing' for update;
  if not found then return; end if; -- a late or repeated webhook
  if output is null then
    update public.recordings set status = 'failed' where appointment_id = appointment;
    return;
  end if;
  select a.recipient_id into recipient from public.appointments a where a.id = appointment;
  update public.recordings set status = 'ready', result = jsonb_build_object(
    'segments', coalesce(output -> 'segments', '[]'),
    'qa', coalesce(output -> 'qa', '[]'),
    'next_steps', (select coalesce(jsonb_agg(s || jsonb_build_object('owner_id', (
        select (array_agg(m.user_id))[1] from public.members m
        where m.circle_id = r.circle_id and m.left_at is null and lower(m.name) = lower(s ->> 'owner')
        having count(*) = 1))), '[]')
      from jsonb_array_elements(coalesce(output -> 'next_steps', '[]')) s),
    'medication', case when jsonb_typeof(output -> 'medication') = 'object' then
      output -> 'medication' || jsonb_build_object('medication_id', (
        select (array_agg(m.id))[1] from public.medications m
        where m.recipient_id = recipient and m.active and lower(m.name) = lower(output -> 'medication' ->> 'name')
        having count(*) = 1)) end)
  where appointment_id = appointment;
end $$;

-- "Bagikan": the Attendee shows the checked result to the circle.
create function public.share_recording(appointment uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.recordings set shared_at = coalesce(shared_at, now())
    where appointment_id = appointment and recorded_by = auth.uid() and status = 'ready';
  if not found then raise exception 'nothing to share'; end if;
end $$;

revoke execute on function public.start_recording(uuid), public.finish_recording(uuid, jsonb),
  public.share_recording(uuid) from public, anon, authenticated;
grant execute on function public.start_recording(uuid), public.share_recording(uuid) to authenticated;
