-- Data Category restrictions (see CONTEXT.md, ADR 0004), per Member as the prototype's `member` screen shows them
-- (approved in #9). v1 has three categories: Appointments (with their Questions), Visit Notes and Medications.
-- A row in hidden_categories hides one category of one Care Recipient's data from one Member, on every read.
-- When the Care Recipient is a Member (Role parent, linked below), only they set these; otherwise the admins do,
-- and admins then see everything.

create type public.data_category as enum ('appointments', 'visit_notes', 'medications');

-- The Member who is this Care Recipient. Only an accepted parent Invitation sets it.
alter table public.care_recipients add column member_id uuid,
  add foreign key (circle_id, member_id) references public.members (circle_id, user_id);
revoke insert, update on public.care_recipients from authenticated;
grant insert (circle_id, name, relation), update (name, relation, allergies, conditions) on public.care_recipients to authenticated;

-- onb3 "Bapak atur per orang": what Members who join later start without.
alter table public.care_circles add column hide_by_default public.data_category[] not null default '{}';

create table public.hidden_categories (
  circle_id uuid not null,
  recipient_id uuid not null,
  member_id uuid not null,
  category public.data_category not null,
  primary key (recipient_id, member_id, category),
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, member_id) references public.members (circle_id, user_id) on delete cascade
);
create index on public.hidden_categories (circle_id);
alter table public.hidden_categories enable row level security;
revoke insert, update, delete on public.hidden_categories from authenticated;
-- Everyone sees who can see what; only set_hidden changes it.
create policy "members read restrictions" on public.hidden_categories
  for select to authenticated using (public.is_member(circle_id));

-- The Care Recipient, while they are a Member; null when the admins set restrictions.
create function public.restrictions_owner(recipient uuid) returns uuid
language sql stable security definer set search_path = '' as $$
  select r.member_id from public.care_recipients r
  join public.members m on m.circle_id = r.circle_id and m.user_id = r.member_id and m.left_at is null
  where r.id = recipient
$$;

-- Restrictions set by the Care Recipient bind everyone, admins too. Set by the admins, they skip admins, so a
-- restriction on someone later promoted stays in place for if they stop being one.
create function public.sees(recipient uuid, category public.data_category) returns boolean
language sql stable security definer set search_path = '' as $$
  select not exists (
    select 1 from public.hidden_categories h join public.care_recipients r on r.id = h.recipient_id
    where h.recipient_id = recipient and h.member_id = auth.uid() and h.category = sees.category
      and (public.restrictions_owner(r.id) is not null or not public.is_admin(r.circle_id)))
$$;

create function public.set_hidden(recipient uuid, member uuid, category public.data_category, hidden boolean) returns void
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
  if hidden then
    insert into public.hidden_categories values (circle, recipient, member, set_hidden.category) on conflict do nothing;
  else
    delete from public.hidden_categories h
      where h.recipient_id = recipient and h.member_id = member and h.category = set_hidden.category;
  end if;
end $$;

-- ponytail: admins only, even once the Care Recipient is a Member; it only shapes who joins next.
create function public.hide_by_default(circle uuid, categories public.data_category[]) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin(circle) then raise exception 'only an admin sets restrictions'; end if;
  update public.care_circles set hide_by_default = coalesce(categories, '{}') where id = circle;
end $$;

create function public.hide_on_join() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.hidden_categories
    select new.circle_id, r.id, new.user_id, c
    from public.care_recipients r
    cross join unnest((select x.hide_by_default from public.care_circles x where x.id = new.circle_id)) c
    where r.circle_id = new.circle_id
    on conflict do nothing;
  return new;
end $$;
create trigger hide_on_join after insert or update of left_at on public.members
  for each row when (new.left_at is null and new.role not in ('admin', 'parent')) execute function public.hide_on_join();

-- Every read of the three categories goes through sees(). Questions and Visit Notes follow their Appointment, since
-- a policy's subquery on appointments is itself filtered by its policy.
alter policy "members read appointments" on public.appointments
  using (public.is_member(circle_id) and public.sees(recipient_id, 'appointments'));
alter policy "members read questions" on public.questions
  using (public.is_member(circle_id) and exists (select 1 from public.appointments a where a.id = appointment_id));
alter policy "members read visit notes" on public.visit_notes
  using (public.is_member(circle_id) and exists (
    select 1 from public.appointments a where a.id = appointment_id and public.sees(a.recipient_id, 'visit_notes')));
alter policy "members read answers" on public.answers
  using (public.is_member(circle_id) and exists (select 1 from public.visit_notes n where n.appointment_id = answers.appointment_id));
alter policy "members read medications" on public.medications
  using (public.is_member(circle_id) and public.sees(recipient_id, 'medications'));

-- Carry-over must not change for whoever can't see Visit Notes: it asks these instead of reading them.
create function public.has_visit_note(appointment uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.visit_notes n where n.appointment_id = appointment)
$$;
create function public.is_answered(question uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.answers x where x.question_id = question and x.answer <> '')
$$;

create or replace view public.appointment_questions with (security_invoker = true) as
  select x.appointment_id, q.id, q.circle_id, q.appointment_id as asked_in, o.starts_at as asked_for,
    q.asked_by, m.name as asked_by_name, q.text, x.answer, q.created_at
  from public.answers x
  join public.questions q on q.id = x.question_id
  join public.appointments o on o.id = q.appointment_id
  left join public.members m on m.circle_id = q.circle_id and m.user_id = q.asked_by
union all
  select a.id, q.id, q.circle_id, q.appointment_id, o.starts_at, q.asked_by, m.name, q.text, null, q.created_at
  from public.questions q
  join public.appointments o on o.id = q.appointment_id
  left join public.members m on m.circle_id = q.circle_id and m.user_id = q.asked_by
  cross join lateral (
    select a.id from public.appointments a
    where a.recipient_id = o.recipient_id and a.provider_id = o.provider_id and a.cancelled_at is null
      and a.starts_at >= o.starts_at
      and not public.has_visit_note(a.id)
    order by a.starts_at, a.id <> o.id, a.id
    limit 1
  ) a
  where not public.is_answered(q.id);

-- Emergency Info's "Obat saat ini" in the app skips Data Category, like the QR page (ADR 0003).
create function public.emergency_medications(recipient uuid) returns setof public.medications
language sql stable security definer set search_path = '' as $$
  select m.* from public.medications m join public.care_recipients r on r.id = m.recipient_id
  where m.recipient_id = recipient and m.active and public.is_member(r.circle_id)
  order by m.time_of_day, m.name
$$;

-- A parent Invitation names the Care Recipient it is; accepting links them, unless someone else already is.
alter table public.invitations add column recipient_id uuid,
  add foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  add check ((role = 'parent') = (recipient_id is not null));
revoke select on public.invitations from authenticated;
grant select (id, circle_id, name, phone, role, recipient_id, created_at, accepted_at, cancelled_at) on public.invitations to authenticated;

drop function public.invite(uuid, text, text, public.role);
create function public.invite(circle uuid, invitee_name text, invitee_phone text, invitee_role public.role default 'sibling', recipient uuid default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare invitation uuid;
begin
  if not public.is_admin(circle) then raise exception 'only an admin can invite'; end if;
  if recipient is not null and public.restrictions_owner(recipient) is not null then
    raise exception 'the care recipient is already a member';
  end if;
  insert into public.invitations (circle_id, name, phone, role, invited_by, recipient_id)
    values (circle, invitee_name, invitee_phone, invitee_role, auth.uid(), recipient)
    on conflict (circle_id, phone) where accepted_at is null and cancelled_at is null
    do update set name = excluded.name, role = excluded.role, recipient_id = excluded.recipient_id
    returning id into invitation;
  return invitation;
end $$;
revoke execute on function public.invite(uuid, text, text, public.role, uuid) from public, anon;
grant execute on function public.invite(uuid, text, text, public.role, uuid) to authenticated;

create or replace function public.join_by_invitation(invitation uuid, member uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare i public.invitations;
begin
  select * into i from public.invitations
    where id = invitation and accepted_at is null and cancelled_at is null
      and regexp_replace(phone, '\D', '', 'g') = (select u.phone from auth.users u where u.id = member)
    for update;
  if not found then raise exception 'no pending invitation for this number'; end if;
  insert into public.members (circle_id, user_id, role, name) values (i.circle_id, member, i.role, i.name)
    on conflict (circle_id, user_id) do update set role = excluded.role, name = excluded.name, left_at = null, created_at = now()
    where public.members.left_at is not null;
  if i.role = 'parent' then
    if public.restrictions_owner(i.recipient_id) is distinct from member and public.restrictions_owner(i.recipient_id) is not null then
      raise exception 'the care recipient is already a member';
    end if;
    update public.care_recipients set member_id = member where id = i.recipient_id;
    -- Nothing stays hidden from the Care Recipient, say from an earlier time as a sibling.
    delete from public.hidden_categories h where h.recipient_id = i.recipient_id and h.member_id = member;
  end if;
  update public.invitations set accepted_at = now() where id = i.id;
  return i.circle_id;
end $$;

-- The Care Recipient takes no Duty turns (their Role is parent).
drop trigger join_rotations on public.members;
create trigger join_rotations after insert or update of left_at on public.members
  for each row when (new.left_at is null and new.role not in ('viewer', 'parent')) execute function public.join_rotations();

-- invitee's "Bapak membagikan kepada Anda" (#9): what they will start without.
drop function public.my_invitations();
create function public.my_invitations()
returns table (id uuid, name text, inviter text, circle text, duty_id uuid, duty text, duty_holder text, hidden public.data_category[])
language sql stable security definer set search_path = '' as $$
  select i.id, i.name, m.name, c.name, d.id, d.name, h.name,
    case when i.role in ('admin', 'parent') then '{}' else c.hide_by_default end
  from public.invitations i
  join public.care_circles c on c.id = i.circle_id
  left join public.members m on m.circle_id = i.circle_id and m.user_id = i.invited_by
  left join lateral (select x.id, x.name from public.duties x
    where x.circle_id = i.circle_id and i.role not in ('viewer', 'parent') order by x.created_at limit 1) d on true
  left join public.members h on h.circle_id = i.circle_id and h.user_id = public.duty_holder(d.id, public.this_week() + 7)
  where i.accepted_at is null and i.cancelled_at is null
    and regexp_replace(i.phone, '\D', '', 'g') = (select u.phone from auth.users u where u.id = auth.uid())
  order by i.created_at
$$;

revoke execute on function public.restrictions_owner(uuid), public.sees(uuid, public.data_category),
  public.set_hidden(uuid, uuid, public.data_category, boolean), public.hide_by_default(uuid, public.data_category[]),
  public.hide_on_join(), public.has_visit_note(uuid), public.is_answered(uuid), public.emergency_medications(uuid),
  public.my_invitations() from public, anon;
grant execute on function public.restrictions_owner(uuid), public.sees(uuid, public.data_category),
  public.set_hidden(uuid, uuid, public.data_category, boolean), public.hide_by_default(uuid, public.data_category[]),
  public.has_visit_note(uuid), public.is_answered(uuid), public.emergency_medications(uuid), public.my_invitations() to authenticated;
