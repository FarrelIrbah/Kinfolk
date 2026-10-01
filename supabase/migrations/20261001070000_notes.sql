-- Note (#27, see CONTEXT.md): v3's `notes`. A Member's free writing, shared with the Care Circle or only for its
-- author. A private Note is read by its author alone, admins included. No Data Category, not on the Timeline.

create table public.notes (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  text text not null check (trim(text) <> ''),
  private boolean not null default false,
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.notes (circle_id, at);
alter table public.notes enable row level security;

create policy "members read notes" on public.notes
  for select to authenticated using (public.is_member(circle_id) and (not private or by = auth.uid()));
create policy "members write notes" on public.notes
  for insert to authenticated with check (public.is_writer(circle_id) and by = auth.uid());
