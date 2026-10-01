-- Document (#29, see CONTEXT.md): v3's Records › Dokumen. A PDF or photo about the Care Recipient, in the private
-- `documents` Storage bucket under a folder per Care Circle. Each row is one version; re-uploading the same name
-- (ignoring case) makes the next one. Legal ones fall under Keinginan & hukum, the rest under Dokumen.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('documents', 'documents', false, 20971520, array['application/pdf', 'image/jpeg', 'image/png']);

create table public.documents (
  id uuid primary key, -- chosen by the uploader, who stores the file at `path` first
  circle_id uuid not null,
  recipient_id uuid not null,
  name text not null check (trim(name) <> ''), -- "Laporan MRI otak"
  ext text not null check (ext in ('PDF', 'JPG', 'PNG')),
  legal boolean not null default false,
  version int not null default 1, -- set by number_document
  path text generated always as (circle_id::text || '/' || id::text || '.' || lower(ext)) stored,
  by uuid not null default auth.uid(),
  at timestamptz not null default now(),
  foreign key (circle_id, recipient_id) references public.care_recipients (circle_id, id) on delete cascade,
  foreign key (circle_id, by) references public.members (circle_id, user_id)
);
-- A legal and a non-legal Document may share a name: separate chains, so nobody learns of a version they can't see.
create unique index on public.documents (recipient_id, lower(trim(name)), legal, version);
create index on public.documents (circle_id, at);
alter table public.documents enable row level security;

create function public.number_document() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  new.name := trim(new.name);
  select coalesce(max(d.version), 0) + 1 into new.version from public.documents d
  where d.recipient_id = new.recipient_id and lower(d.name) = lower(new.name) and d.legal = new.legal;
  return new;
end $$;
-- ponytail: two same-name uploads at the same instant collide on the index; the loser retries.
create trigger number_document before insert on public.documents for each row execute function public.number_document();
revoke execute on function public.number_document() from public, anon, authenticated;

create policy "members read documents" on public.documents
  for select to authenticated using (
    public.is_member(circle_id) and public.sees(recipient_id, case when legal then 'wishes' else 'documents' end::public.data_category));
-- No edit or delete: a change is a new version.
create policy "members upload documents" on public.documents
  for insert to authenticated with check (
    public.is_writer(circle_id) and by = auth.uid()
    and public.sees(recipient_id, case when legal then 'wishes' else 'documents' end::public.data_category));

-- The file follows its row: readable by whoever reads the Document. Writers put files in their own circle's folder.
-- ponytail: a file whose row never got saved stays unreadable in Storage; sweep them if space matters.
create policy "members read document files" on storage.objects
  for select to authenticated using (
    bucket_id = 'documents' and exists (select 1 from public.documents d where d.path = objects.name));
create policy "members upload document files" on storage.objects
  for insert to authenticated with check (
    bucket_id = 'documents' and public.is_writer(((storage.foldername(name))[1])::uuid));

-- Uploads join the Timeline as Dokumen entries, v3's text: "Mengunggah laporan MRI otak (versi 2)."
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
  left join public.members m on m.circle_id = d.circle_id and m.user_id = d.by;
