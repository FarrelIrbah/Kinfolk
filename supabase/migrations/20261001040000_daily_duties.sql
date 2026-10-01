-- Duties pass to the next Member in their rotation every day, and a swap moves one day (#23, reverting #10's weekly model).
-- duty_turn: the rotation moves one place a day from starts_on; duty_holder: then the last accepted swap of that day.

-- Before the functions change: who holds this week, and any later week someone took, to keep for each of its days.
create temporary table kept_turns as
  select d.id as duty_id, d.circle_id, w.week + g.i as day, public.duty_holder(d.id, w.week) as holder,
    public.duty_turn(d.id, public.this_week()) as this_week_turn
  from public.duties d
  cross join lateral (select public.this_week() as week
    union select s.week from public.duty_swaps s where s.duty_id = d.id and s.accepted and s.week > public.this_week()) w
  cross join generate_series(0, 6) g(i);

-- ponytail: asks still waiting were for a whole week; they lapse rather than become seven.
update public.messages set answered_at = now() where kind = 'swap_ask' and answered_at is null;
delete from public.duty_swaps;

alter table public.duties drop constraint duties_starts_on_check;
comment on column public.duties.starts_on is 'the day the first in the rotation holds it';
alter table public.duty_swaps drop constraint duty_swaps_week_check;
alter table public.duty_swaps rename column week to day;

drop function public.duty_week(uuid, date), public.save_duty(uuid, uuid, text, time, uuid[], date),
  public.ask_swap(uuid, date, uuid), public.take_turn(uuid, date), public.my_invitations(),
  public.duty_turn(uuid, date), public.duty_holder(uuid, date), public.anchor_duty(uuid, uuid, date);

-- ponytail: "today" on the server is Jakarta's (WIB), like this_week(); the app passes the device's own day elsewhere.
create function public.wib_today() returns date language sql stable set search_path = '' as $$
  select (now() at time zone 'Asia/Jakarta')::date
$$;

-- "Minggu"
create function public.day_name(d date) returns text language sql immutable set search_path = '' as $$
  select (array['Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu', 'Minggu'])[extract(isodow from d)]
$$;

-- The rotation moves one place a day from starts_on, skipping Former Members; swaps aside.
create function public.duty_turn(duty uuid, on_day date) returns uuid
language sql stable set search_path = '' as $$
  select o.user_id from (
      select r.user_id, m.left_at, row_number() over (order by r.pos) - 1 as i, count(*) over () as n
      from public.duty_rotation r join public.members m on m.circle_id = r.circle_id and m.user_id = r.user_id
      where r.duty_id = duty) o
    cross join (select on_day - starts_on as k from public.duties where id = duty) d
    where o.left_at is null
    order by ((o.i - d.k) % o.n + o.n) % o.n limit 1
$$;

-- The last accepted swap of the day overrides the rotation.
create function public.duty_holder(duty uuid, on_day date) returns uuid
language sql stable set search_path = '' as $$
  select coalesce(
    (select s.to_id from public.duty_swaps s
      join public.members m on m.circle_id = s.circle_id and m.user_id = s.to_id and m.left_at is null
      where s.duty_id = duty and s.day = on_day and s.accepted
      order by s.answered_at desc limit 1),
    public.duty_turn(duty, on_day))
$$;

-- After the rotation changes, whoever it gave on_day keeps it and the others follow in the new order.
create function public.anchor_duty(duty uuid, keep uuid, on_day date) returns void
language sql set search_path = '' as $$
  update public.duties set starts_on = on_day - coalesce((
    select o.i::int from (select r.user_id, row_number() over (order by r.pos) - 1 as i
      from public.duty_rotation r where r.duty_id = anchor_duty.duty) o
    where o.user_id = keep), 0)
  where id = duty
$$;

-- A swap waiting on day for an answer, unless it has lapsed: asked by someone who no longer holds the day, or of a Former Member.
create function public.open_swap(duty uuid, on_day date) returns public.duty_swaps
language sql stable set search_path = '' as $$
  select s.* from public.duty_swaps s
    join public.members m on m.circle_id = s.circle_id and m.user_id = s.to_id and m.left_at is null
    where s.duty_id = duty and s.day = on_day and s.answered_at is null and s.from_id = public.duty_holder(duty, on_day)
    order by s.asked_at desc limit 1
$$;

-- Each Duty on each day of the week of on_week (a Monday).
create function public.duty_week(circle uuid, on_week date)
returns table (duty_id uuid, name text, time_of_day time, day date, holder uuid, rotation uuid[], swap_id uuid, swap_to uuid)
language sql stable set search_path = '' as $$
  select d.id, d.name, d.time_of_day, x.day, public.duty_holder(d.id, x.day),
    array(select r.user_id from public.duty_rotation r
      join public.members m on m.circle_id = r.circle_id and m.user_id = r.user_id and m.left_at is null
      where r.duty_id = d.id order by r.pos),
    s.id, s.to_id
  from public.duties d
  cross join lateral (select on_week + g.i as day from generate_series(0, 6) g(i)) x
  left join lateral public.open_swap(d.id, x.day) s on true
  where d.circle_id = circle
  order by d.created_at, x.day
$$;

create function public.save_duty(circle uuid, duty uuid, duty_name text, at_time time, rotation uuid[], on_day date)
returns uuid language plpgsql security definer set search_path = '' as $$
declare keep uuid;
begin
  if not public.is_admin(circle) then raise exception 'only an admin can change duties'; end if;
  if exists (select 1 from unnest(rotation) u where not exists (
      select 1 from public.members m where m.circle_id = circle and m.user_id = u and m.left_at is null)) then
    raise exception 'not a member';
  end if;
  if duty is null then
    insert into public.duties (circle_id, name, time_of_day, starts_on) values (circle, duty_name, at_time, on_day)
      returning id into duty;
  else
    keep := public.duty_turn(duty, on_day);
    update public.duties set name = duty_name, time_of_day = at_time where id = duty and circle_id = circle;
    if not found then raise exception 'no such duty'; end if;
    delete from public.duty_rotation where duty_id = duty;
  end if;
  insert into public.duty_rotation (duty_id, circle_id, user_id, pos)
    select duty, circle, u, o from unnest(rotation) with ordinality x(u, o);
  -- Editing keeps today's holder, if still in the rotation; else the first starts.
  perform public.anchor_duty(duty, keep, on_day);
  return duty;
end $$;

-- The holder asks another Member; asking again replaces the last ask for that day.
create function public.ask_swap(duty uuid, on_day date, member uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare circle uuid; swap uuid;
begin
  select circle_id into circle from public.duties where id = duty;
  if circle is null or not public.is_member(circle) then raise exception 'no such duty'; end if;
  if public.duty_holder(duty, on_day) is distinct from auth.uid() then raise exception 'not your turn'; end if;
  if member = auth.uid() or not exists (
      select 1 from public.members where circle_id = circle and user_id = member and left_at is null) then
    raise exception 'not a member';
  end if;
  update public.duty_swaps set answered_at = now() where duty_id = duty and day = on_day and answered_at is null;
  insert into public.duty_swaps (duty_id, circle_id, day, from_id, to_id) values (duty, circle, on_day, auth.uid(), member)
    returning id into swap;
  return swap;
end $$;

-- Volunteering for someone else's day needs no one's agreement but the volunteer's (invitee "Saya ambil");
-- a swap asked for that day is settled.
create function public.take_turn(duty uuid, on_day date) returns void
language plpgsql security definer set search_path = '' as $$
declare circle uuid; holder uuid;
begin
  select circle_id into circle from public.duties where id = duty;
  if circle is null or not public.is_writer(circle) then raise exception 'no such duty'; end if;
  holder := public.duty_holder(duty, on_day);
  if holder is not distinct from auth.uid() then return; end if;
  update public.messages set answered_at = now() where kind = 'swap_ask' and answered_at is null
    and ref in (select id from public.duty_swaps where duty_id = duty and day = on_day and answered_at is null);
  update public.duty_swaps set answered_at = now() where duty_id = duty and day = on_day and answered_at is null;
  insert into public.duty_swaps (duty_id, circle_id, day, from_id, to_id, answered_at, accepted)
    values (duty, circle, on_day, holder, auth.uid(), now(), true);
end $$;

-- Members join every rotation at the end once they accept (or rejoin); whoever holds today keeps it.
create or replace function public.join_rotations() returns trigger language plpgsql security definer set search_path = '' as $$
declare d public.duties; keep uuid;
begin
  for d in select * from public.duties where circle_id = new.circle_id loop
    keep := public.duty_turn(d.id, public.wib_today());
    insert into public.duty_rotation (duty_id, circle_id, user_id, pos)
      values (d.id, d.circle_id, new.user_id, coalesce((select max(r.pos) from public.duty_rotation r where r.duty_id = d.id), 0) + 1)
      on conflict do nothing;
    perform public.anchor_duty(d.id, keep, public.wib_today());
  end loop;
  return new;
end $$;

-- invitee's "Satu hal kecil, jika bisa" (#23, approved by the owner): the first open day from today, a swap asked
-- and not yet answered, of any Duty.
create function public.my_invitations()
returns table (id uuid, name text, inviter text, circle text, duty_id uuid, duty text, duty_day date, duty_time time,
  hidden public.data_category[])
language sql stable security definer set search_path = '' as $$
  select i.id, i.name, m.name, c.name, o.duty_id, o.name, o.day, o.time_of_day,
    case when i.role in ('admin', 'parent') then '{}' else c.hide_by_default end
  from public.invitations i
  join public.care_circles c on c.id = i.circle_id
  left join public.members m on m.circle_id = i.circle_id and m.user_id = i.invited_by
  left join lateral (select s.duty_id, d.name, s.day, d.time_of_day from public.duty_swaps s
    join public.duties d on d.id = s.duty_id
    where s.circle_id = i.circle_id and i.role not in ('viewer', 'parent') and s.day >= public.wib_today()
      and s.id = (public.open_swap(s.duty_id, s.day)).id
    order by s.day, d.created_at limit 1) o on true
  where i.accepted_at is null and i.cancelled_at is null
    and regexp_replace(i.phone, '\D', '', 'g') = (select u.phone from auth.users u where u.id = auth.uid())
  order by i.created_at
$$;

-- WhatsApp (#13), per day: "Sri bertanya: bisa ambil telepon cek malam Tukiman Minggu 4 Okt, 19.00? Balas YA atau TIDAK."
-- as v3's sms `swap`; "Budi pegang telepon cek malam hari Minggu 4 Okt."
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
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- "Minggu 4 Okt"
create function public.day_label(d date) returns text language sql immutable set search_path = '' as $$
  select public.day_name(d) || ' ' || public.day_month(d)
$$;

create or replace function public.ask_swap_on_whatsapp() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.messages (user_id, kind, ref, from_id, template, params)
    select new.to_id, 'swap_ask', new.id, new.from_id, 'kinfolk_swap_ask', array[
      public.member_name(new.circle_id, new.from_id), public.in_sentence(d.name) || ' ' || c.name,
      public.day_label(new.day) || ', ' || public.hm(d.time_of_day)]
    from public.duties d join public.care_circles c on c.id = d.circle_id where d.id = new.duty_id;
  return new;
end $$;

create or replace function public.tell_swap_answer() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.messages set answered_at = now() where kind = 'swap_ask' and ref = new.id and answered_at is null;
  insert into public.messages (user_id, kind, ref, template, params)
    select new.from_id, case when new.accepted then 'swap_yes' else 'swap_no' end, new.id,
      case when new.accepted then 'kinfolk_swap_yes' else 'kinfolk_swap_no' end,
      array[public.member_name(new.circle_id, new.to_id), public.in_sentence(d.name), public.day_label(new.day)]
    from public.duties d where d.id = new.duty_id;
  return new;
end $$;

create or replace function public.answer_swap_as(swap uuid, member uuid, accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare s public.duty_swaps;
begin
  select * into s from public.duty_swaps where id = swap and answered_at is null and to_id = member for update;
  if not found or not exists (select 1 from public.members where circle_id = s.circle_id and user_id = member and left_at is null) then
    raise exception 'no swap asked of you';
  end if;
  if public.duty_holder(s.duty_id, s.day) is distinct from s.from_id then raise exception 'swap lapsed'; end if;
  perform set_config('kinfolk.answering', 'swap', true);
  update public.duty_swaps set answered_at = now(), accepted = accept where id = swap;
  perform set_config('kinfolk.answering', '', true);
end $$;

-- Reminders due [at]: the Driver 2 hours before leaving (or the start), the day's Duty holder an hour before.
-- ponytail: a Duty before 01.00 or a drive before 02.00 misses its reminder (the window crosses midnight).
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
$$;

-- As in 20261001000000_whatsapp.sql, with the swap answer per day.
create or replace function public.whatsapp_reply(phone text, message uuid, yes boolean) returns text
language plpgsql security definer set search_path = '' as $$
declare
  member uuid;
  m public.messages;
  a public.appointments;
  who text;
  what text;
  asker text;
  recipient text;
  expired constant text := 'Permintaan ini sudah tidak berlaku.';
begin
  select u.id into member from auth.users u where u.phone = regexp_replace(whatsapp_reply.phone, '\D', '', 'g');
  select * into m from public.messages x
    where x.user_id = member and x.kind in ('swap_ask', 'drive_ask') and x.sent_at is not null
      and (message is null or x.id = message)
    order by x.answered_at is null desc, x.created_at desc, x.id limit 1 for update; -- typed: the newest open one
  if not found then return null; end if;
  if m.answered_at is not null or m.sent_at < now() - interval '24 hours' then return expired; end if;

  if m.kind = 'swap_ask' then
    begin
      perform public.answer_swap_as(m.ref, member, yes);
    exception when others then return expired;
    end;
    -- "Terima kasih, Rina. Anda pegang telepon hari Minggu. Sri sudah diberi tahu." (v3 sms `yesR`)
    select public.member_name(s.circle_id, member), public.in_sentence(d.name) || ' hari ' || public.day_label(s.day),
        public.member_name(s.circle_id, s.from_id)
      into who, what, asker
      from public.duty_swaps s join public.duties d on d.id = s.duty_id where s.id = m.ref;
    update public.messages set answered_at = now() where id = m.id;
    return case when yes then format('Terima kasih, %s. Anda pegang %s. %s sudah diberi tahu.', who, what, asker)
      else format('Ditolak. %s akan bertanya ke yang lain.', asker) end;
  end if;

  select * into a from public.appointments x
    where x.id = m.ref and x.driver_id = member and x.cancelled_at is null and x.starts_at > now() for update;
  if not found then return expired; end if;
  if yes then
    update public.appointments set driver_confirmed_at = now() where id = a.id;
  else
    update public.appointments set driver_id = null where id = a.id;
  end if;
  update public.messages set answered_at = now() where id = m.id;
  who := public.member_name(a.circle_id, member);
  asker := public.member_name(a.circle_id, m.from_id);
  select r.name into recipient from public.care_recipients r where r.id = a.recipient_id;
  what := recipient || ' ' || public.when_label(a.starts_at);
  if exists (select 1 from public.members x where x.circle_id = a.circle_id and x.user_id = m.from_id and x.left_at is null)
    and public.member_sees(m.from_id, a.recipient_id, 'appointments') then
    insert into public.messages (user_id, kind, ref, template, params)
      values (m.from_id, case when yes then 'drive_yes' else 'drive_no' end, a.id,
        case when yes then 'kinfolk_drive_yes' else 'kinfolk_drive_no' end,
        array[who, recipient, public.when_label(a.starts_at)]);
  end if;
  return case when yes then format('Terima kasih, %s. Anda mengantar %s. %s sudah diberi tahu.', who, what, asker)
    else format('Tidak apa-apa. %s akan bertanya ke yang lain.', asker) end;
end $$;

-- Keep what kept_turns saw: this week's holders day by day (and the weeks someone took), then the rotation carries
-- on from the one after this week's turn next Monday.
select public.anchor_duty(k.duty_id, k.this_week_turn, public.this_week() + 6)
  from kept_turns k where k.day = public.this_week() + 6;
insert into public.duty_swaps (duty_id, circle_id, day, from_id, to_id, answered_at, accepted)
  select k.duty_id, k.circle_id, k.day, public.duty_holder(k.duty_id, k.day), k.holder, now(), true
  from kept_turns k where k.holder is not null and k.holder is distinct from public.duty_holder(k.duty_id, k.day);
drop table kept_turns;

revoke execute on function public.wib_today(), public.day_name(date), public.day_label(date), public.duty_turn(uuid, date),
  public.duty_holder(uuid, date), public.anchor_duty(uuid, uuid, date), public.open_swap(uuid, date), public.duty_week(uuid, date),
  public.save_duty(uuid, uuid, text, time, uuid[], date), public.ask_swap(uuid, date, uuid), public.take_turn(uuid, date),
  public.my_invitations() from public, anon;
grant execute on function public.duty_turn(uuid, date), public.duty_holder(uuid, date), public.open_swap(uuid, date), public.duty_week(uuid, date), public.save_duty(uuid, uuid, text, time, uuid[], date),
  public.ask_swap(uuid, date, uuid), public.take_turn(uuid, date), public.my_invitations() to authenticated;
