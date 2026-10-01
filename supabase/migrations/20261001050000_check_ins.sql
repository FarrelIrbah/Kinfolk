-- Check-in (#24, see CONTEXT.md): v3's `checkin`, logged by tonight's Duty holder after the evening call. One per
-- Care Recipient per day, readable by every Member (no Data Category). A reading of 140 or more tells the others.

create table public.check_ins (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  recipient_id uuid not null references public.care_recipients on delete cascade,
  day date not null, -- the logger's calendar day
  by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  sys smallint not null check (sys >= 70), -- v3: below 70/40 is "Isi tekanan darah."
  dia smallint not null check (dia >= 40),
  ate text not null check (ate in ('yes', 'some', 'no')),
  walked boolean not null,
  mood text not null check (mood in ('good', 'okay', 'low')),
  note text not null default '',
  alerted boolean not null default false, -- the others were told of 140 or more today (set by tell_high_bp)
  unique (recipient_id, day),
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.check_ins (circle_id, day);
alter table public.check_ins enable row level security;

create policy "members read check-ins" on public.check_ins
  for select to authenticated using (public.is_member(circle_id));
create policy "members log check-ins" on public.check_ins
  for insert to authenticated with check (
    public.is_writer(circle_id) and by = auth.uid()
    and exists (select 1 from public.care_recipients r where r.id = recipient_id and r.circle_id = check_ins.circle_id));
-- "Ubah": whoever changes it last is who logged it. It stays that Care Recipient's, that day.
create policy "members change check-ins" on public.check_ins
  for update to authenticated using (public.is_writer(circle_id)) with check (public.is_writer(circle_id) and by = auth.uid());

create function public.stamp_check_in() returns trigger language plpgsql set search_path = '' as $$
begin
  new.circle_id := old.circle_id;
  new.recipient_id := old.recipient_id;
  new.day := old.day;
  new."by" := auth.uid();
  new.at := now();
  return new;
end $$;
create trigger stamp_check_in before update on public.check_ins for each row execute function public.stamp_check_in();

-- 140 or more (v3 looks at the systolic only) tells every other Member but the Care Recipient, once per Check-in: an "Ubah" never tells them
-- again, even after going below 140 and back. "Tensi Tukiman malam ini 152/90, 140 ke atas. Dicatat oleh Sri."
-- (owner-approved in #24)
create function public.flag_high_bp() returns trigger language plpgsql set search_path = '' as $$
begin
  new.alerted := (tg_op = 'UPDATE' and old.alerted) or new.sys >= 140;
  return new;
end $$;
create trigger flag_high_bp before insert or update on public.check_ins for each row execute function public.flag_high_bp();

-- After, since an upsert that conflicts runs the before-insert triggers too.
create function public.tell_high_bp() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.messages (user_id, kind, ref, template, params)
    select m.user_id, 'bp_high', new.id, 'kinfolk_bp_high', array[r.name, new.sys || '/' || new.dia, public.member_name(new.circle_id, new."by")]
    from public.care_recipients r
    join public.members m on m.circle_id = new.circle_id and m.left_at is null and m.user_id <> new."by"
      and m.user_id is distinct from r.member_id -- not the Care Recipient (owner, #24)
    where r.id = new.recipient_id;
  return new;
end $$;
create trigger tell_high_bp_insert after insert on public.check_ins
  for each row when (new.alerted) execute function public.tell_high_bp();
create trigger tell_high_bp_update after update on public.check_ins
  for each row when (new.alerted and not old.alerted) execute function public.tell_high_bp();

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
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- "Telepon Tukiman" on the evening card: their sign-in number while they are a Member, for any Member to call.
create function public.recipient_phone(recipient uuid) returns text
language sql stable security definer set search_path = '' as $$
  select '+' || u.phone from public.care_recipients r
  join public.members m on m.circle_id = r.circle_id and m.user_id = r.member_id and m.left_at is null
  join auth.users u on u.id = m.user_id
  where r.id = recipient and public.is_member(r.circle_id) and u.phone is not null and u.phone <> ''
$$;
revoke execute on function public.recipient_phone(uuid), public.tell_high_bp(), public.flag_high_bp(), public.stamp_check_in() from public, anon;
grant execute on function public.recipient_phone(uuid) to authenticated;

-- Check-ins join the Timeline as Cek entries, v3's text:
-- "Telepon malam: tensi 128/80, sudah makan malam, berjalan, suasana hati baik. Agak lelah"
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
  left join public.members m on m.circle_id = k.circle_id and m.user_id = k.by;
