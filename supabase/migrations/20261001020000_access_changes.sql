-- Six Data Categories and the access change log (#20). v3's three new ones join the enum: Documents, Wishes &
-- legal, Bills & money; visit_notes keeps its meaning and is relabelled "Rekaman kunjungan" in the app. Each change
-- set_hidden makes is logged (`member`'s "Riwayat perubahan") and shows on the Timeline as a Check-in.

alter type public.data_category add value 'documents';
alter type public.data_category add value 'wishes';
alter type public.data_category add value 'money';

-- [on_behalf_of]: the Care Recipient, when they owned the restrictions then (ADR 0004). [text]: the Timeline's,
-- fixed when it happens like timeline_events.
create table public.access_changes (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null,
  recipient_id uuid not null,
  member_id uuid not null,
  category public.data_category not null,
  hidden boolean not null,
  by uuid not null,
  on_behalf_of uuid,
  at timestamptz not null default now(),
  text text not null,
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, member_id) references public.members (circle_id, user_id),
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.access_changes (circle_id, at);
alter table public.access_changes enable row level security;
revoke insert, update, delete on public.access_changes from authenticated;
-- Like hidden_categories: everyone sees who can see what.
create policy "members read access changes" on public.access_changes
  for select to authenticated using (public.is_member(circle_id));

create or replace function public.set_hidden(recipient uuid, member uuid, category public.data_category, hidden boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare circle uuid; owner uuid := public.restrictions_owner(recipient);
begin
  select r.circle_id into circle from public.care_recipients r where r.id = recipient;
  if owner is not null then
    if owner is distinct from auth.uid() then raise exception 'only the care recipient sets restrictions'; end if;
    if member = owner then raise exception 'nothing is hidden from the care recipient'; end if;
  elsif not public.is_admin(circle) then
    raise exception 'only an admin sets restrictions';
  elsif exists (select 1 from public.members m where m.circle_id = circle and m.user_id = member and m.role = 'admin') then
    raise exception 'admins see everything';
  end if;
  if not exists (select 1 from public.members m where m.circle_id = circle and m.user_id = member and m.left_at is null) then
    raise exception 'not a member';
  end if;
  if set_hidden.hidden then
    insert into public.hidden_categories values (circle, recipient, member, set_hidden.category) on conflict do nothing;
  else
    delete from public.hidden_categories h
      where h.recipient_id = recipient and h.member_id = member and h.category = set_hidden.category;
  end if;
  if not found then return; end if; -- nothing changed, nothing to log

  -- "Urungkan": taking back one's own change moments later erases it instead of logging another.
  -- ponytail: 15 s covers the toast's 5 s plus a slow network; an id from the RPC if it ever needs exact.
  delete from public.access_changes c
    where c.id = (select x.id from public.access_changes x
        where x.recipient_id = recipient and x.member_id = member and x.category = set_hidden.category
        order by x.at desc limit 1)
      and c.by = auth.uid() and c.hidden <> set_hidden.hidden and c.at > now() - interval '15 seconds';
  if found then return; end if;

  insert into public.access_changes (circle_id, recipient_id, member_id, category, hidden, by, on_behalf_of, text)
    select circle, recipient, member, set_hidden.category, set_hidden.hidden, auth.uid(), owner,
      -- The app's labels (`member`), e.g. "Rekaman kunjungan disembunyikan dari Dewi."
      case set_hidden.category::text
        when 'appointments' then 'Janji dokter' when 'visit_notes' then 'Rekaman kunjungan'
        when 'medications' then 'Obat' when 'documents' then 'Dokumen'
        when 'wishes' then 'Keinginan & hukum' when 'money' then 'Tagihan & uang' end
      || case when set_hidden.hidden then ' disembunyikan dari ' else ' dibagikan ke ' end || m.name || '.'
    from public.members m where m.circle_id = circle and m.user_id = member;
end $$;

-- Access changes join the Timeline without an Appointment; whoever reads the circle reads them.
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
  left join public.members m on m.circle_id = c.circle_id and m.user_id = c.by;
