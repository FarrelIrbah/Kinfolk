-- Timeline (see CONTEXT.md): Appointments and Visit Notes, newest first, each with who and when.

-- Who scheduled an Appointment. Earlier ones go to an admin of the Care Circle, the longest-standing current one.
alter table public.appointments add column created_by uuid default auth.uid();
update public.appointments a set created_by = (
  select user_id from public.members m where m.circle_id = a.circle_id
  order by m.left_at is not null, m.role <> 'admin', m.created_at limit 1);
alter table public.appointments alter column created_by set not null,
  add foreign key (circle_id, created_by) references public.members (circle_id, user_id);

alter policy "members schedule appointments" on public.appointments
  with check (public.is_writer(circle_id) and created_by = auth.uid());

-- Editing never changes who scheduled it.
create function public.keep_created_by() returns trigger language plpgsql set search_path = '' as $$
begin
  new.created_by := old.created_by;
  return new;
end $$;
create trigger keep_created_by before update on public.appointments
  for each row execute function public.keep_created_by();

-- by_name stays on entries by a Former Member. Cancelled Appointments drop out, with their Visit Notes.
create view public.timeline with (security_invoker = true) as
  select 'appointment' as kind, a.id as appointment_id, a.circle_id, a.created_by as by, m.name as by_name,
    a.created_at as at, a.title, p.name as provider, a.starts_at, '{}'::text[] as next_steps, '' as notes
  from public.appointments a
  join public.providers p on p.id = a.provider_id
  left join public.members m on m.circle_id = a.circle_id and m.user_id = a.created_by
  where a.cancelled_at is null
union all
  select 'visit_note', a.id, a.circle_id, n.written_by, m.name, n.updated_at, a.title, p.name, a.starts_at,
    n.next_steps, n.notes
  from public.visit_notes n
  join public.appointments a on a.id = n.appointment_id
  join public.providers p on p.id = a.provider_id
  left join public.members m on m.circle_id = n.circle_id and m.user_id = n.written_by
  where a.cancelled_at is null;

-- The Timeline shows who saved a Visit Note last, so saving again (say, by a new Attendee) takes it over.
create or replace function public.save_visit_note(appointment uuid, answers jsonb, next_steps text[], notes text)
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
    on conflict (appointment_id) do update set next_steps = excluded.next_steps, notes = excluded.notes, written_by = excluded.written_by, updated_at = now();
  insert into public.answers (appointment_id, circle_id, question_id, answer)
    select appointment, circle, q, coalesce(answers ->> q::text, '') from unnest(asked) q
    on conflict (appointment_id, question_id) do update set answer = excluded.answer;
end $$;
