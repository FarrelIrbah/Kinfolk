-- Medication and Care Contact (see CONTEXT.md).

-- One time of day per Medication orders Home's "next"; twice a day is two Medications (owner-approved in #11).
create table public.medications (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  recipient_id uuid not null,
  name text not null, -- "Clopidogrel"
  dose text not null default '', -- "75 mg"
  schedule text not null default '', -- "pagi, sesudah makan"
  time_of_day time not null, -- 07:00
  active boolean not null default true,
  created_at timestamptz not null default now(),
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade
);
create index on public.medications (circle_id, time_of_day);

-- "Medis" / "Rumah & tetangga" / "Darurat" groups from the prototype; the emergency flag is separate, so a doctor
-- in "Medis" can still be an emergency contact on Emergency Info.
create table public.care_contacts (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  name text not null,
  relationship text not null default '', -- "Dokter saraf"
  phone text not null, -- E.164, "+6281234567890"
  grp text not null check (grp in ('medical', 'home', 'emergency')),
  emergency boolean not null default false,
  created_at timestamptz not null default now()
);
create index on public.care_contacts (circle_id);

alter table public.medications enable row level security;
alter table public.care_contacts enable row level security;

create policy "members read medications" on public.medications
  for select to authenticated using (public.is_member(circle_id));
create policy "members add medications" on public.medications
  for insert to authenticated with check (public.is_member(circle_id));
create policy "members edit medications" on public.medications
  for update to authenticated using (public.is_member(circle_id)) with check (public.is_member(circle_id));

create policy "members read care contacts" on public.care_contacts
  for select to authenticated using (public.is_member(circle_id));
create policy "members add care contacts" on public.care_contacts
  for insert to authenticated with check (public.is_member(circle_id));
create policy "members edit care contacts" on public.care_contacts
  for update to authenticated using (public.is_member(circle_id)) with check (public.is_member(circle_id));
create policy "members remove care contacts" on public.care_contacts
  for delete to authenticated using (public.is_member(circle_id));
