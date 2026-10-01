-- Timeline of every kind (#19): besides Appointments and Visit Notes, events written as they happen, each with
-- its v3 kind (Rota first; Medicine, Check-in, Document come with their tickets). An event's text is fixed when
-- it happens, and it is read only by those who may read its target.

create table public.timeline_events (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  kind text not null, -- drive_confirmed
  by uuid not null,
  at timestamptz not null default now(),
  text text not null,
  -- The target, opened on tap. ponytail: Appointments only; a later target makes this nullable, adds its own
  -- column, and a clause to the policy and the view below.
  appointment_id uuid not null references public.appointments on delete cascade,
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.timeline_events (circle_id, at);
alter table public.timeline_events enable row level security;
revoke insert, update, delete on public.timeline_events from authenticated;
-- The subquery on appointments is filtered by its own policy, so Data Categories follow the target.
create policy "members read events" on public.timeline_events for select to authenticated using (
  public.is_member(circle_id) and exists (select 1 from public.appointments a where a.id = appointment_id));

-- The Driver's YA on WhatsApp: "Konfirmasi via WhatsApp: mengantar ke kontrol neurologi jam 13.45."
create function public.log_drive_confirmed() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.timeline_events (circle_id, kind, by, at, appointment_id, text)
    values (new.circle_id, 'drive_confirmed', new.driver_id, new.driver_confirmed_at, new.id,
      'Konfirmasi via WhatsApp: mengantar ke ' || public.in_sentence(new.title) || ' jam '
        || public.hm(public.wib(coalesce(new.departs_at, new.starts_at))::time) || '.');
  return new;
end $$;
create trigger log_drive_confirmed after update of driver_confirmed_at on public.appointments
  for each row when (new.driver_confirmed_at is not null and old.driver_confirmed_at is null)
  execute function public.log_drive_confirmed();
revoke execute on function public.log_drive_confirmed() from public, anon, authenticated;

-- Events join the view with their text, and their Appointment's title, provider and start.
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
  where a.cancelled_at is null;
