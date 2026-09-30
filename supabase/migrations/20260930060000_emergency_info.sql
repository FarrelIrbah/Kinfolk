-- Emergency Info (see CONTEXT.md, ADR 0003): a fixed summary of a Care Recipient that anyone holding the QR card's
-- link can open without login. It skips Data Category on purpose, so it only ever returns the fields below.

alter table public.care_recipients
  add column allergies text not null default '', -- "Penisilin"
  add column conditions text not null default ''; -- "Stroke iskemik, April 2026. …"

-- One card per Care Recipient. Members read it only through emergency_card; revoking swaps the token.
create table public.emergency_cards (
  recipient_id uuid primary key references public.care_recipients on delete cascade,
  token uuid not null unique default gen_random_uuid(), -- the link's secret
  version int not null default 1, -- "Tautan kartu v2"
  last_scanned_at timestamptz -- "terakhir dipindai"
);
alter table public.emergency_cards enable row level security;
revoke all on public.emergency_cards from anon, authenticated;

-- Any Member: the card to show and share, made the first time someone asks.
create function public.emergency_card(recipient uuid) returns public.emergency_cards
language plpgsql security definer set search_path = '' as $$
declare card public.emergency_cards;
begin
  if not exists (select 1 from public.care_recipients r where r.id = recipient and public.is_member(r.circle_id)) then
    raise exception 'not a member';
  end if;
  insert into public.emergency_cards (recipient_id) values (recipient) on conflict do nothing;
  select * into card from public.emergency_cards where recipient_id = recipient;
  return card;
end $$;

-- Admins only: the old link stops working at once.
create function public.reissue_emergency_card(recipient uuid) returns public.emergency_cards
language plpgsql security definer set search_path = '' as $$
declare card public.emergency_cards;
begin
  if not exists (select 1 from public.care_recipients r where r.id = recipient and public.is_admin(r.circle_id)) then
    raise exception 'only an admin can revoke';
  end if;
  insert into public.emergency_cards (recipient_id) values (recipient)
    on conflict (recipient_id) do update
    set token = gen_random_uuid(), version = public.emergency_cards.version + 1, last_scanned_at = null
    returning * into card;
  return card;
end $$;

-- What the emergency Edge Function renders, null for a revoked or unknown token. Service role only. [scanned] is false
-- for link-preview bots, so sharing the link doesn't count as a scan. Never add a field without revisiting ADR 0003.
create function public.emergency_info(token uuid, scanned boolean default true) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare r public.care_recipients;
begin
  if scanned then update public.emergency_cards c set last_scanned_at = now() where c.token = emergency_info.token; end if;
  select cr.* into r from public.emergency_cards c join public.care_recipients cr on cr.id = c.recipient_id
    where c.token = emergency_info.token;
  if not found then return null; end if;
  return jsonb_build_object(
    'name', r.name,
    'allergies', r.allergies,
    'conditions', r.conditions,
    'medications', coalesce((
      select jsonb_agg(trim(m.name || ' ' || m.dose) order by m.time_of_day, m.name)
      from public.medications m where m.recipient_id = r.id and m.active), '[]'),
    'contacts', coalesce((
      select jsonb_agg(jsonb_build_object('name', c.name, 'relationship', c.relationship, 'phone', c.phone) order by c.name)
      from public.care_contacts c where c.circle_id = r.circle_id and c.emergency), '[]'));
end $$;

revoke execute on function public.emergency_card(uuid) from public, anon;
revoke execute on function public.reissue_emergency_card(uuid) from public, anon;
revoke execute on function public.emergency_info(uuid, boolean) from public, anon, authenticated;
grant execute on function public.emergency_card(uuid) to authenticated;
grant execute on function public.reissue_emergency_card(uuid) to authenticated;
