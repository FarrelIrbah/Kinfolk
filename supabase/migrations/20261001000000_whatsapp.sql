-- WhatsApp notifications and replies (ADR 0002). Copy approved by the owner in #13 (docs/whatsapp-templates.md).
-- Everything to send goes into messages as a filled-in template; the whatsapp Edge Function sends it every minute,
-- queues the reminders that are due, and turns replies to an ask (a swap or a drive) into answers.

-- Set only by the Driver's YA on WhatsApp ("Dikonfirmasi via WhatsApp" on the rota).
alter table public.appointments add column driver_confirmed_at timestamptz;

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users on delete cascade,
  kind text not null, -- swap_ask, drive_ask (both answered YA/TIDAK), drive_reminder, duty_reminder, visit_note, *_yes, *_no
  ref uuid, -- the swap, Appointment or Duty
  from_id uuid, -- who asked
  day date, -- which day a reminder is for
  template text not null,
  params text[] not null,
  created_at timestamptz not null default now(),
  sent_at timestamptz,
  wa_id text, -- Meta's message id, to fall back to SMS when delivery fails later
  answered_at timestamptz -- also when the ask was answered in the app, or no longer stands
);
create index on public.messages (wa_id);
create index on public.messages (user_id, kind, sent_at);
create index on public.messages (sent_at) where sent_at is null;
-- A reminder goes once.
create unique index on public.messages (kind, ref, user_id, day) where kind in ('drive_reminder', 'duty_reminder');
alter table public.messages enable row level security; -- no policies: the service role only

-- Meta-approved bodies; the SMS fallback sends them filled in.
create function public.template_body(template text, params text[]) returns text
language plpgsql immutable set search_path = '' as $$
declare body text := case template
  when 'kinfolk_swap_ask' then '{{1}} bertanya: bisa ambil {{2}} minggu {{3}}? Balas YA atau TIDAK.'
  when 'kinfolk_swap_yes' then '{{1}} pegang {{2}} minggu ini.'
  when 'kinfolk_swap_no' then '{{1}} tidak bisa ambil {{2}} minggu ini.'
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

-- Date copy as in the app (When.kt), in WIB. ponytail: WIB for everyone, like this_week(); per-member zones if WITA/WIT families complain.
create function public.wib(at timestamptz) returns timestamp language sql stable set search_path = '' as $$
  select at at time zone 'Asia/Jakarta'
$$;
create function public.hm(t time) returns text language sql immutable set search_path = '' as $$ select to_char(t, 'HH24.MI') $$;
create function public.day_month(d date) returns text language sql immutable set search_path = '' as $$
  select extract(day from d) || ' ' || (array['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sept', 'Okt', 'Nov', 'Des'])[extract(month from d)]
$$;
-- "Kam, 1 Okt · 09.00"
create function public.when_label(at timestamptz) returns text language sql stable set search_path = '' as $$
  select (array['Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab', 'Min'])[extract(isodow from public.wib(at))] || ', '
    || public.day_month(public.wib(at)::date) || ' · ' || public.hm(public.wib(at)::time)
$$;
-- "telepon cek malam"
create function public.in_sentence(name text) returns text language sql immutable set search_path = '' as $$
  select lower(left(name, 1)) || substr(name, 2)
$$;
-- A template parameter can't hold line breaks or end the sentence the template ends.
create function public.one_line(text text) returns text language sql immutable set search_path = '' as $$
  select rtrim(regexp_replace(trim(text), '\s+', ' ', 'g'), '.')
$$;

create function public.member_name(circle uuid, member uuid) returns text language sql stable set search_path = '' as $$
  select name from public.members where circle_id = circle and user_id = member
$$;

-- sees() for any Member, so notifications follow the same Data Category rules as reads.
create function public.member_sees(member uuid, recipient uuid, category public.data_category) returns boolean
language sql stable security definer set search_path = '' as $$
  select not exists (
    select 1 from public.hidden_categories h join public.care_recipients r on r.id = h.recipient_id
    where h.recipient_id = recipient and h.member_id = member and h.category = member_sees.category
      and (public.restrictions_owner(r.id) is not null or not exists (select 1 from public.members m
        where m.circle_id = r.circle_id and m.user_id = member and m.left_at is null and m.role = 'admin')))
$$;
create or replace function public.sees(recipient uuid, category public.data_category) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.member_sees(auth.uid(), recipient, category)
$$;

-- Swap: the asked Member gets YA/TIDAK buttons; the asker hears the answer, wherever it was given.
create function public.ask_swap_on_whatsapp() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.messages (user_id, kind, ref, from_id, template, params)
    select new.to_id, 'swap_ask', new.id, new.from_id, 'kinfolk_swap_ask', array[
      public.member_name(new.circle_id, new.from_id), public.in_sentence(d.name),
      public.day_month(new.week) || ' – ' || public.day_month(new.week + 6) || ', ' || public.hm(d.time_of_day)]
    from public.duties d where d.id = new.duty_id;
  return new;
end $$;
create trigger ask_swap_on_whatsapp after insert on public.duty_swaps
  for each row when (new.answered_at is null and new.from_id is not null) execute function public.ask_swap_on_whatsapp();

create function public.tell_swap_answer() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.messages set answered_at = now() where kind = 'swap_ask' and ref = new.id and answered_at is null;
  insert into public.messages (user_id, kind, ref, template, params)
    select new.from_id, case when new.accepted then 'swap_yes' else 'swap_no' end, new.id,
      case when new.accepted then 'kinfolk_swap_yes' else 'kinfolk_swap_no' end,
      array[public.member_name(new.circle_id, new.to_id), public.in_sentence(d.name)]
    from public.duties d where d.id = new.duty_id;
  return new;
end $$;
-- Only an answer: ask_swap also closes an earlier ask, by setting answered_at.
create trigger tell_swap_answer after update of answered_at on public.duty_swaps
  for each row when (new.from_id is not null and current_setting('kinfolk.answering', true) = 'swap')
  execute function public.tell_swap_answer();

-- answer_swap as any Member, so a WhatsApp reply shares it.
create function public.answer_swap_as(swap uuid, member uuid, accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare s public.duty_swaps;
begin
  select * into s from public.duty_swaps where id = swap and answered_at is null and to_id = member for update;
  if not found or not exists (select 1 from public.members where circle_id = s.circle_id and user_id = member and left_at is null) then
    raise exception 'no swap asked of you';
  end if;
  if public.duty_holder(s.duty_id, s.week) is distinct from s.from_id then raise exception 'swap lapsed'; end if;
  perform set_config('kinfolk.answering', 'swap', true);
  update public.duty_swaps set answered_at = now(), accepted = accept where id = swap;
  perform set_config('kinfolk.answering', '', true);
end $$;
create or replace function public.answer_swap(swap uuid, accept boolean) returns void
language sql security definer set search_path = '' as $$
  select public.answer_swap_as(swap, auth.uid(), accept)
$$;
revoke execute on function public.answer_swap_as(uuid, uuid, boolean) from public, anon, authenticated;

-- Drive: assigning someone else asks them; only their YA confirms, and changing the Driver starts over.
create function public.keep_driver_confirmed() returns trigger language plpgsql set search_path = '' as $$
begin
  if auth.uid() is not null then
    new.driver_confirmed_at := case when tg_op = 'UPDATE' and new.driver_id is not distinct from old.driver_id then old.driver_confirmed_at end;
  end if;
  return new;
end $$;
create trigger keep_driver_confirmed before insert or update on public.appointments
  for each row execute function public.keep_driver_confirmed();

create function public.ask_driver_on_whatsapp() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'UPDATE' and (new.driver_id is distinct from old.driver_id or new.cancelled_at is not null) then
    update public.messages set answered_at = now() where kind = 'drive_ask' and ref = new.id and answered_at is null;
  end if;
  if new.driver_id is null or new.driver_id = auth.uid() or auth.uid() is null or new.cancelled_at is not null
    or new.starts_at <= now() or (tg_op = 'UPDATE' and new.driver_id is not distinct from old.driver_id)
    or not public.member_sees(new.driver_id, new.recipient_id, 'appointments') then
    return new;
  end if;
  insert into public.messages (user_id, kind, ref, from_id, template, params)
    select new.driver_id, 'drive_ask', new.id, auth.uid(), 'kinfolk_drive_ask', array[
      public.member_name(new.circle_id, auth.uid()), r.name, new.title,
      public.when_label(new.starts_at) || coalesce(', berangkat ' || public.hm(public.wib(new.departs_at)::time), '')]
    from public.care_recipients r where r.id = new.recipient_id;
  return new;
end $$;
create trigger ask_driver_on_whatsapp after insert or update of driver_id, cancelled_at on public.appointments
  for each row execute function public.ask_driver_on_whatsapp();

-- Visit Note: the first save tells every Member who may read it, but its author.
create function public.tell_visit_note() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.messages (user_id, kind, ref, template, params)
    select m.user_id, 'visit_note', new.appointment_id, 'kinfolk_visit_note', array[
      public.member_name(new.circle_id, new.written_by), r.name,
      -- The Timeline's text (#8).
      public.one_line(a.title || case
        when cardinality(new.next_steps) > 0 then ': ' || array_to_string(new.next_steps, ', ')
        when new.notes <> '' then ': ' || new.notes
        else '' end)]
    from public.appointments a
    join public.care_recipients r on r.id = a.recipient_id
    join public.members m on m.circle_id = a.circle_id and m.left_at is null and m.user_id <> new.written_by
    where a.id = new.appointment_id
      and public.member_sees(m.user_id, a.recipient_id, 'appointments') and public.member_sees(m.user_id, a.recipient_id, 'visit_notes');
  return new;
end $$;
create trigger tell_visit_note after insert on public.visit_notes for each row execute function public.tell_visit_note();

-- Reminders due [at]: the Driver 2 hours before leaving (or the start), the Duty holder an hour before, every day.
-- ponytail: a Duty before 01.00 or a drive before 02.00 misses its reminder (the window crosses midnight).
create function public.queue_reminders(at timestamptz) returns void language sql security definer set search_path = '' as $$
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
    cross join lateral (select public.duty_holder(d.id, date_trunc('week', public.wib(at))::date) as holder) h
    where h.holder is not null
      and public.wib(at)::time >= d.time_of_day - interval '1 hour' and public.wib(at)::time < d.time_of_day
  on conflict do nothing;
$$;

-- Takes everything waiting to be sent. ponytail: marked sent before sending, so a failed send is not retried.
create function public.claim_messages()
returns table (id uuid, phone text, template text, params text[], body text, answerable boolean)
language sql security definer set search_path = '' as $$
  update public.messages m set sent_at = now()
  where m.id in (select x.id from public.messages x where x.sent_at is null for update skip locked)
  returning m.id, (select u.phone from auth.users u where u.id = m.user_id), m.template, m.params,
    public.template_body(m.template, m.params), m.kind in ('swap_ask', 'drive_ask')
$$;

create function public.sent_on_whatsapp(message uuid, wa_id text) returns void language sql security definer set search_path = '' as $$
  update public.messages set wa_id = sent_on_whatsapp.wa_id where id = message
$$;

-- Meta accepts a message to a number without WhatsApp and reports the failure later: its SMS instead.
create function public.whatsapp_failed(wa_id text) returns table (phone text, body text)
language sql security definer set search_path = '' as $$
  update public.messages m set wa_id = null where m.wa_id = whatsapp_failed.wa_id
  returning (select u.phone from auth.users u where u.id = m.user_id), public.template_body(m.template, m.params)
$$;

-- A YA or TIDAK from [phone], to the ask [message] (a button) or else their latest ask. Returns the free-form reply,
-- or null when there's nothing to answer.
create function public.whatsapp_reply(phone text, message uuid, yes boolean) returns text
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
    select public.member_name(s.circle_id, member), public.in_sentence(d.name), public.member_name(s.circle_id, s.from_id)
      into who, what, asker
      from public.duty_swaps s join public.duties d on d.id = s.duty_id where s.id = m.ref;
    update public.messages set answered_at = now() where id = m.id;
    return case when yes then format('Terima kasih, %s. Anda pegang %s minggu ini. %s sudah diberi tahu.', who, what, asker)
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

revoke execute on function public.template_body(text, text[]), public.member_sees(uuid, uuid, public.data_category),
  public.queue_reminders(timestamptz), public.claim_messages(), public.whatsapp_reply(text, uuid, boolean),
  public.sent_on_whatsapp(uuid, text), public.whatsapp_failed(text),
  public.ask_swap_on_whatsapp(), public.tell_swap_answer(), public.keep_driver_confirmed(), public.ask_driver_on_whatsapp(),
  public.tell_visit_note() from public, anon, authenticated;
