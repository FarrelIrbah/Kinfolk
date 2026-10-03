-- Mode Bapak (#37): v3's `bapak` on any Member's phone, handed to the Care Recipient. "Saya baik" and "Butuh bantuan"
-- are written as the Care Recipient: the phone's Member stays as `by` (whose phone), the Timeline shows the Care
-- Recipient (owner-approved in #37). Both text every Member but the Care Recipient on WhatsApp. Restrictions keep
-- set_hidden's rules (ADR 0004): Mode Bapak on someone else's phone grants nothing.

create table public.recipient_presses (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  recipient_id uuid not null,
  kind text not null check (kind in ('fine', 'help')),
  by uuid not null,
  at timestamptz not null default now(),
  text text not null, -- the Timeline's, fixed when it happens
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.recipient_presses (circle_id, at);
alter table public.recipient_presses enable row level security;
revoke insert, update, delete on public.recipient_presses from authenticated;
create policy "members read presses" on public.recipient_presses
  for select to authenticated using (public.is_member(circle_id));

-- v3's lists: "Sri", "Sri dan Budi", "Sri, Budi, dan Dewi" (names() in the app).
create function public.and_names(names text[]) returns text language sql immutable set search_path = '' as $$
  select case
    when coalesce(array_length(names, 1), 0) <= 1 then coalesce(names[1], '')
    when array_length(names, 1) = 2 then names[1] || ' dan ' || names[2]
    else array_to_string(names[1:array_length(names, 1) - 1], ', ') || ', dan ' || names[array_length(names, 1)]
  end
$$;

-- The organizer: the first admin to join, never the Care Recipient.
create function public.organizer(recipient uuid) returns uuid language sql stable security definer set search_path = '' as $$
  select m.user_id from public.care_recipients r
  join public.members m on m.circle_id = r.circle_id and m.left_at is null and m.role = 'admin'
    and m.user_id is distinct from r.member_id
  where r.id = recipient order by m.created_at limit 1
$$;

-- Records a press by the signed-in Member and texts every Member but the Care Recipient; returns how many.
create function public.press(recipient uuid, press_kind text, template text, params text[], text text) returns int
language plpgsql security definer set search_path = '' as $$
declare r public.care_recipients; n int;
begin
  select * into r from public.care_recipients where id = recipient;
  if r.id is null or not public.is_writer(r.circle_id) then raise exception 'not a member'; end if;
  insert into public.recipient_presses (circle_id, recipient_id, kind, by, text)
    values (r.circle_id, r.id, press_kind, auth.uid(), press.text);
  insert into public.messages (user_id, kind, ref, from_id, template, params)
    select m.user_id, 'recipient_' || press_kind, r.id, auth.uid(), press.template, press.params
    from public.members m
    where m.circle_id = r.circle_id and m.left_at is null and m.user_id is distinct from r.member_id;
  get diagnostics n = row_count;
  return n;
end $$;

-- "Saya baik": "Tukiman baik-baik saja. Dikirim dari Mode Tukiman." Returns how many children were texted
-- ("Terkirim ke 5 anak").
create function public.say_fine(recipient uuid) returns int language plpgsql security definer set search_path = '' as $$
declare name text := (select r.name from public.care_recipients r where r.id = recipient);
begin
  return public.press(recipient, 'fine', 'kinfolk_recipient_ok', array[name], 'Menekan "Saya baik" di Mode ' || name || '.');
end $$;

-- Who "Butuh bantuan" calls: the organizer, then the emergency contacts (#34) in join order.
create function public.helpers(recipient uuid) returns text language sql stable security definer set search_path = '' as $$
  select public.and_names(array_agg(m.name order by m.user_id = o.id desc, m.created_at))
  from public.care_recipients r
  cross join lateral (select public.organizer(recipient) as id) o
  join public.members m on m.circle_id = r.circle_id and m.left_at is null and m.user_id is distinct from r.member_id
  where r.id = recipient and (m.user_id = o.id or m.emergency)
$$;

-- "Butuh bantuan": "Tukiman butuh bantuan. Sri dan Budi sedang dihubungi." Returns "Sri dan Budi".
create function public.ask_help(recipient uuid) returns text language plpgsql security definer set search_path = '' as $$
declare name text := (select r.name from public.care_recipients r where r.id = recipient);
  who text := coalesce(public.helpers(recipient), '');
begin
  perform public.press(recipient, 'help', 'kinfolk_recipient_help', array[name, who], 'Menekan "Butuh bantuan". ' || who || ' ditelepon.');
  return who;
end $$;

-- "Telepon Sri" and the dialer after "Butuh bantuan": the organizer's sign-in number, for any Member.
create function public.organizer_phone(recipient uuid) returns text
language sql stable security definer set search_path = '' as $$
  select '+' || u.phone from public.care_recipients r join auth.users u on u.id = public.organizer(recipient)
  where r.id = recipient and public.is_member(r.circle_id) and u.phone is not null and u.phone <> ''
$$;

revoke execute on function public.organizer(uuid), public.press(uuid, text, text, text[], text), public.helpers(uuid),
  public.say_fine(uuid), public.ask_help(uuid), public.organizer_phone(uuid) from public, anon, authenticated;
grant execute on function public.say_fine(uuid), public.ask_help(uuid), public.organizer_phone(uuid) to authenticated;

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
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- Presses join the Timeline as Cek entries (v3 T1), named after the Care Recipient.
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
  join public.care_recipients r on r.id = p.recipient_id;
