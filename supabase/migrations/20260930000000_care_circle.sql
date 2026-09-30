-- Care Circle, Member, Care Recipient (see CONTEXT.md).
-- All access rules live in RLS (ADR 0001): a Member sees only their own Care Circles.

create type public.role as enum ('admin', 'sibling', 'parent', 'viewer');

create table public.care_circles (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  needs text[] not null default '{}', -- onb1 "Apa yang paling sering perlu dikoordinasi?"
  created_at timestamptz not null default now()
);

create table public.members (
  circle_id uuid not null references public.care_circles on delete cascade,
  user_id uuid not null references auth.users on delete cascade,
  role public.role not null,
  created_at timestamptz not null default now(),
  primary key (circle_id, user_id)
);

create table public.care_recipients (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  name text not null,
  relation text, -- to the Member who added them: father, mother, grandparent, spouse
  created_at timestamptz not null default now()
);
create index on public.care_recipients (circle_id);
create index on public.members (user_id);

-- security definer so policies on members don't recurse into members.
create function public.is_member(circle uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.members where circle_id = circle and user_id = auth.uid())
$$;

alter table public.care_circles enable row level security;
alter table public.members enable row level security;
alter table public.care_recipients enable row level security;

create policy "members read their circles" on public.care_circles
  for select to authenticated using (public.is_member(id));

create policy "members read fellow members" on public.members
  for select to authenticated using (public.is_member(circle_id));

create policy "members read recipients" on public.care_recipients
  for select to authenticated using (public.is_member(circle_id));
create policy "members add recipients" on public.care_recipients
  for insert to authenticated with check (public.is_member(circle_id));
create policy "members edit recipients" on public.care_recipients
  for update to authenticated using (public.is_member(circle_id)) with check (public.is_member(circle_id));

-- The only way to create a Care Circle: its creator becomes its admin, atomically.
create function public.create_care_circle(recipient_name text, relation text, needs text[])
returns uuid language plpgsql security definer set search_path = '' as $$
declare circle uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  insert into public.care_circles (name, needs) values (recipient_name, coalesce(needs, '{}')) returning id into circle;
  insert into public.members (circle_id, user_id, role) values (circle, auth.uid(), 'admin');
  insert into public.care_recipients (circle_id, name, relation) values (circle, recipient_name, relation);
  return circle;
end $$;

revoke execute on function public.create_care_circle(text, text, text[]) from public, anon;
revoke execute on function public.is_member(uuid) from public, anon;
grant execute on function public.create_care_circle(text, text, text[]) to authenticated;
grant execute on function public.is_member(uuid) to authenticated;
