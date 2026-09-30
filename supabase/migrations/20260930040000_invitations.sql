-- Invitation (see CONTEXT.md). A pending Invitation is not a Member: it grants nothing until accepted, in the app by
-- the signed-in owner of the invited number, or on the web page by whoever holds the link sent to that number.

alter table public.members add column name text; -- "Sri"; an invited Member gets the name the admin typed

-- onb1 now asks "Nama Anda" (owner-approved in #4); the creator's name becomes their Member name.
drop function public.create_care_circle(text, text, text[]);
create function public.create_care_circle(recipient_name text, relation text, needs text[], my_name text default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare circle uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  insert into public.care_circles (name, needs) values (recipient_name, coalesce(needs, '{}')) returning id into circle;
  insert into public.members (circle_id, user_id, role, name) values (circle, auth.uid(), 'admin', my_name);
  insert into public.care_recipients (circle_id, name, relation) values (circle, recipient_name, relation);
  return circle;
end $$;
revoke execute on function public.create_care_circle(text, text, text[], text) from public, anon;
grant execute on function public.create_care_circle(text, text, text[], text) to authenticated;

create function public.is_admin(circle uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.members where circle_id = circle and user_id = auth.uid() and role = 'admin')
$$;
revoke execute on function public.is_admin(uuid) from public, anon;
grant execute on function public.is_admin(uuid) to authenticated;

create table public.invitations (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.care_circles on delete cascade,
  name text not null, -- "Budi"
  phone text not null check (phone ~ '^\+[1-9][0-9]{7,14}$'), -- E.164
  role public.role not null default 'sibling',
  invited_by uuid not null references auth.users on delete cascade,
  link uuid not null unique default gen_random_uuid(), -- the web link's secret; Members can't read it
  created_at timestamptz not null default now(),
  accepted_at timestamptz,
  cancelled_at timestamptz
);
create index on public.invitations (circle_id);
create unique index invitations_pending on public.invitations (circle_id, phone) where accepted_at is null and cancelled_at is null;

alter table public.invitations enable row level security;
revoke all on public.invitations from anon, authenticated;
grant select (id, circle_id, name, phone, role, created_at, accepted_at, cancelled_at), update (cancelled_at)
  on public.invitations to authenticated;

create policy "admins read invitations" on public.invitations
  for select to authenticated using (public.is_admin(circle_id));
-- Cancelling is the only change, and only while pending; a cancelled Invitation stays cancelled.
create policy "admins cancel invitations" on public.invitations
  for update to authenticated using (public.is_admin(circle_id) and accepted_at is null and cancelled_at is null)
  with check (cancelled_at is not null);

-- Called by the invite Edge Function with the admin's own token, which then sends the WhatsApp message.
-- Inviting a number that's already pending re-sends the same Invitation.
create function public.invite(circle uuid, invitee_name text, invitee_phone text, invitee_role public.role default 'sibling')
returns uuid language plpgsql security definer set search_path = '' as $$
declare invitation uuid;
begin
  if not public.is_admin(circle) then raise exception 'only an admin can invite'; end if;
  insert into public.invitations (circle_id, name, phone, role, invited_by)
    values (circle, invitee_name, invitee_phone, invitee_role, auth.uid())
    on conflict (circle_id, phone) where accepted_at is null and cancelled_at is null
    do update set name = excluded.name, role = excluded.role
    returning id into invitation;
  return invitation;
end $$;
revoke execute on function public.invite(uuid, text, text, public.role) from public, anon;
grant execute on function public.invite(uuid, text, text, public.role) to authenticated;

-- What the invite message and the web page show. Service role only.
create function public.invitation_card(invitation uuid default null, link uuid default null)
returns table (phone text, name text, inviter text, circle text, link uuid, pending boolean, accepted boolean)
language sql stable security definer set search_path = '' as $$
  select i.phone, i.name, m.name, c.name, i.link, i.accepted_at is null and i.cancelled_at is null, i.accepted_at is not null
  from public.invitations i
  join public.care_circles c on c.id = i.circle_id
  left join public.members m on m.circle_id = i.circle_id and m.user_id = i.invited_by
  where i.id = invitation_card.invitation or i.link = invitation_card.link
$$;
revoke execute on function public.invitation_card(uuid, uuid) from public, anon, authenticated;

-- The pending Invitations to the signed-in Member's own (OTP-verified) number.
create function public.my_invitations() returns table (id uuid, name text, inviter text, circle text)
language sql stable security definer set search_path = '' as $$
  select i.id, i.name, m.name, c.name
  from public.invitations i
  join public.care_circles c on c.id = i.circle_id
  left join public.members m on m.circle_id = i.circle_id and m.user_id = i.invited_by
  where i.accepted_at is null and i.cancelled_at is null
    and regexp_replace(i.phone, '\D', '', 'g') = (select u.phone from auth.users u where u.id = auth.uid())
  order by i.created_at
$$;
revoke execute on function public.my_invitations() from public, anon;
grant execute on function public.my_invitations() to authenticated;

create function public.join_by_invitation(invitation uuid, member uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare i public.invitations;
begin
  select * into i from public.invitations
    where id = invitation and accepted_at is null and cancelled_at is null
      and regexp_replace(phone, '\D', '', 'g') = (select u.phone from auth.users u where u.id = member)
    for update;
  if not found then raise exception 'no pending invitation for this number'; end if;
  insert into public.members (circle_id, user_id, role, name) values (i.circle_id, member, i.role, i.name)
    on conflict (circle_id, user_id) do nothing;
  update public.invitations set accepted_at = now() where id = i.id;
  return i.circle_id;
end $$;
revoke execute on function public.join_by_invitation(uuid, uuid) from public, anon, authenticated;

-- In the app: accepts one of my_invitations(). Returns the Care Circle id.
create function public.accept_invitation(invitation uuid) returns uuid
language sql security definer set search_path = '' as $$
  select public.join_by_invitation(invitation, auth.uid())
$$;
revoke execute on function public.accept_invitation(uuid) from public, anon;
grant execute on function public.accept_invitation(uuid) to authenticated;

-- On the web page: the link was delivered to the invited number, so holding it stands for owning that number.
-- The invitation Edge Function creates the phone's Auth user first if they never signed in. Service role only.
create function public.accept_invitation_link(link uuid) returns uuid
language sql security definer set search_path = '' as $$
  select public.join_by_invitation(i.id, u.id)
  from public.invitations i
  join auth.users u on u.phone = regexp_replace(i.phone, '\D', '', 'g')
  where i.link = accept_invitation_link.link
$$;
revoke execute on function public.accept_invitation_link(uuid) from public, anon, authenticated;
