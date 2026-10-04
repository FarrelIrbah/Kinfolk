-- WhatsApp dose reminder (#38): 15 minutes after a Medication's time, while today's Dose Log is missing, every Member
-- holding a Duty today gets "Malam ini: atorvastatin jam 21.00. Balas 1 jika sudah diberikan." (v3 sms `med`; the
-- first words follow the time, owner-approved in #38). Replying 1 logs the dose: "Tercatat: atorvastatin diberikan,
-- 21.02. Lingkaran bisa melihatnya." (v3 sms `oneR`).

drop index public.messages_kind_ref_user_id_day_idx;
create unique index on public.messages (kind, ref, user_id, day) where kind in ('drive_reminder', 'duty_reminder', 'dose_reminder');

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
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- "Malam ini", as Mode Bapak's greeting (#37): pagi before 11, siang before 15, sore before 18.
create function public.part_of_day(t time) returns text language sql immutable set search_path = '' as $$
  select case when t < '11:00' then 'Pagi ini' when t < '15:00' then 'Siang ini' when t < '18:00' then 'Sore ini' else 'Malam ini' end
$$;

-- As in 20261001060000_tasks.sql, plus dose reminders.
-- ponytail: a dose reminder goes between 15 minutes and an hour after its time, so a late-night dose (after 23.45)
-- misses it and a minute job down for an hour skips it.
create or replace function public.queue_reminders(at timestamptz) returns void language sql security definer set search_path = '' as $$
  insert into public.messages (user_id, kind, ref, day, template, params)
    select a.driver_id, 'drive_reminder', a.id, public.wib(a.starts_at)::date, 'kinfolk_drive_reminder', array[
      r.name, a.title,
      public.one_line(public.hm(public.wib(a.starts_at)::time) || coalesce(', berangkat ' || public.hm(public.wib(a.departs_at)::time), '')
        || case when trim(a.bring) <> '' then '. Bawa: ' || a.bring else '' end)]
    from public.appointments a
    join public.care_recipients r on r.id = a.recipient_id
    join public.members m on m.circle_id = a.circle_id and m.user_id = a.driver_id and m.left_at is null
    where a.cancelled_at is null
      and at >= coalesce(a.departs_at, a.starts_at) - interval '2 hours' and at < coalesce(a.departs_at, a.starts_at)
      and public.member_sees(a.driver_id, a.recipient_id, 'appointments')
  on conflict do nothing;

  insert into public.messages (user_id, kind, ref, day, template, params)
    select h.holder, 'duty_reminder', d.id, public.wib(at)::date, 'kinfolk_duty_reminder', array[public.in_sentence(d.name), public.hm(d.time_of_day)]
    from public.duties d
    cross join lateral (select public.duty_holder(d.id, public.wib(at)::date) as holder) h
    where h.holder is not null
      and public.wib(at)::time >= d.time_of_day - interval '1 hour' and public.wib(at)::time < d.time_of_day
  on conflict do nothing;

  insert into public.tasks (circle_id, recipient_id, text, owner_id, due, source, medication_id, refill_on, added_by)
    select md.circle_id, md.recipient_id, 'Ambil isi ulang ' || md.name, md.refill_by, md.refill_on, 'refill', md.id, md.refill_on, md.refill_by
    from public.medications md
    join public.members m on m.circle_id = md.circle_id and m.user_id = md.refill_by and m.left_at is null
    where md.active and md.refill_on is not null and public.wib(at)::date >= md.refill_on - 4
  on conflict do nothing;

  insert into public.messages (user_id, kind, ref, day, template, params)
    select distinct h.holder, 'dose_reminder', md.id, public.wib(at)::date, 'kinfolk_dose_reminder',
      array[public.part_of_day(md.time_of_day), public.in_sentence(md.name), public.hm(md.time_of_day)]
    from public.medications md
    join public.duties d on d.circle_id = md.circle_id
    cross join lateral (select public.duty_holder(d.id, public.wib(at)::date) as holder) h
    join public.members m on m.circle_id = md.circle_id and m.user_id = h.holder and m.left_at is null and m.role <> 'viewer'
    where md.active
      and public.wib(at)::time - md.time_of_day >= interval '15 minutes' and public.wib(at)::time - md.time_of_day < interval '1 hour'
      and not exists (select 1 from public.dose_logs l where l.medication_id = md.id and l.day = public.wib(at)::date)
      and public.member_sees(h.holder, md.recipient_id, 'medications')
  on conflict do nothing;
$$;

-- A "1" from [phone]: their newest open dose reminder in the last 24 hours becomes the day's Dose Log, given by them.
-- Returns the free-form reply, or null when they were never reminded.
create function public.whatsapp_given(phone text) returns text
language plpgsql security definer set search_path = '' as $$
declare
  member uuid;
  m public.messages;
  md public.medications;
  given timestamptz;
begin
  select u.id into member from auth.users u where u.phone = regexp_replace(whatsapp_given.phone, '\D', '', 'g');
  select * into m from public.messages x
    where x.user_id = member and x.kind = 'dose_reminder' and x.sent_at is not null
    order by x.answered_at is null desc, x.created_at desc, x.id limit 1 for update;
  if not found then return null; end if;
  select * into md from public.medications x where x.id = m.ref and x.active;
  if m.answered_at is not null or m.sent_at < now() - interval '24 hours' or not found
    or not exists (select 1 from public.members x where x.circle_id = md.circle_id and x.user_id = member and x.left_at is null and x.role <> 'viewer')
    or not public.member_sees(member, md.recipient_id, 'medications') then
    return 'Permintaan ini sudah tidak berlaku.';
  end if;
  -- Every holder's reminder for this dose is answered, whoever replies first.
  update public.messages set answered_at = now() where kind = 'dose_reminder' and ref = m.ref and day = m.day and answered_at is null;
  insert into public.dose_logs (circle_id, medication_id, day, given_by)
    values (md.circle_id, md.id, m.day, member) on conflict do nothing returning at into given;
  if given is null then return 'Permintaan ini sudah tidak berlaku.'; end if; -- marked in the app meanwhile
  return format('Tercatat: %s diberikan, %s. Lingkaran bisa melihatnya.', public.in_sentence(md.name), public.hm(public.wib(given)::time));
end $$;

revoke execute on function public.part_of_day(time), public.whatsapp_given(text) from public, anon, authenticated;
