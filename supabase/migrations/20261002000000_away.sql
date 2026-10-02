-- Away (#30, see CONTEXT.md): v3's "Saya tidak bisa minggu ini" marks a Member Away until the week ends and asks
-- someone for each of their days left (one swap a day, each with its own WhatsApp YA/TIDAK); and v3's month view.

create type public.away_reason as enum ('sick', 'travelling', 'work', 'other'); -- Sakit, Bepergian, Pekerjaan, Lainnya

create table public.aways (
  circle_id uuid not null,
  user_id uuid not null default auth.uid(),
  week date not null check (extract(isodow from week) = 1), -- the Monday, in the Member's own calendar
  reason public.away_reason not null,
  at timestamptz not null default now(),
  primary key (circle_id, user_id, week),
  foreign key (circle_id, user_id) references public.members (circle_id, user_id) on delete cascade
);
alter table public.aways enable row level security;

-- "Alasan (hanya saudara yang melihat)": not the Care Recipient. Written only through go_away.
create policy "siblings read aways" on public.aways
  for select to authenticated using (public.is_member(circle_id) and not exists (
    select 1 from public.members m where m.circle_id = aways.circle_id and m.user_id = auth.uid() and m.role = 'parent'));

-- All or none: each ask is ask_swap's (only the holder, of a current Member), which queues its WhatsApp.
create function public.go_away(circle uuid, on_week date, why public.away_reason, asks jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare a jsonb;
begin
  -- Only those who take Duty days (v3 SIBS): not the Care Recipient nor a viewer.
  if not exists (select 1 from public.members where circle_id = circle and user_id = auth.uid() and left_at is null
      and role not in ('parent', 'viewer')) then
    raise exception 'not a member';
  end if;
  insert into public.aways (circle_id, user_id, week, reason) values (circle, auth.uid(), on_week, why)
    on conflict (circle_id, user_id, week) do update set reason = excluded.reason;
  for a in select * from jsonb_array_elements(asks) loop
    if not exists (select 1 from public.duties where id = (a->>'duty')::uuid and circle_id = circle) then
      raise exception 'no such duty';
    end if;
    perform public.ask_swap((a->>'duty')::uuid, (a->>'day')::date, (a->>'member')::uuid);
  end loop;
end $$;

-- v3 month view, per current Member: drives, Duty days (v3 "telepon") and the rest (v3 "lainnya": doses given,
-- Document versions, Expenses paid) in the month of on_day. Runs as the caller, so each count follows their
-- Data Categories.
-- ponytail: a drive's and a Document's day is Jakarta's (WIB), like the server's other days.
create function public.month_load(circle uuid, on_day date)
returns table (member_id uuid, drives int, calls int, other int)
language sql stable set search_path = '' as $$
  with month as (select date_trunc('month', on_day)::date as first,
      (date_trunc('month', on_day) + interval '1 month')::date as next)
  select m.user_id,
    (select count(*) from public.appointments a, month
      where a.circle_id = circle and a.driver_id = m.user_id and a.cancelled_at is null
        and public.wib(a.starts_at)::date >= month.first and public.wib(a.starts_at)::date < month.next)::int,
    (select count(*) from public.duties d, month, generate_series(month.first, month.next - 1, interval '1 day') g(day)
      -- From the day it was made: before that the rotation still names a holder (starts_on is anchored back).
      where d.circle_id = circle and g.day::date >= public.wib(d.created_at)::date
        and public.duty_holder(d.id, g.day::date) = m.user_id)::int,
    ((select count(*) from public.dose_logs x, month
        where x.circle_id = circle and x.given_by = m.user_id and x.day >= month.first and x.day < month.next)
      + (select count(*) from public.documents x, month
        where x.circle_id = circle and x.by = m.user_id and public.wib(x.at)::date >= month.first and public.wib(x.at)::date < month.next)
      + (select count(*) from public.expenses x, month
        where x.circle_id = circle and x.paid_by = m.user_id and x.day >= month.first and x.day < month.next))::int
  from public.members m
  where m.circle_id = circle and m.left_at is null and public.is_member(circle)
$$;

revoke execute on function public.go_away(uuid, date, public.away_reason, jsonb), public.month_load(uuid, date) from public, anon;
grant execute on function public.go_away(uuid, date, public.away_reason, jsonb), public.month_load(uuid, date) to authenticated;
