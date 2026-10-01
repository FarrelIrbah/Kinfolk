-- Expense (#28, see CONTEXT.md): v3's Biaya, who paid what for the care, in whole Rupiah. Kinfolk never bills
-- anyone. Under the Tagihan & uang Data Category; not on the Timeline, no edit or delete (as v3).

create table public.expenses (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  recipient_id uuid not null,
  what text not null check (trim(what) <> ''), -- "Alarm jatuh (DP)"
  amount bigint not null check (amount > 0), -- 45000
  paid_by uuid not null,
  day date not null, -- the writer's calendar day
  by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, paid_by) references public.members (circle_id, user_id),
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.expenses (circle_id, day);
alter table public.expenses enable row level security;

create policy "members read expenses" on public.expenses
  for select to authenticated using (public.is_member(circle_id) and public.sees(recipient_id, 'money'));
create policy "members add expenses" on public.expenses
  for insert to authenticated with check (
    public.is_writer(circle_id) and public.sees(recipient_id, 'money') and by = auth.uid()
    -- Paid by one of v3's payers (the "Dibayar oleh" chips): a current Member, not the Care Recipient nor a viewer.
    and exists (select 1 from public.members m where m.circle_id = expenses.circle_id and m.user_id = paid_by
      and m.left_at is null and m.role not in ('parent', 'viewer')));
