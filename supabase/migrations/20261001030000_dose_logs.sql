-- Dose Log and medicine details (#21, see CONTEXT.md). `records` marks a Medication given for the day ("Tandai");
-- Home's ring counts today's; each one is a Medicine entry on the Timeline.

alter table public.medications
  add column note text not null default '', -- "Jangan berhenti tanpa Dr. Rao."
  add column blood_thinner boolean not null default false,
  add column refill_on date,
  add column refill_by uuid,
  add foreign key (circle_id, refill_by) references public.members (circle_id, user_id);

-- One per Medication per day; [day] is the giver's calendar day. Untoggling deletes it.
create table public.dose_logs (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  medication_id uuid not null references public.medications on delete cascade,
  day date not null,
  given_by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  unique (medication_id, day),
  foreign key (circle_id, given_by) references public.members (circle_id, user_id)
);
create index on public.dose_logs (circle_id, day);
alter table public.dose_logs enable row level security;

-- Whoever sees the Medication sees who gave it; any writer who sees it marks or unmarks it.
create function public.sees_medication(medication uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.medications m
    where m.id = medication and public.is_member(m.circle_id) and public.sees(m.recipient_id, 'medications'))
$$;
revoke execute on function public.sees_medication(uuid) from public, anon;
grant execute on function public.sees_medication(uuid) to authenticated;

create policy "members read dose logs" on public.dose_logs
  for select to authenticated using (public.sees_medication(medication_id));
create policy "members give doses" on public.dose_logs
  for insert to authenticated with check (
    public.is_writer(circle_id) and public.sees_medication(medication_id) and given_by = auth.uid()
    and exists (select 1 from public.medications m where m.id = medication_id and m.circle_id = dose_logs.circle_id));
create policy "members take back doses" on public.dose_logs
  for delete to authenticated using (public.is_writer(circle_id) and public.sees_medication(medication_id));

-- Given doses join the Timeline as Medicine entries, "Clopidogrel 75 mg diberikan." (owner-approved in #21).
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
  left join public.members m on m.circle_id = d.circle_id and m.user_id = d.given_by;
