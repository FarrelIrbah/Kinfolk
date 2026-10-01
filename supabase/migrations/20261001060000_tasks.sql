-- Task (#26, see CONTEXT.md): v3's `tasks`. One owner and a due date; it comes from a Next Step (written with the
-- Visit Note), a Medication's refill (added by the minute job 4 days before), or a Member ("+ Tambah tugas").

create table public.tasks (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  recipient_id uuid not null,
  text text not null check (trim(text) <> ''),
  owner_id uuid not null,
  due date not null,
  source text not null default 'added' check (source in ('step', 'refill', 'added')),
  appointment_id uuid references public.visit_notes on delete cascade, -- a Next Step: its Visit Note
  position int, -- its place among the Next Steps
  medication_id uuid references public.medications on delete cascade, -- a refill
  refill_on date,
  added_by uuid not null default auth.uid(), -- for a Next Step, the Attendee
  created_at timestamptz not null default now(),
  done boolean not null default false,
  done_by uuid,
  done_at timestamptz,
  reminded_at timestamptz, -- "Diingatkan": once per Task
  unique (medication_id, refill_on),
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, owner_id) references public.members (circle_id, user_id),
  foreign key (circle_id, added_by) references public.members (circle_id, user_id)
);
create index on public.tasks (circle_id);
create index on public.tasks (appointment_id);
alter table public.tasks enable row level security;
revoke insert, update, delete on public.tasks from authenticated;
-- Members add Tasks and change their text, owner, due date and done; Next Steps and refills come from the server.
grant insert (circle_id, recipient_id, text, owner_id, due), update (text, owner_id, due, done) on public.tasks to authenticated;

-- A Next Step follows its Visit Note, a refill its Medication (Data Category); what Members add, everyone sees.
create function public.member_sees_task(member uuid, t public.tasks) returns boolean
language sql stable security definer set search_path = '' as $$
  select case t.source
    when 'step' then public.member_sees(member, t.recipient_id, 'appointments') and public.member_sees(member, t.recipient_id, 'visit_notes')
    when 'refill' then public.member_sees(member, t.recipient_id, 'medications')
    else true end
$$;
revoke execute on function public.member_sees_task(uuid, public.tasks) from public, anon;
grant execute on function public.member_sees_task(uuid, public.tasks) to authenticated;

create policy "members read tasks" on public.tasks
  for select to authenticated using (public.is_member(circle_id) and public.member_sees_task(auth.uid(), tasks));
create policy "members add tasks" on public.tasks
  for insert to authenticated with check (
    public.is_writer(circle_id) and source = 'added' and added_by = auth.uid()
    and exists (select 1 from public.members m where m.circle_id = tasks.circle_id and m.user_id = owner_id and m.left_at is null));
create policy "members change tasks" on public.tasks
  for update to authenticated using (public.is_writer(circle_id) and public.member_sees_task(auth.uid(), tasks))
  with check (exists (select 1 from public.members m where m.circle_id = tasks.circle_id and m.user_id = owner_id and m.left_at is null));

-- Who ticked it, and when; unticking ("Urungkan") clears both.
create function public.stamp_task_done() returns trigger language plpgsql set search_path = '' as $$
begin
  new.done_by := case when new.done then auth.uid() end;
  new.done_at := case when new.done then now() end;
  return new;
end $$;
create trigger stamp_task_done before update of done on public.tasks
  for each row when (new.done is distinct from old.done) execute function public.stamp_task_done();

-- A Next Step's text lives on its Visit Note too (Timeline, WhatsApp): the Task form changes both.
create function public.sync_step_text() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.visit_notes set next_steps[new.position + 1] = new.text where appointment_id = new.appointment_id;
  return new;
end $$;
create trigger sync_step_text after update of text on public.tasks
  for each row when (new.source = 'step' and new.text is distinct from old.text) execute function public.sync_step_text();

-- Next Steps written before Tasks become theirs: the Attendee's, due a week after the visit, like a new one in the app.
insert into public.tasks (circle_id, recipient_id, text, owner_id, due, source, appointment_id, position, added_by)
  select n.circle_id, a.recipient_id, s.text, n.written_by, public.wib(a.starts_at)::date + 7, 'step', n.appointment_id, (s.i - 1)::int, n.written_by
  from public.visit_notes n
  join public.appointments a on a.id = n.appointment_id
  cross join lateral unnest(n.next_steps) with ordinality s(text, i);

-- The Tasks screen: with the Appointment a Next Step came from ("Dari kontrol neurologi").
create view public.task_list with (security_invoker = true) as
  select t.*, a.title as from_title from public.tasks t left join public.appointments a on a.id = t.appointment_id;

-- As in 20260930080000_timeline.sql, but every Next Step has an owner and a due date and is a Task:
-- [steps] is [{id, text, owner, due}], id null for a new one. A step left out is removed with its Task.
drop function public.save_visit_note(uuid, jsonb, text[], text);
create function public.save_visit_note(appointment uuid, answers jsonb, steps jsonb, notes text)
returns void language plpgsql security definer set search_path = '' as $$
declare circle uuid; recipient uuid; asked uuid[];
begin
  if not public.is_attendee(appointment) then raise exception 'only the attendee writes the visit note'; end if;
  select circle_id, recipient_id into circle, recipient from public.appointments where id = appointment;
  steps := coalesce(steps, '[]');
  -- ponytail: a Question added after the first save carries to the next visit instead of joining this note.
  select coalesce(array_agg(id), '{}') into asked from public.appointment_questions where appointment_id = appointment;
  insert into public.visit_notes (appointment_id, circle_id, next_steps, notes, written_by)
    values (appointment, circle, array(select s ->> 'text' from jsonb_array_elements(steps) s), coalesce(notes, ''), auth.uid())
    on conflict (appointment_id) do update set next_steps = excluded.next_steps, notes = excluded.notes, written_by = excluded.written_by, updated_at = now();
  insert into public.answers (appointment_id, circle_id, question_id, answer)
    select appointment, circle, q, coalesce(answers ->> q::text, '') from unnest(asked) q
    on conflict (appointment_id, question_id) do update set answer = excluded.answer;

  delete from public.tasks t where t.appointment_id = appointment
    and not exists (select 1 from jsonb_array_elements(steps) s where s ->> 'id' = t.id::text);
  insert into public.tasks (id, circle_id, recipient_id, text, owner_id, due, source, appointment_id, position, added_by)
    select coalesce((s ->> 'id')::uuid, gen_random_uuid()), circle, recipient, s ->> 'text', (s ->> 'owner')::uuid,
      (s ->> 'due')::date, 'step', appointment, (i - 1)::int, auth.uid()
    from jsonb_array_elements(steps) with ordinality x(s, i)
    on conflict (id) do update set text = excluded.text, owner_id = excluded.owner_id, due = excluded.due, position = excluded.position
      where tasks.appointment_id = excluded.appointment_id; -- never another Visit Note's Task
end $$;
revoke execute on function public.save_visit_note(uuid, jsonb, jsonb, text) from public, anon;
grant execute on function public.save_visit_note(uuid, jsonb, jsonb, text) to authenticated;

-- "Ingatkan": WhatsApp to the owner, once per Task, by anyone else who sees it. Approved by the owner in #26:
-- "Sri mengingatkan: Perpanjang izin parkir disabilitas, tenggat 6 Okt."
create function public.remind_task(task uuid) returns void language plpgsql security definer set search_path = '' as $$
declare t public.tasks;
begin
  select * into t from public.tasks x where x.id = task for update;
  if not found or not public.is_writer(t.circle_id) or not public.member_sees_task(auth.uid(), t) then raise exception 'no such task'; end if;
  if t.owner_id = auth.uid() or t.done or t.reminded_at is not null then raise exception 'nothing to remind'; end if;
  update public.tasks set reminded_at = now() where id = task;
  if exists (select 1 from public.members m where m.circle_id = t.circle_id and m.user_id = t.owner_id and m.left_at is null)
    and public.member_sees_task(t.owner_id, t) then
    insert into public.messages (user_id, kind, ref, from_id, template, params)
      values (t.owner_id, 'task_reminder', t.id, auth.uid(), 'kinfolk_task_reminder',
        array[public.member_name(t.circle_id, auth.uid()), public.one_line(t.text), public.day_month(t.due)]);
  end if;
end $$;
revoke execute on function public.remind_task(uuid) from public, anon;
grant execute on function public.remind_task(uuid) to authenticated;

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
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- As in 20261001040000_daily_duties.sql, plus refill Tasks: 4 days before an active Medication's refill date, for
-- whoever refills it ("Ambil isi ulang omeprazole", v3). One per refill date; none while nobody refills it.
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
$$;

revoke execute on function public.stamp_task_done(), public.sync_step_text() from public, anon, authenticated;
