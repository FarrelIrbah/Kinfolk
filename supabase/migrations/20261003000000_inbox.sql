-- v3 `inbox` (#31): its items are derived, no table of their own. Swaps asked of me come from here; Questions and
-- Tasks from their own reads.

-- Swaps still open asking me to take a day from from_day on, soonest first; RLS keeps it to my Care Circles.
create function public.swaps_to_me(circle uuid, from_day date)
returns table (swap_id uuid, duty_id uuid, name text, time_of_day time, day date, from_id uuid, asked_at timestamptz)
language sql stable set search_path = '' as $$
  select s.id, d.id, d.name, d.time_of_day, s.day, s.from_id, s.asked_at
  from public.duty_swaps s join public.duties d on d.id = s.duty_id
  where s.circle_id = circle and s.to_id = auth.uid() and s.answered_at is null and s.day >= from_day
    and s.id = (public.open_swap(s.duty_id, s.day)).id
  order by s.day, d.created_at
$$;

revoke execute on function public.swaps_to_me(uuid, date) from public, anon;
grant execute on function public.swaps_to_me(uuid, date) to authenticated;
