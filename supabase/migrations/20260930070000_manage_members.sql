-- Managing Members (see CONTEXT.md). Leaving or being removed makes a Former Member: the row stays, with left_at set,
-- so what they wrote keeps their name, but every access check below ignores them. The viewer Role reads only.

alter table public.members add column left_at timestamptz;

create or replace function public.is_member(circle uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.members where circle_id = circle and user_id = auth.uid() and left_at is null)
$$;

create or replace function public.is_admin(circle uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.members
    where circle_id = circle and user_id = auth.uid() and left_at is null and role = 'admin')
$$;

-- A Member who may change things: everyone but a viewer.
create function public.is_writer(circle uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.members
    where circle_id = circle and user_id = auth.uid() and left_at is null and role <> 'viewer')
$$;
revoke execute on function public.is_writer(uuid) from public, anon;
grant execute on function public.is_writer(uuid) to authenticated;

alter policy "members add recipients" on public.care_recipients with check (public.is_writer(circle_id));
alter policy "members edit recipients" on public.care_recipients using (public.is_writer(circle_id)) with check (public.is_writer(circle_id));
alter policy "members add providers" on public.providers with check (public.is_writer(circle_id));
alter policy "members schedule appointments" on public.appointments with check (public.is_writer(circle_id));
alter policy "members edit appointments" on public.appointments using (public.is_writer(circle_id)) with check (public.is_writer(circle_id));
alter policy "members add medications" on public.medications with check (public.is_writer(circle_id));
alter policy "members edit medications" on public.medications using (public.is_writer(circle_id)) with check (public.is_writer(circle_id));
alter policy "members add care contacts" on public.care_contacts with check (public.is_writer(circle_id));
alter policy "members edit care contacts" on public.care_contacts using (public.is_writer(circle_id)) with check (public.is_writer(circle_id));
alter policy "members remove care contacts" on public.care_contacts using (public.is_writer(circle_id));
alter policy "members ask questions" on public.questions with check (public.is_writer(circle_id) and asked_by = auth.uid());

-- A Former Member stays attendee_id on past visits, so being the Attendee is not enough.
create or replace function public.is_attendee(appointment uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.appointments
    where id = appointment and attendee_id = auth.uid() and public.is_writer(circle_id))
$$;

-- An accepted Invitation to a Former Member makes them a Member again.
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
  update public.invitations set accepted_at = now() where id = i.id;
  return i.circle_id;
end $$;

create function public.promote_member(circle uuid, member uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin(circle) then raise exception 'only an admin can promote'; end if;
  update public.members set role = 'admin' where circle_id = circle and user_id = member and left_at is null;
  if not found then raise exception 'not a member'; end if;
end $$;
revoke execute on function public.promote_member(uuid, uuid) from public, anon;
grant execute on function public.promote_member(uuid, uuid) to authenticated;

-- Admins remove anyone; everyone may remove themselves (leave). Never the last admin, so the circle keeps one.
create function public.remove_member(circle uuid, member uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare admins int;
begin
  if not (public.is_admin(circle) or (member = auth.uid() and public.is_member(circle))) then
    raise exception 'only an admin can remove a member';
  end if;
  -- Locks the admins, so two admins leaving at once can't both pass the count.
  select count(*) into admins from (
    select 1 from public.members where circle_id = circle and role = 'admin' and left_at is null for update) a;
  update public.members set left_at = now()
    where circle_id = circle and user_id = member and left_at is null
      and (role <> 'admin' or admins > 1);
  if not found then
    if exists (select 1 from public.members where circle_id = circle and user_id = member and left_at is null) then
      raise exception 'last admin';
    end if;
    raise exception 'not a member';
  end if;
  -- Upcoming Appointments lose them; past ones keep their name.
  update public.appointments set driver_id = null where circle_id = circle and driver_id = member and starts_at > now();
  update public.appointments set attendee_id = null where circle_id = circle and attendee_id = member and starts_at > now();
end $$;
revoke execute on function public.remove_member(uuid, uuid) from public, anon;
grant execute on function public.remove_member(uuid, uuid) to authenticated;
