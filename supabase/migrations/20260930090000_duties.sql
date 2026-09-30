-- Duty (see CONTEXT.md): a recurring task that passes to the next Member in its rotation every week, Monday to Sunday.
-- Who holds it in a week is worked out here (duty_holder), never stored: the rotation, then any accepted swap.
-- Members read everything; every change goes through the functions below.

create table public.duties (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  name text not null, -- "Telepon cek malam"
  time_of_day time not null, -- 19.00
  starts_on date not null check (extract(isodow from starts_on) = 1), -- the Monday the first in the rotation holds it
  created_at timestamptz not null default now()
);
create index on public.duties (circle_id);

-- A Former Member's row stays, so the others keep their turns; theirs pass to the next Member.
create table public.duty_rotation (
  duty_id uuid not null references public.duties on delete cascade,
  circle_id uuid not null,
  user_id uuid not null,
  pos int not null,
  primary key (duty_id, user_id),
  foreign key (circle_id, user_id) references public.members (circle_id, user_id)
);

-- One week's turn handed from from_id to to_id; it counts once to_id accepts.
create table public.duty_swaps (
  id uuid primary key default gen_random_uuid(),
  duty_id uuid not null references public.duties on delete cascade,
  circle_id uuid not null,
  week date not null check (extract(isodow from week) = 1),
  from_id uuid, -- null when nobody held it
  to_id uuid not null,
  asked_at timestamptz not null default now(),
  answered_at timestamptz,
  accepted boolean not null default false,
  foreign key (circle_id, to_id) references public.members (circle_id, user_id)
);
create index on public.duty_swaps (duty_id, week);

alter table public.duties enable row level security;
alter table public.duty_rotation enable row level security;
alter table public.duty_swaps enable row level security;
create policy "members read duties" on public.duties for select to authenticated using (public.is_member(circle_id));
create policy "members read rotations" on public.duty_rotation for select to authenticated using (public.is_member(circle_id));
create policy "members read swaps" on public.duty_swaps for select to authenticated using (public.is_member(circle_id));

-- ponytail: "this week" on the server is Jakarta's (WIB); the app passes the device's own week everywhere else.
create function public.this_week() returns date language sql stable set search_path = '' as $$
  select date_trunc('week', now() at time zone 'Asia/Jakarta')::date
$$;

-- The rotation moves one place a week from starts_on, skipping Former Members; swaps aside.
create function public.duty_turn(duty uuid, on_week date) returns uuid
language sql stable set search_path = '' as $$
  select o.user_id from (
      select r.user_id, m.left_at, row_number() over (order by r.pos) - 1 as i, count(*) over () as n
      from public.duty_rotation r join public.members m on m.circle_id = r.circle_id and m.user_id = r.user_id
      where r.duty_id = duty) o
    cross join (select (on_week - starts_on) / 7 as k from public.duties where id = duty) d
    where o.left_at is null
    order by ((o.i - d.k) % o.n + o.n) % o.n limit 1
$$;

-- The last accepted swap overrides the rotation.
create function public.duty_holder(duty uuid, on_week date) returns uuid
language sql stable set search_path = '' as $$
  select coalesce(
    (select s.to_id from public.duty_swaps s
      join public.members m on m.circle_id = s.circle_id and m.user_id = s.to_id and m.left_at is null
      where s.duty_id = duty and s.week = on_week and s.accepted
      order by s.answered_at desc limit 1),
    public.duty_turn(duty, on_week))
$$;

-- After the rotation changes, whoever it gave on_week keeps it and the others follow in the new order.
create function public.anchor_duty(duty uuid, keep uuid, on_week date) returns void
language sql set search_path = '' as $$
  update public.duties set starts_on = on_week - 7 * coalesce((
    select o.i::int from (select r.user_id, row_number() over (order by r.pos) - 1 as i
      from public.duty_rotation r where r.duty_id = anchor_duty.duty) o
    where o.user_id = keep), 0)
  where id = duty
$$;

create function public.duty_week(circle uuid, on_week date)
returns table (duty_id uuid, name text, time_of_day time, holder uuid, rotation uuid[], swap_id uuid, swap_to uuid)
language sql stable set search_path = '' as $$
  select d.id, d.name, d.time_of_day, h.holder,
    array(select r.user_id from public.duty_rotation r
      join public.members m on m.circle_id = r.circle_id and m.user_id = r.user_id and m.left_at is null
      where r.duty_id = d.id order by r.pos),
    s.id, s.to_id
  from public.duties d
  cross join lateral (select public.duty_holder(d.id, on_week) as holder) h
  -- A swap asked by someone who no longer holds the turn, or of a Former Member, has lapsed.
  left join lateral (select s.id, s.to_id from public.duty_swaps s
      join public.members m on m.circle_id = s.circle_id and m.user_id = s.to_id and m.left_at is null
      where s.duty_id = d.id and s.week = on_week and s.answered_at is null and s.from_id = h.holder
      order by s.asked_at desc limit 1) s on true
  where d.circle_id = circle
  order by d.created_at
$$;

create function public.save_duty(circle uuid, duty uuid, duty_name text, at_time time, rotation uuid[], on_week date)
returns uuid language plpgsql security definer set search_path = '' as $$
declare keep uuid;
begin
  if not public.is_admin(circle) then raise exception 'only an admin can change duties'; end if;
  if exists (select 1 from unnest(rotation) u where not exists (
      select 1 from public.members m where m.circle_id = circle and m.user_id = u and m.left_at is null)) then
    raise exception 'not a member';
  end if;
  if duty is null then
    insert into public.duties (circle_id, name, time_of_day, starts_on) values (circle, duty_name, at_time, on_week)
      returning id into duty;
  else
    keep := public.duty_turn(duty, on_week);
    update public.duties set name = duty_name, time_of_day = at_time where id = duty and circle_id = circle;
    if not found then raise exception 'no such duty'; end if;
    delete from public.duty_rotation where duty_id = duty;
  end if;
  insert into public.duty_rotation (duty_id, circle_id, user_id, pos)
    select duty, circle, u, o from unnest(rotation) with ordinality x(u, o);
  -- Editing keeps this week's holder, if still in the rotation; else the first starts.
  perform public.anchor_duty(duty, keep, on_week);
  return duty;
end $$;

create function public.delete_duty(duty uuid) returns void language plpgsql security definer set search_path = '' as $$
begin
  delete from public.duties where id = duty and public.is_admin(circle_id);
  if not found then raise exception 'only an admin can delete a duty'; end if;
end $$;

-- The holder asks another Member; asking again replaces the last ask.
create function public.ask_swap(duty uuid, on_week date, member uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare circle uuid; swap uuid;
begin
  select circle_id into circle from public.duties where id = duty;
  if circle is null or not public.is_member(circle) then raise exception 'no such duty'; end if;
  if public.duty_holder(duty, on_week) is distinct from auth.uid() then raise exception 'not your turn'; end if;
  if member = auth.uid() or not exists (
      select 1 from public.members where circle_id = circle and user_id = member and left_at is null) then
    raise exception 'not a member';
  end if;
  update public.duty_swaps set answered_at = now() where duty_id = duty and week = on_week and answered_at is null;
  insert into public.duty_swaps (duty_id, circle_id, week, from_id, to_id) values (duty, circle, on_week, auth.uid(), member)
    returning id into swap;
  return swap;
end $$;

create function public.answer_swap(swap uuid, accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare s public.duty_swaps;
begin
  select * into s from public.duty_swaps where id = swap and answered_at is null and to_id = auth.uid() for update;
  if not found or not public.is_member(s.circle_id) then raise exception 'no swap asked of you'; end if;
  if public.duty_holder(s.duty_id, s.week) is distinct from s.from_id then raise exception 'swap lapsed'; end if;
  update public.duty_swaps set answered_at = now(), accepted = accept where id = swap;
end $$;

-- Volunteering for someone else's turn needs no one's agreement but the volunteer's (invitee, approved in #10).
create function public.take_turn(duty uuid, on_week date) returns void
language plpgsql security definer set search_path = '' as $$
declare circle uuid; holder uuid;
begin
  select circle_id into circle from public.duties where id = duty;
  if circle is null or not public.is_writer(circle) then raise exception 'no such duty'; end if;
  holder := public.duty_holder(duty, on_week);
  if holder is not distinct from auth.uid() then return; end if;
  insert into public.duty_swaps (duty_id, circle_id, week, from_id, to_id, answered_at, accepted)
    values (duty, circle, on_week, holder, auth.uid(), now(), true);
end $$;

-- Members join every rotation at the end once they accept (or rejoin), never while only invited; viewers only read.
-- Whoever holds this week keeps it.
create function public.join_rotations() returns trigger language plpgsql security definer set search_path = '' as $$
declare d public.duties; keep uuid;
begin
  for d in select * from public.duties where circle_id = new.circle_id loop
    keep := public.duty_turn(d.id, public.this_week());
    insert into public.duty_rotation (duty_id, circle_id, user_id, pos)
      values (d.id, d.circle_id, new.user_id, coalesce((select max(r.pos) from public.duty_rotation r where r.duty_id = d.id), 0) + 1)
      on conflict do nothing;
    perform public.anchor_duty(d.id, keep, public.this_week());
  end loop;
  return new;
end $$;
create trigger join_rotations after insert or update of left_at on public.members
  for each row when (new.left_at is null and new.role <> 'viewer') execute function public.join_rotations();

-- invitee's "Satu hal kecil, jika bisa" (#10): the first Duty and who holds it next week.
drop function public.my_invitations();
create function public.my_invitations()
returns table (id uuid, name text, inviter text, circle text, duty_id uuid, duty text, duty_holder text)
language sql stable security definer set search_path = '' as $$
  select i.id, i.name, m.name, c.name, d.id, d.name, h.name
  from public.invitations i
  join public.care_circles c on c.id = i.circle_id
  left join public.members m on m.circle_id = i.circle_id and m.user_id = i.invited_by
  left join lateral (select x.id, x.name from public.duties x
    where x.circle_id = i.circle_id and i.role <> 'viewer' order by x.created_at limit 1) d on true
  left join public.members h on h.circle_id = i.circle_id and h.user_id = public.duty_holder(d.id, public.this_week() + 7)
  where i.accepted_at is null and i.cancelled_at is null
    and regexp_replace(i.phone, '\D', '', 'g') = (select u.phone from auth.users u where u.id = auth.uid())
  order by i.created_at
$$;

revoke execute on function public.this_week(), public.duty_turn(uuid, date), public.duty_holder(uuid, date), public.anchor_duty(uuid, uuid, date), public.duty_week(uuid, date),
  public.save_duty(uuid, uuid, text, time, uuid[], date), public.delete_duty(uuid), public.ask_swap(uuid, date, uuid),
  public.answer_swap(uuid, boolean), public.take_turn(uuid, date), public.my_invitations(), public.join_rotations() from public, anon;
grant execute on function public.this_week(), public.duty_turn(uuid, date), public.duty_holder(uuid, date), public.duty_week(uuid, date),
  public.save_duty(uuid, uuid, text, time, uuid[], date), public.delete_duty(uuid), public.ask_swap(uuid, date, uuid),
  public.answer_swap(uuid, boolean), public.take_turn(uuid, date), public.my_invitations() to authenticated;
