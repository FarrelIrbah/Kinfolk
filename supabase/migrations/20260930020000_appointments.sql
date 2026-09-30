-- Provider and Appointment (see CONTEXT.md). A Provider is the doctor, not the place: the location lives on
-- each Appointment, so a Provider's history follows them across hospitals.

create table public.providers (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  name text not null, -- "Dr. Anand Rao"
  created_at timestamptz not null default now(),
  unique (circle_id, id)
);

alter table public.care_recipients add unique (circle_id, id);

-- Composite keys keep the Care Recipient, Provider, Driver and Attendee inside the Appointment's Care Circle.
-- One column each for Driver and Attendee: at most one of each, and they may be the same Member.
create table public.appointments (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  recipient_id uuid not null,
  provider_id uuid not null,
  title text not null, -- "Kontrol neurologi"
  location text, -- "Peninsula Neurology, Suite 204"
  starts_at timestamptz not null,
  departs_at timestamptz, -- "berangkat 13.45"
  driver_id uuid,
  attendee_id uuid,
  bring text not null default '', -- "Bawa"
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, provider_id) references public.providers (circle_id, id) on delete cascade,
  -- ponytail: removing a Member who is Driver or Attendee is refused until Former Member (#5) decides what stays.
  foreign key (circle_id, driver_id) references public.members (circle_id, user_id),
  foreign key (circle_id, attendee_id) references public.members (circle_id, user_id)
);
create index on public.appointments (circle_id, starts_at);
create index on public.appointments (provider_id, starts_at);
create index on public.providers (circle_id);

alter table public.providers enable row level security;
alter table public.appointments enable row level security;

create policy "members read providers" on public.providers
  for select to authenticated using (public.is_member(circle_id));
create policy "members add providers" on public.providers
  for insert to authenticated with check (public.is_member(circle_id));

create policy "members read appointments" on public.appointments
  for select to authenticated using (public.is_member(circle_id));
create policy "members schedule appointments" on public.appointments
  for insert to authenticated with check (public.is_member(circle_id));
create policy "members edit appointments" on public.appointments
  for update to authenticated using (public.is_member(circle_id)) with check (public.is_member(circle_id));
