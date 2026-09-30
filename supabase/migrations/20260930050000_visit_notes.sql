-- Question, Visit Note, Next Step and carry-over (see CONTEXT.md).
-- A Question is asked for one Appointment. Until it gets an answer it sits on the first Appointment with the same
-- Care Recipient and Provider, at or after the one it was asked for, that has no Visit Note yet: that is carry-over.

alter table public.appointments add unique (circle_id, id);

create table public.questions (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  appointment_id uuid not null, -- the Appointment it was asked for
  asked_by uuid not null default auth.uid(),
  text text not null,
  created_at timestamptz not null default now(),
  unique (circle_id, id),
  foreign key (circle_id, appointment_id) references public.appointments (circle_id, id) on delete cascade,
  -- ponytail: like Driver/Attendee, removing a Member who asked is refused until Former Member (#5).
  foreign key (circle_id, asked_by) references public.members (circle_id, user_id)
);
create index on public.questions (appointment_id);

-- One per Appointment, written by its Attendee.
create table public.visit_notes (
  appointment_id uuid primary key,
  circle_id uuid not null,
  next_steps text[] not null default '{}', -- "Langkah berikutnya"
  notes text not null default '', -- "Catatan"
  written_by uuid not null default auth.uid(),
  updated_at timestamptz not null default now(),
  foreign key (circle_id, appointment_id) references public.appointments (circle_id, id) on delete cascade
);

-- Every Question on a Visit Note, with the answer; '' means not answered at that visit.
create table public.answers (
  appointment_id uuid not null references public.visit_notes on delete cascade,
  circle_id uuid not null,
  question_id uuid not null,
  answer text not null default '',
  primary key (appointment_id, question_id),
  foreign key (circle_id, question_id) references public.questions (circle_id, id) on delete cascade
);
create index on public.answers (question_id);

create function public.is_attendee(appointment uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.appointments where id = appointment and attendee_id = auth.uid())
$$;
revoke execute on function public.is_attendee(uuid) from public, anon;
grant execute on function public.is_attendee(uuid) to authenticated;

alter table public.questions enable row level security;
alter table public.visit_notes enable row level security;
alter table public.answers enable row level security;

create policy "members read questions" on public.questions
  for select to authenticated using (public.is_member(circle_id));
create policy "members ask questions" on public.questions
  for insert to authenticated with check (public.is_member(circle_id) and asked_by = auth.uid());

create policy "members read visit notes" on public.visit_notes
  for select to authenticated using (public.is_member(circle_id));

create policy "members read answers" on public.answers
  for select to authenticated using (public.is_member(circle_id));
-- No write policies: Visit Notes and answers are written only through save_visit_note below.

-- The Questions on each Appointment: those on its Visit Note, or, before it has one, every open Question that
-- carries over to it. asked_in differs from appointment_id for a carried-over Question.
create view public.appointment_questions with (security_invoker = true) as
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
  left join public.members m on m.circle_id = q.circle_id and m.user_id = q.asked_by
  cross join lateral (
    select a.id from public.appointments a
    where a.recipient_id = o.recipient_id and a.provider_id = o.provider_id and a.cancelled_at is null
      and a.starts_at >= o.starts_at
      and not exists (select 1 from public.visit_notes n where n.appointment_id = a.id)
    order by a.starts_at, a.id <> o.id, a.id
    limit 1
  ) a
  where not exists (select 1 from public.answers x where x.question_id = q.id and x.answer <> '');

-- The only way to write a Visit Note: by the Attendee, answering exactly the Questions on that Appointment. Every
-- one of them gets a row, blank when [answers] leaves it out (say, a Question added while the form was open), so
-- it shows as not answered and carries over.
create function public.save_visit_note(appointment uuid, answers jsonb, next_steps text[], notes text)
returns void language plpgsql security definer set search_path = '' as $$
declare circle uuid; asked uuid[];
begin
  if not public.is_attendee(appointment) then raise exception 'only the attendee writes the visit note'; end if;
  select circle_id into circle from public.appointments where id = appointment;
  -- Before the note exists this is every open Question carried to it; after, the ones already on the note.
  -- ponytail: a Question added after the first save carries to the next visit instead of joining this note.
  select coalesce(array_agg(id), '{}') into asked from public.appointment_questions where appointment_id = appointment;
  insert into public.visit_notes (appointment_id, circle_id, next_steps, notes, written_by)
    values (appointment, circle, coalesce(next_steps, '{}'), coalesce(notes, ''), auth.uid())
    on conflict (appointment_id) do update set next_steps = excluded.next_steps, notes = excluded.notes, updated_at = now();
  insert into public.answers (appointment_id, circle_id, question_id, answer)
    select appointment, circle, q, coalesce(answers ->> q::text, '') from unnest(asked) q
    on conflict (appointment_id, question_id) do update set answer = excluded.answer;
end $$;
revoke execute on function public.save_visit_note(uuid, jsonb, text[], text) from public, anon;
grant execute on function public.save_visit_note(uuid, jsonb, text[], text) to authenticated;
