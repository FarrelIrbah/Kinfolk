-- Export (#33, see CONTEXT.md): v3's `export`, one PDF for a new doctor built by the export Edge Function from what
-- the exporting Member may see. Anyone with the link can download it for 7 days; every export joins the Timeline.

-- A Document's page count, read on the phone when it is uploaded, so `export` can count pages before building.
-- ponytail: Documents uploaded before this count as 1 page; the PDF holds all their pages either way.
alter table public.documents add column pages int not null default 1 check (pages > 0);

insert into storage.buckets (id, name, public, allowed_mime_types)
values ('exports', 'exports', false, array['application/pdf']);

create table public.exports (
  id uuid primary key, -- chosen by the Edge Function, which stores the file at `path` first
  circle_id uuid not null,
  recipient_id uuid not null,
  prepared_for text not null default '', -- "Disiapkan untuk": "Dr. Lestari, kardiologi"
  pages int not null check (pages > 0),
  token uuid not null unique default gen_random_uuid(), -- the link's secret
  by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days',
  path text generated always as (circle_id::text || '/' || id::text || '.pdf') stored,
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
create index on public.exports (circle_id, at);
alter table public.exports enable row level security;
revoke insert, update, delete on public.exports from authenticated;
create policy "members read exports" on public.exports for select to authenticated using (public.is_member(circle_id));
-- Only the Edge Function (service role) touches the files.

-- What goes in the PDF, as the caller sees it (RLS and Data Categories apply): null when they can't see [recipient].
create function public.export_data(recipient uuid, docs uuid[]) returns jsonb
language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
    'circle_id', r.circle_id,
    'name', r.name,
    'conditions', r.conditions,
    'allergies', r.allergies,
    'contacts', coalesce((
      select jsonb_agg(jsonb_build_object('name', c.name, 'relationship', c.relationship, 'phone', c.phone) order by c.name)
      from public.care_contacts c where c.circle_id = r.circle_id and c.emergency), '[]'),
    'medications', coalesce((
      select jsonb_agg(jsonb_build_object('name', m.name, 'dose', m.dose, 'schedule', m.schedule, 'note', m.note,
        'blood_thinner', m.blood_thinner, 'active', m.active) order by not m.active, m.time_of_day, m.name)
      from public.medications m where m.recipient_id = r.id), '[]'),
    'check_ins', coalesce((
      select jsonb_agg(jsonb_build_object('day', k.day, 'sys', k.sys, 'dia', k.dia, 'ate', k.ate, 'walked', k.walked, 'mood', k.mood) order by k.day)
      from (select * from public.check_ins k where k.recipient_id = r.id order by k.day desc limit 30) k), '[]'),
    'visits', coalesce((
      select jsonb_agg(v order by v->>'starts_at' desc) from (
        select jsonb_build_object('title', a.title, 'provider', p.name, 'starts_at', a.starts_at, 'notes', n.notes,
          'steps', coalesce((select jsonb_agg(t.text order by t.position) from public.tasks t where t.appointment_id = n.appointment_id), '[]')) v
        from public.visit_notes n
        join public.appointments a on a.id = n.appointment_id and a.cancelled_at is null
        join public.providers p on p.id = a.provider_id
        where a.recipient_id = r.id order by a.starts_at desc limit 3) x), '[]'),
    'documents', coalesce((
      select jsonb_agg(jsonb_build_object('name', d.name, 'ext', d.ext, 'path', d.path) order by array_position(docs, d.id))
      from public.documents d where d.recipient_id = r.id and d.id = any(docs) and not d.legal), '[]'))
  from public.care_recipients r where r.id = recipient
$$;
revoke execute on function public.export_data(uuid, uuid[]) from public, anon;
grant execute on function public.export_data(uuid, uuid[]) to authenticated;

-- The Edge Function logs the export as the caller once its file is stored; returns the link's token.
create function public.log_export(id uuid, recipient uuid, prepared_for text, pages int) returns uuid
language plpgsql security definer set search_path = '' as $$
declare circle uuid; token uuid;
begin
  select r.circle_id into circle from public.care_recipients r where r.id = recipient;
  if circle is null or not public.is_member(circle) then raise exception 'not a member'; end if;
  insert into public.exports (id, circle_id, recipient_id, prepared_for, pages)
    values (log_export.id, circle, recipient, trim(log_export.prepared_for), log_export.pages)
    returning exports.token into token;
  return token;
end $$;
revoke execute on function public.log_export(uuid, uuid, text, int) from public, anon;
grant execute on function public.log_export(uuid, uuid, text, int) to authenticated;

-- The file behind a link, null once it has expired or for an unknown token. Service role only.
create function public.export_file(token uuid) returns text
language sql stable security definer set search_path = '' as $$
  select e.path from public.exports e where e.token = export_file.token and e.expires_at > now()
$$;
revoke execute on function public.export_file(uuid) from public, anon, authenticated;

-- Exports join the Timeline as Dokumen entries, v3's text: "Mengekspor PDF 6 halaman untuk Dr. Lestari, kardiologi.
-- Tautan berlaku 7 hari." Without "Disiapkan untuk", the "untuk …" part is left out.
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
  left join public.members m on m.circle_id = c.circle_id and m.user_id = c.by
union all
  select 'dose_given', null, d.circle_id, d.given_by, m.name, d.at, null, null, null, '{}', '',
    trim(md.name || ' ' || md.dose) || ' diberikan.'
  from public.dose_logs d
  join public.medications md on md.id = d.medication_id
  left join public.members m on m.circle_id = d.circle_id and m.user_id = d.given_by
union all
  select 'check_in', null, k.circle_id, k.by, m.name, k.at, null, null, null, '{}', '',
    'Telepon malam: tensi ' || k.sys || '/' || k.dia || ', '
    || case k.ate when 'yes' then 'sudah makan malam' when 'some' then 'makan sedikit' else 'tidak makan malam' end || ', '
    || case when k.walked then 'berjalan' else 'tidak berjalan' end || ', '
    || case k.mood when 'good' then 'suasana hati baik' when 'okay' then 'biasa saja' else 'murung' end || '.'
    || case when trim(k.note) <> '' then ' ' || trim(k.note) else '' end
  from public.check_ins k
  left join public.members m on m.circle_id = k.circle_id and m.user_id = k.by
union all
  select 'document', null, d.circle_id, d.by, m.name, d.at, null, null, null, '{}', '',
    -- The first letter lowered ("laporan MRI otak"), unless an acronym starts it ("MRI otak", "USG perut").
    'Mengunggah ' || case when substr(d.name, 2, 1) <> lower(substr(d.name, 2, 1)) then d.name
      else lower(left(d.name, 1)) || substr(d.name, 2) end
    || case when d.version > 1 then ' (versi ' || d.version || ')' else '' end || '.'
  from public.documents d
  left join public.members m on m.circle_id = d.circle_id and m.user_id = d.by
union all
  select 'export', null, e.circle_id, e.by, m.name, e.at, null, null, null, '{}', '',
    'Mengekspor PDF ' || e.pages || ' halaman' || case when e.prepared_for <> '' then ' untuk ' || e.prepared_for else '' end
    || '. Tautan berlaku 7 hari.'
  from public.exports e
  left join public.members m on m.circle_id = e.circle_id and m.user_id = e.by;
