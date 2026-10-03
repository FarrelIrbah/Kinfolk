-- Emergency Info parity with v3 (#34, ADR 0003 updated): date of birth, weight and wishes on the Care Recipient, a
-- blood-thinner banner from Medications, and Members flagged by an admin as emergency contacts (shown first, with
-- "Jarak dari rumah"). The app and the QR page read the same summary, so they show the same fields.

alter table public.care_recipients
  add column born_on date, -- "lahir 12 Mar 1948", and the age
  add column weight_kg int check (weight_kg > 0), -- "64 kg"
  add column wishes text not null default ''; -- "Keinginan": "Tindakan penuh"
grant update (born_on, weight_kg, wishes) on public.care_recipients to authenticated; -- Form Info darurat, any Member

alter table public.members
  add column emergency boolean not null default false, -- an emergency contact on Emergency Info
  add column distance text not null default ''; -- "Jarak dari rumah": "10 menit"

-- Admins only. Never the Care Recipient themselves.
create function public.set_emergency_contact(circle uuid, member uuid, emergency boolean, distance text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin(circle) then raise exception 'only an admin can set emergency contacts'; end if;
  update public.members m set emergency = set_emergency_contact.emergency, distance = trim(set_emergency_contact.distance)
    where m.circle_id = circle and m.user_id = member and m.left_at is null
      and not exists (select 1 from public.care_recipients r where r.circle_id = circle and r.member_id = member);
  if not found then raise exception 'not a member'; end if;
end $$;

-- Emergency Info of [r]: only these fields, ever (ADR 0003). Contacts: flagged Members in join order (relationship
-- "Anak", with their distance and sign-in number), then flagged Care Contacts by name.
create function public.emergency_summary(r public.care_recipients) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'name', r.name,
    'born_on', r.born_on,
    'weight_kg', r.weight_kg,
    'allergies', r.allergies,
    'wishes', r.wishes,
    'conditions', r.conditions,
    'blood_thinners', coalesce((
      select jsonb_agg(m.name order by m.time_of_day, m.name)
      from public.medications m where m.recipient_id = r.id and m.active and m.blood_thinner), '[]'),
    'medications', coalesce((
      select jsonb_agg(trim(m.name || ' ' || m.dose) order by m.time_of_day, m.name)
      from public.medications m where m.recipient_id = r.id and m.active), '[]'),
    'contacts', coalesce((
      select jsonb_agg(c order by c.k, c.at, c.name) from (
        select 0 as k, m.created_at as at, coalesce(m.name, '') as name, 'Anak' as relationship, m.distance, '+' || u.phone as phone
        from public.members m join auth.users u on u.id = m.user_id
        where m.circle_id = r.circle_id and m.emergency and m.left_at is null and m.user_id is distinct from r.member_id
          and u.phone is not null and u.phone <> ''
        union all
        select 1, null, c.name, c.relationship, '', c.phone
        from public.care_contacts c where c.circle_id = r.circle_id and c.emergency) c), '[]'))
$$;

-- For the app (and its offline copy): any Member, Data Category aside (ADR 0003).
create function public.emergency_info_of(recipient uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select public.emergency_summary(r) from public.care_recipients r where r.id = recipient and public.is_member(r.circle_id)
$$;

-- The QR page (service role): the same summary, null for a revoked or unknown token.
create or replace function public.emergency_info(token uuid, scanned boolean default true) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare r public.care_recipients;
begin
  if scanned then update public.emergency_cards c set last_scanned_at = now() where c.token = emergency_info.token; end if;
  select cr.* into r from public.emergency_cards c join public.care_recipients cr on cr.id = c.recipient_id
    where c.token = emergency_info.token;
  if not found then return null; end if;
  return public.emergency_summary(r);
end $$;

revoke execute on function public.set_emergency_contact(uuid, uuid, boolean, text), public.emergency_info_of(uuid) from public, anon;
revoke execute on function public.emergency_summary(public.care_recipients) from public, anon, authenticated;
grant execute on function public.set_emergency_contact(uuid, uuid, boolean, text), public.emergency_info_of(uuid) to authenticated;

-- The app reads "Obat saat ini" from emergency_info_of now.
drop function public.emergency_medications(uuid);
