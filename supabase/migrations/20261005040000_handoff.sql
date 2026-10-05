-- Handoff (#49, see CONTEXT.md): after sharing, the Attendee sends one WhatsApp to each Member who wasn't in the room.
-- Owner-approved: not the Attendee, the Care Recipient or the Driver; like the share (#46), only Members who see Janji
-- dokter and Rekaman kunjungan. The dose line goes only to those who also see Obat.

-- Who it went to, for "Terkirim ke …"; null until sent.
alter table public.recordings add column handoff_told text[];

create function public.handoff_members(appointment uuid) returns setof public.members
language sql stable security definer set search_path = '' as $$
  select m.* from public.appointments a
  join public.recordings r on r.appointment_id = a.id
  join public.care_recipients cr on cr.id = a.recipient_id
  join public.members m on m.circle_id = a.circle_id
  where a.id = appointment and m.left_at is null and m.user_id <> r.recorded_by
    and m.user_id is distinct from cr.member_id and m.user_id is distinct from a.driver_id
    and public.member_sees(m.user_id, a.recipient_id, 'appointments') and public.member_sees(m.user_id, a.recipient_id, 'visit_notes')
  order by m.created_at
$$;
revoke execute on function public.handoff_members(uuid) from public, anon, authenticated;

-- "Dikirim ke": for the Attendee of a shared summary.
create function public.handoff_to(appointment uuid) returns uuid[]
language plpgsql stable security definer set search_path = '' as $$
begin
  if not exists (select 1 from public.recordings r where r.appointment_id = appointment and r.recorded_by = auth.uid() and r.shared_at is not null) then
    raise exception 'no shared summary';
  end if;
  return (select coalesce(array_agg(m.user_id), '{}') from public.handoff_members(appointment) m);
end $$;

-- "Kirim serah terima": once. Returns the names told, in join order.
create function public.send_handoff(appointment uuid) returns text[]
language plpgsql security definer set search_path = '' as $$
declare a public.appointments; r public.recordings; title text; said text; dose text; steps text; names text[];
begin
  select * into r from public.recordings
    where appointment_id = appointment and recorded_by = auth.uid() and shared_at is not null and handoff_told is null for update;
  if not found then raise exception 'nothing to hand off'; end if;
  select * into a from public.appointments where id = appointment;
  -- As inSentence() in the app: "kontrol neurologi", but "MRI otak".
  title := case when substr(a.title, 2, 1) ~ '[A-Z]' then a.title else lower(left(a.title, 1)) || substr(a.title, 2) end;
  select coalesce(string_agg(rtrim(trim(q ->> 'answer'), '.'), '; ' order by i), '-') into said
    from jsonb_array_elements(r.result -> 'qa') with ordinality x(q, i) where trim(q ->> 'answer') <> '';
  select d.name || ' dari ' || d.from_dose || ' ke ' || d.to_dose || '. Pengingat '
      || case when d.applied then 'sudah' else 'belum' end || ' diperbarui' into dose
    from public.recording_dose_changes d where d.appointment_id = appointment;
  select coalesce(string_agg(public.member_name(t.circle_id, t.owner_id) || ': ' || t.text || ', ' || public.day_month(t.due), '; ' order by t.position), '-')
    into steps from public.tasks t where t.appointment_id = appointment and t.source = 'step';
  with told as (
    insert into public.messages (user_id, kind, ref, template, params)
      select m.user_id, 'handoff', appointment,
        case when dose is not null and public.member_sees(m.user_id, a.recipient_id, 'medications') then 'kinfolk_handoff' else 'kinfolk_handoff_nomed' end,
        case when dose is not null and public.member_sees(m.user_id, a.recipient_id, 'medications')
          then array[public.member_name(a.circle_id, auth.uid()), title, said, dose, steps]
          else array[public.member_name(a.circle_id, auth.uid()), title, said, steps] end
      from public.handoff_members(appointment) m
      returning user_id)
  select coalesce(array_agg(m.name order by m.created_at), '{}') into names
    from told join public.members m on m.circle_id = a.circle_id and m.user_id = told.user_id;
  update public.recordings set handoff_told = names where appointment_id = appointment;
  return names;
end $$;

revoke execute on function public.handoff_to(uuid), public.send_handoff(uuid) from public, anon;
grant execute on function public.handoff_to(uuid), public.send_handoff(uuid) to authenticated;

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
  when 'kinfolk_handoff' then E'Serah terima dari {{1}}, setelah {{2}}.\nYang terjadi: {{3}}\nPerubahan obat: {{4}}\nSiapa mengerjakan apa: {{5}}'
  when 'kinfolk_handoff_nomed' then E'Serah terima dari {{1}}, setelah {{2}}.\nYang terjadi: {{3}}\nSiapa mengerjakan apa: {{4}}'
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;
