-- Summary checks and the medication change (#47, owner-approved). The worker marks summary lines to check ([check]
-- "Cek: …" on a Q&A answer or Next Step); the Attendee confirms each with "Sudah benar" ([checked]: "q0", "n1")
-- before sharing. The dose change it heard ("Perubahan obat") is applied by anyone who reads the Recording and sees
-- Obat: the Medication takes the new dose (so its reminders do), a Timeline entry is written and the circle is told
-- on WhatsApp.

alter table public.recordings add column checked text[] not null default '{}',
  add column ready_at timestamptz; -- "15.10 · ringkasan kunjungan siap" on Home

create function public.recording_ready() returns trigger language plpgsql set search_path = '' as $$
begin
  new.ready_at := case when new.status = 'ready' then coalesce(new.ready_at, now()) end;
  return new;
end $$;
create trigger recording_ready before update of status on public.recordings
  for each row execute function public.recording_ready();

-- The lines [r]'s worker asked to check that nobody has confirmed yet.
create function public.unchecked(r public.recordings) returns text[] language sql immutable set search_path = '' as $$
  select coalesce(array_agg(k), '{}') from (
    select 'q' || (i - 1) as k from jsonb_array_elements(coalesce(r.result -> 'qa', '[]')) with ordinality x(v, i)
      where coalesce(trim(v ->> 'check'), '') <> ''
    union all
    select 'n' || (i - 1) from jsonb_array_elements(coalesce(r.result -> 'next_steps', '[]')) with ordinality x(v, i)
      where coalesce(trim(v ->> 'check'), '') <> ''
  ) f where not k = any (r.checked)
$$;
revoke execute on function public.unchecked(public.recordings) from public, anon;

-- "Sudah benar": the Attendee, before sharing.
create function public.check_summary_line(appointment uuid, line text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.recordings r set checked = r.checked || line
    where r.appointment_id = appointment and r.recorded_by = auth.uid() and r.status = 'ready' and r.shared_at is null
      and line = any (public.unchecked(r));
  if not found then raise exception 'nothing to check'; end if;
end $$;

-- As in 20261005010000_visit_recording.sql, refused while a line is unchecked.
create or replace function public.share_recording(appointment uuid, answers jsonb, steps jsonb) returns text[]
language plpgsql security definer set search_path = '' as $$
declare r public.recordings; names text[];
begin
  select * into r from public.recordings
    where appointment_id = appointment and recorded_by = auth.uid() and status = 'ready' and shared_at is null for update;
  if not found then raise exception 'nothing to share'; end if;
  if cardinality(public.unchecked(r)) > 0 then raise exception 'lines to check'; end if;
  perform public.save_visit_note(appointment, answers, steps,
    coalesce((select n.notes from public.visit_notes n where n.appointment_id = appointment), ''));
  select coalesce(array_agg(mb.name order by mb.created_at), '{}') into names
    from public.messages m join public.members mb on mb.circle_id = r.circle_id and mb.user_id = m.user_id
    where m.kind = 'visit_note' and m.ref = appointment;
  update public.recordings set shared_at = now(), told = names where appointment_id = appointment;
  return names;
end $$;

-- An applied dose change, one per visit. [said_by]: the Provider; [t]: seconds into the Recording.
create table public.dose_changes (
  appointment_id uuid primary key,
  circle_id uuid not null,
  medication_id uuid not null references public.medications on delete cascade,
  from_dose text not null,
  to_dose text not null,
  said_by text not null,
  t numeric,
  by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  foreign key (circle_id, appointment_id) references public.appointments (circle_id, id) on delete cascade
);
alter table public.dose_changes enable row level security;
revoke insert, update, delete on public.dose_changes from authenticated;
create policy "members read dose changes" on public.dose_changes
  for select to authenticated using (public.sees_medication(medication_id));

-- The dose change in each Recording the Member reads, of an active Medication they see. [applied]: applied here, or
-- the Medication already has the new dose. [said_by]: the Provider.
create view public.recording_dose_changes with (security_invoker = true) as
  select r.appointment_id, r.circle_id, md.id as medication_id, md.name, coalesce(c.from_dose, md.dose) as from_dose,
    r.result -> 'medication' ->> 'dose' as to_dose,
    (r.result -> 'segments' -> ((r.result -> 'medication' ->> 'segment')::int) ->> 't')::numeric as t,
    c.appointment_id is not null or lower(md.dose) = lower(r.result -> 'medication' ->> 'dose') as applied,
    coalesce(c.said_by, p.name) as said_by
  from public.recordings r
  join public.medications md on md.id = (r.result -> 'medication' ->> 'medication_id')::uuid and md.active
  left join public.dose_changes c on c.appointment_id = r.appointment_id
  left join public.appointments a on a.id = r.appointment_id
  left join public.providers p on p.id = a.provider_id
  where r.status = 'ready' and coalesce(trim(r.result -> 'medication' ->> 'dose'), '') <> ''
    -- ponytail: one not applied within a week is dropped, so a skipped change doesn't nag forever; a "Lewati"
    -- button if families want to dismiss sooner.
    and (c.appointment_id is not null or lower(md.dose) = lower(r.result -> 'medication' ->> 'dose')
      or coalesce(r.ready_at, r.created_at) > now() - interval '7 days');

-- "Perbarui pengingat". Returns the names told on WhatsApp (the other Members who see Obat, not the Care Recipient),
-- in join order.
create function public.apply_dose_change(appointment uuid) returns text[]
language plpgsql security definer set search_path = '' as $$
declare a public.appointments; r public.recordings; md public.medications; new_dose text; provider text; names text[];
begin
  select * into a from public.appointments where id = appointment;
  select * into r from public.recordings
    where appointment_id = appointment and status = 'ready' and (recorded_by = auth.uid() or shared_at is not null);
  select * into md from public.medications where id = (r.result -> 'medication' ->> 'medication_id')::uuid and active;
  new_dose := trim(r.result -> 'medication' ->> 'dose');
  if md.id is null or coalesce(new_dose, '') = '' or not public.is_writer(a.circle_id)
    or not public.sees(a.recipient_id, 'visit_notes') or not public.sees(a.recipient_id, 'medications') then
    raise exception 'no dose change to apply';
  end if;
  select p.name into provider from public.providers p where p.id = a.provider_id;
  insert into public.dose_changes (appointment_id, circle_id, medication_id, from_dose, to_dose, said_by, t)
    values (appointment, a.circle_id, md.id, md.dose, new_dose, provider,
      (r.result -> 'segments' -> ((r.result -> 'medication' ->> 'segment')::int) ->> 't')::numeric);
  update public.medications set dose = new_dose where id = md.id;
  with told as (
    insert into public.messages (user_id, kind, ref, template, params)
      select m.user_id, 'dose_change', appointment, 'kinfolk_dose_change',
        array[public.member_name(a.circle_id, auth.uid()), cr.name, md.name, md.dose, new_dose, provider]
      from public.members m join public.care_recipients cr on cr.id = a.recipient_id
      where m.circle_id = a.circle_id and m.left_at is null and m.user_id <> auth.uid()
        and m.user_id is distinct from cr.member_id and public.member_sees(m.user_id, a.recipient_id, 'medications')
      returning user_id)
  select coalesce(array_agg(m.name order by m.created_at), '{}') into names
    from told join public.members m on m.circle_id = a.circle_id and m.user_id = told.user_id;
  return names;
end $$;

-- "Urungkan" by whoever applied it: the old dose comes back, the entry goes, and WhatsApp not yet sent is dropped.
create function public.undo_dose_change(appointment uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare c public.dose_changes;
begin
  delete from public.dose_changes where appointment_id = appointment and by = auth.uid() returning * into c;
  if not found then raise exception 'nothing to undo'; end if;
  -- Only while the dose is still the one applied, so a later change or edit isn't overwritten.
  update public.medications set dose = c.from_dose where id = c.medication_id and dose = c.to_dose;
  if not found then raise exception 'dose changed since'; end if;
  delete from public.messages where kind = 'dose_change' and ref = appointment and sent_at is null;
end $$;

revoke execute on function public.check_summary_line(uuid, text), public.apply_dose_change(uuid),
  public.undo_dose_change(uuid) from public, anon;
grant execute on function public.check_summary_line(uuid, text), public.apply_dose_change(uuid),
  public.undo_dose_change(uuid) to authenticated;

-- Applied dose changes join the Timeline as Obat entries, "Amlodipine dari 5 mg ke 10 mg (Dr. Anand Rao, menit 02:10
-- rekaman). Pengingat diperbarui.", by whoever applied it, seen by whoever sees Obat.
create or replace view public.timeline with (security_invoker = true) as
  select 'appointment' as kind, a.id as appointment_id, a.circle_id, a.created_by as by, m.name as by_name,
    a.created_at as at, a.title, p.name as provider, a.starts_at, '{}'::text[] as next_steps, '' as notes, '' as text
  from public.appointments a
  join public.providers p on p.id = a.provider_id
  left join public.members m on m.circle_id = a.circle_id and m.user_id = a.created_by
  where a.cancelled_at is null
union all
  select 'visit_note', a.id, a.circle_id, n.written_by, m.name, n.updated_at, a.title, p.name, a.starts_at,
    n.next_steps, n.notes, ''
  from public.visit_notes n
  join public.appointments a on a.id = n.appointment_id
  join public.providers p on p.id = a.provider_id
  left join public.members m on m.circle_id = n.circle_id and m.user_id = n.written_by
  where a.cancelled_at is null
union all
  select e.kind, e.appointment_id, e.circle_id, e.by, m.name, e.at, a.title, p.name, a.starts_at, '{}', '', e.text
  from public.timeline_events e
  join public.appointments a on a.id = e.appointment_id
  join public.providers p on p.id = a.provider_id
  left join public.members m on m.circle_id = e.circle_id and m.user_id = e.by
  where a.cancelled_at is null
union all
  select 'access_change', null, c.circle_id, c.by, m.name, c.at, null, null, null, '{}', '', c.text
  from public.access_changes c
  left join public.members m on m.circle_id = c.circle_id and m.user_id = c.by
union all
  select 'dose_given', null, d.circle_id, d.given_by, m.name, d.at, null, null, null, '{}', '',
    trim(md.name || ' ' || md.dose) || ' diberikan.'
  from public.dose_logs d
  join public.medications md on md.id = d.medication_id
  left join public.members m on m.circle_id = d.circle_id and m.user_id = d.given_by
union all
  select 'check_in', null, k.circle_id, k.by, m.name, k.at, null, null, null, '{}', '',
    'Telepon malam: tensi ' || k.sys || '/' || k.dia || ', '
    || case k.ate when 'yes' then 'sudah makan malam' when 'some' then 'makan sedikit' else 'tidak makan malam' end || ', '
    || case when k.walked then 'berjalan' else 'tidak berjalan' end || ', '
    || case k.mood when 'good' then 'suasana hati baik' when 'okay' then 'biasa saja' else 'murung' end || '.'
    || case when trim(k.note) <> '' then ' ' || trim(k.note) else '' end
  from public.check_ins k
  left join public.members m on m.circle_id = k.circle_id and m.user_id = k.by
union all
  select 'document', null, d.circle_id, d.by, m.name, d.at, null, null, null, '{}', '',
    -- The first letter lowered ("laporan MRI otak"), unless an acronym starts it ("MRI otak", "USG perut").
    'Mengunggah ' || case when substr(d.name, 2, 1) <> lower(substr(d.name, 2, 1)) then d.name
      else lower(left(d.name, 1)) || substr(d.name, 2) end
    || case when d.version > 1 then ' (versi ' || d.version || ')' else '' end || '.'
  from public.documents d
  left join public.members m on m.circle_id = d.circle_id and m.user_id = d.by
union all
  select 'export', null, e.circle_id, e.by, m.name, e.at, null, null, null, '{}', '',
    'Mengekspor PDF ' || e.pages || ' halaman' || case when e.prepared_for <> '' then ' untuk ' || e.prepared_for else '' end
    || '. Tautan berlaku 7 hari.'
  from public.exports e
  left join public.members m on m.circle_id = e.circle_id and m.user_id = e.by
union all
  select 'recipient_press', null, p.circle_id, p.by, r.name, p.at, null, null, null, '{}', '', p.text
  from public.recipient_presses p
  join public.care_recipients r on r.id = p.recipient_id
union all
  select 'dose_change', null, c.circle_id, c.by, m.name, c.at, null, null, null, '{}', '',
    md.name || ' dari ' || c.from_dose || ' ke ' || c.to_dose || ' (' || c.said_by
    || case when c.t is not null then ', menit ' || lpad((floor(c.t)::int / 60)::text, 2, '0') || ':'
      || lpad((floor(c.t)::int % 60)::text, 2, '0') || ' rekaman' else '' end || '). Pengingat diperbarui.'
  from public.dose_changes c
  join public.medications md on md.id = c.medication_id
  left join public.members m on m.circle_id = c.circle_id and m.user_id = c.by;

create or replace function public.template_body(template text, params text[]) returns text
language plpgsql immutable set search_path = '' as $$
declare body text := case template
  when 'kinfolk_swap_ask' then '{{1}} bertanya: bisa ambil {{2}} {{3}}? Balas YA atau TIDAK.'
  when 'kinfolk_swap_yes' then '{{1}} pegang {{2}} hari {{3}}.'
  when 'kinfolk_swap_no' then '{{1}} tidak bisa ambil {{2}} hari {{3}}.'
  when 'kinfolk_drive_ask' then '{{1}} bertanya: bisa mengantar {{2}} ke {{3}}, {{4}}? Balas YA atau TIDAK.'
  when 'kinfolk_drive_yes' then '{{1}} mengantar {{2}} {{3}}.'
  when 'kinfolk_drive_no' then '{{1}} tidak bisa mengantar {{2}} {{3}}.'
  when 'kinfolk_drive_reminder' then 'Hari ini: antar {{1}} ke {{2}} jam {{3}}.'
  when 'kinfolk_duty_reminder' then 'Hari ini: {{1}} jam {{2}}.'
  when 'kinfolk_visit_note' then '{{1}} menulis catatan kunjungan {{2}}. {{3}}.'
  when 'kinfolk_bp_high' then 'Tensi {{1}} malam ini {{2}}, 140 ke atas. Dicatat oleh {{3}}.'
  when 'kinfolk_task_reminder' then '{{1}} mengingatkan: {{2}}, tenggat {{3}}.'
  when 'kinfolk_recipient_ok' then '{{1}} baik-baik saja. Dikirim dari Mode {{1}}.'
  when 'kinfolk_recipient_help' then '{{1}} butuh bantuan. {{2}} sedang dihubungi.'
  when 'kinfolk_dose_reminder' then '{{1}}: {{2}} jam {{3}}. Balas 1 jika sudah diberikan.'
  when 'kinfolk_digest' then E'Kinfolk · Minggu {{1}}, {{2}}\n• {{3}}\n• {{4}}\n• {{5}}\n• {{6}}\nBerikutnya: {{7}}.\nKosong: {{8}}.'
  when 'kinfolk_question_moved' then '{{1}} memindahkan pertanyaan Anda "{{2}}" ke kunjungan {{3}}.'
  when 'kinfolk_dose_change' then '{{1}} memperbarui pengingat obat {{2}}: {{3}} dari {{4}} ke {{5}}, sesuai {{6}}.'
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;
