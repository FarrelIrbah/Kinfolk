-- Recording a visit and sharing its summary (#46). The Attendee checks the transcribed result, then "Bagikan ke
-- lingkaran" saves it as the Visit Note (whose first save tells the circle on WhatsApp, Members hidden from Rekaman
-- kunjungan excepted, as before) and shows the Recording to everyone who sees Rekaman kunjungan, in one go.

-- The audio's length, for `processing` and the summary's "rekaman 4 menit"; who the share went to on WhatsApp, for
-- "Dibagikan · WhatsApp ke 2 saudara".
alter table public.recordings add column seconds int not null default 0 check (seconds >= 0),
  add column told text[] not null default '{}';

-- As in 20261005000000_recordings.sql, plus the length.
drop function public.start_recording(uuid);
create function public.start_recording(appointment uuid, seconds int) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare a public.appointments;
begin
  select * into a from public.appointments where id = appointment;
  if not public.is_attendee(appointment) or not public.sees(a.recipient_id, 'visit_notes') then
    raise exception 'only the attendee records';
  end if;
  insert into public.recordings (appointment_id, circle_id, recorded_by, seconds)
    values (appointment, a.circle_id, auth.uid(), greatest(coalesce(start_recording.seconds, 0), 0))
    on conflict (appointment_id) do update set status = 'processing', result = null, recorded_by = auth.uid(),
      seconds = excluded.seconds, created_at = now()
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

-- "Bagikan ke lingkaran": [answers] and [steps] as save_visit_note takes them, from the checked summary. Returns the
-- names told on WhatsApp, in join order.
drop function public.share_recording(uuid);
create function public.share_recording(appointment uuid, answers jsonb, steps jsonb) returns text[]
language plpgsql security definer set search_path = '' as $$
declare r public.recordings; names text[];
begin
  select * into r from public.recordings
    where appointment_id = appointment and recorded_by = auth.uid() and status = 'ready' and shared_at is null for update;
  if not found then raise exception 'nothing to share'; end if;
  perform public.save_visit_note(appointment, answers, steps,
    coalesce((select n.notes from public.visit_notes n where n.appointment_id = appointment), ''));
  select coalesce(array_agg(mb.name order by mb.created_at), '{}') into names
    from public.messages m join public.members mb on mb.circle_id = r.circle_id and mb.user_id = m.user_id
    where m.kind = 'visit_note' and m.ref = appointment;
  update public.recordings set shared_at = now(), told = names where appointment_id = appointment;
  return names;
end $$;

revoke execute on function public.start_recording(uuid, int), public.share_recording(uuid, jsonb, jsonb) from public, anon, authenticated;
grant execute on function public.start_recording(uuid, int), public.share_recording(uuid, jsonb, jsonb) to authenticated;

-- "Pindah ke kunjungan berikut" (approved by the owner in #46): an unanswered Question goes to the Care Recipient's
-- next Appointment with any Provider, and carries over from there as usual.
alter table public.questions add column moved_to uuid,
  add foreign key (circle_id, moved_to) references public.appointments (circle_id, id) on delete set null (moved_to);

create or replace view public.appointment_questions with (security_invoker = true) as
  select x.appointment_id, q.id, q.circle_id, q.appointment_id as asked_in, o.starts_at as asked_for,
    q.asked_by, m.name as asked_by_name, q.text, x.answer, q.created_at
  from public.answers x
  join public.questions q on q.id = x.question_id
  join public.appointments o on o.id = q.appointment_id
  left join public.members m on m.circle_id = q.circle_id and m.user_id = q.asked_by
union all
  select a.id, q.id, q.circle_id, q.appointment_id, o.starts_at, q.asked_by, m.name, q.text, null, q.created_at
  from public.questions q
  join public.appointments o on o.id = q.appointment_id
  join public.appointments f on f.id = coalesce(q.moved_to, q.appointment_id) -- where it carries over from
  left join public.members m on m.circle_id = q.circle_id and m.user_id = q.asked_by
  cross join lateral (
    select a.id from public.appointments a
    where a.recipient_id = f.recipient_id and a.provider_id = f.provider_id and a.cancelled_at is null
      and a.starts_at >= f.starts_at
      and not public.has_visit_note(a.id)
    order by a.starts_at, a.id <> f.id, a.id
    limit 1
  ) a
  where not public.is_answered(q.id);

-- By the Attendee of [from], where the Question sits now. Tells whoever asked it on WhatsApp, unless that's the
-- Attendee, a Former Member, or someone who can't see Janji dokter. Returns {title, starts_at, told} for the toast.
create function public.move_question(question uuid, from_appointment uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare f public.appointments; t public.appointments; q public.questions; told text;
begin
  if not public.is_attendee(from_appointment) then raise exception 'only the attendee moves questions'; end if;
  select * into f from public.appointments where id = from_appointment;
  select * into q from public.questions where id = question and circle_id = f.circle_id;
  if not exists (select 1 from public.appointment_questions x where x.id = question and x.appointment_id = from_appointment and x.answer is null) then
    raise exception 'not an open question of this visit';
  end if;
  select * into t from public.appointments a
    where a.recipient_id = f.recipient_id and a.cancelled_at is null and a.starts_at > f.starts_at and not public.has_visit_note(a.id)
    order by a.starts_at, a.id limit 1;
  if not found then raise exception 'no next visit'; end if;
  update public.questions set moved_to = t.id where id = question;
  select m.name into told from public.members m
    where m.circle_id = q.circle_id and m.user_id = q.asked_by and m.left_at is null and m.user_id <> auth.uid()
      and public.member_sees(m.user_id, t.recipient_id, 'appointments');
  if told is not null then
    insert into public.messages (user_id, kind, ref, template, params)
      values (q.asked_by, 'question_moved', question, 'kinfolk_question_moved', array[
        public.member_name(q.circle_id, auth.uid()), q.text,
        lower(left(t.title, 1)) || substr(t.title, 2) || ' ' || public.day_month(public.wib(t.starts_at)::date)]);
  end if;
  return jsonb_build_object('title', t.title, 'starts_at', t.starts_at, 'told', told);
end $$;
revoke execute on function public.move_question(uuid, uuid) from public, anon;
grant execute on function public.move_question(uuid, uuid) to authenticated;

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
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- As in 20261003010000_search.sql, with v3's Transkrip after Obat: one hit per transcript line the Member can read
-- (the Attendee's own before sharing). [segment] is the line's index, [label] "00:41 · Dr. Anand Rao · Kontrol
-- neurologi" (the app adds the day), [at] the Appointment's start.
drop function public.search(uuid, text);
create function public.search(circle uuid, query text)
returns table (kind text, id uuid, entry text, at timestamptz, text text, segment int, label text)
language sql stable set search_path = '' as $$
  with q as (
    select to_tsquery('simple', string_agg(quote_literal(w.lexeme) || ':*', ' & ')) as q
    from unnest(to_tsvector('simple', query)) w
    where length(trim(query)) >= 2
  ), hits as (
    select 1 as k, 'medication' as kind, m.id, null as entry, null::timestamptz as at, null as text,
      null::int as segment, null as label, row_number() over (order by m.time_of_day, m.name) as n
    from public.medications m, q
    where m.circle_id = circle and to_tsvector('simple', concat_ws(' ', m.name, m.dose, m.schedule,
      case when m.blood_thinner then 'Pengencer darah' end)) @@ q.q
  union all
    select 2, 'transcript', r.appointment_id, null, a.starts_at, s ->> 'text', (i - 1)::int,
      lpad((floor((s ->> 't')::numeric)::int / 60)::text, 2, '0') || ':' || lpad((floor((s ->> 't')::numeric)::int % 60)::text, 2, '0')
        || ' · ' || case s ->> 'speaker'
          when 'provider' then p.name
          when 'recipient' then cr.name
          else coalesce(public.member_name(r.circle_id, r.recorded_by), '') end
        || ' · ' || a.title,
      row_number() over (order by a.starts_at desc, i)
    from public.recordings r
    join public.appointments a on a.id = r.appointment_id
    join public.providers p on p.id = a.provider_id
    join public.care_recipients cr on cr.id = a.recipient_id
    cross join lateral jsonb_array_elements(r.result -> 'segments') with ordinality x(s, i), q
    where r.circle_id = circle and r.status = 'ready' and to_tsvector('simple', s ->> 'text') @@ q.q
  union all
    select 3, 'document', d.id, null, null, null, null, null, row_number() over (order by d.at desc)
    from (select distinct on (lower(name), legal) * from public.documents
          where circle_id = circle order by lower(name), legal, at desc) d, q
    where to_tsvector('simple', d.name) @@ q.q
  union all
    select 4, 'timeline', t.appointment_id, t.kind, t.at, t.text, null, null, row_number() over (order by t.at desc)
    from public.timeline t, q
    where t.circle_id = circle and to_tsvector('simple', concat_ws(' ', t.title, t.provider, t.text, t.notes,
      array_to_string(t.next_steps, ' '), t.by_name)) @@ q.q
  union all
    select 5, 'contact', c.id, null, null, null, null, null, row_number() over (order by c.name)
    from public.care_contacts c, q
    where c.circle_id = circle and to_tsvector('simple', concat_ws(' ', c.name, c.relationship)) @@ q.q
  union all
    select 6, 'task', t.id, null, null, null, null, null, row_number() over (order by t.due, t.text)
    from public.tasks t
    left join public.members o on o.circle_id = t.circle_id and o.user_id = t.owner_id, q
    where t.circle_id = circle and to_tsvector('simple', concat_ws(' ', t.text, o.name)) @@ q.q
  )
  select kind, id, entry, at, text, segment, label from hits order by k, n limit 14
$$;

revoke execute on function public.search(uuid, text) from public, anon;
grant execute on function public.search(uuid, text) to authenticated;
