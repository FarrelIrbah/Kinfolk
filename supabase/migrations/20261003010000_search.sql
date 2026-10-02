-- v3 `search` (#32): Postgres full-text over the Care Circle, each typed word a prefix ("clop" finds Clopidogrel).
-- Security invoker, so every table's RLS, Data Categories included, decides what can be found. Hits come in v3's
-- order of kinds (Obat, Dokumen, Linimasa, Kontak, Tugas), at most 14; the app draws them from its own reads.
-- `entry`, `at` and `text` pick out a Timeline entry (doses marked together share `at`), `id` its Appointment.

create function public.search(circle uuid, query text)
returns table (kind text, id uuid, entry text, at timestamptz, text text)
language sql stable set search_path = '' as $$
  with q as (
    -- The parser's own words, so "2.5" and "120/80" stay whole as they do in the text searched.
    select to_tsquery('simple', string_agg(quote_literal(w.lexeme) || ':*', ' & ')) as q
    from unnest(to_tsvector('simple', query)) w
    where length(trim(query)) >= 2
  ), hits as (
    select 1 as k, 'medication' as kind, m.id, null as entry, null::timestamptz as at, null as text,
      row_number() over (order by m.time_of_day, m.name) as n
    from public.medications m, q
    where m.circle_id = circle and to_tsvector('simple', concat_ws(' ', m.name, m.dose, m.schedule,
      case when m.blood_thinner then 'Pengencer darah' end)) @@ q.q
  union all
    -- The newest version of each Document, as Dokumen lists them.
    select 2, 'document', d.id, null, null, null, row_number() over (order by d.at desc)
    from (select distinct on (lower(name), legal) * from public.documents
          where circle_id = circle order by lower(name), legal, at desc) d, q
    where to_tsvector('simple', d.name) @@ q.q
  union all
    select 3, 'timeline', t.appointment_id, t.kind, t.at, t.text, row_number() over (order by t.at desc)
    from public.timeline t, q
    where t.circle_id = circle and to_tsvector('simple', concat_ws(' ', t.title, t.provider, t.text, t.notes,
      array_to_string(t.next_steps, ' '), t.by_name)) @@ q.q
  union all
    select 4, 'contact', c.id, null, null, null, row_number() over (order by c.name)
    from public.care_contacts c, q
    where c.circle_id = circle and to_tsvector('simple', concat_ws(' ', c.name, c.relationship)) @@ q.q
  union all
    select 5, 'task', t.id, null, null, null, row_number() over (order by t.due, t.text)
    from public.tasks t
    left join public.members o on o.circle_id = t.circle_id and o.user_id = t.owner_id, q
    where t.circle_id = circle and to_tsvector('simple', concat_ws(' ', t.text, o.name)) @@ q.q
  )
  select kind, id, entry, at, text from hits order by k, n limit 14
$$;

revoke execute on function public.search(uuid, text) from public, anon;
grant execute on function public.search(uuid, text) to authenticated;
