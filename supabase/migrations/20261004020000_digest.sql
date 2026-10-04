-- Weekly digest (#40): Sundays from 18.00 WIB every Member but the Care Recipient gets v3's sms `digest` for the week,
-- Monday to Sunday, unless nothing was logged in it (no Check-in, Dose Log or Visit Note). The lines follow the
-- Member's Data Categories; a line without data, or hidden from them, says so (owner-approved in #40):
--   Kinfolk · Minggu Tukiman, 21–27 Sept
--   • 7 dari 7 telepon malam selesai
--   • Rata-rata tensi 131/83
--   • 27 dari 28 dosis tercatat
--   • Kontrol neurologi: fisioterapi 2x/minggu
--   Berikutnya: Kontrol neurologi Sel 14.30, Budi mengantar.
--   Kosong: telepon cek malam Minggu 4 Okt.
-- Home and "Cara lain" show the last one as it arrived (my_digest).

drop index public.messages_kind_ref_user_id_day_idx;
create unique index on public.messages (kind, ref, user_id, day) where kind in ('drive_reminder', 'duty_reminder', 'dose_reminder', 'digest');

create or replace function public.template_body(template text, params text[]) returns text
language plpgsql immutable set search_path = '' as $$
declare body text := case template
  when 'kinfolk_swap_ask' then '{{1}} bertanya: bisa ambil {{2}} {{3}}? Balas YA atau TIDAK.'
  when 'kinfolk_swap_yes' then '{{1}} pegang {{2}} hari {{3}}.'
  when 'kinfolk_swap_no' then '{{1}} tidak bisa ambil {{2}} hari {{3}}.'
  when 'kinfolk_drive_ask' then '{{1}} bertanya: bisa mengantar {{2}} ke {{3}}, {{4}}? Balas YA atau TIDAK.'
  when 'kinfolk_drive_yes' then '{{1}} mengantar {{2}} {{3}}.'
  when 'kinfolk_drive_no' then '{{1}} tidak bisa mengantar {{2}} {{3}}.'
  when 'kinfolk_drive_reminder' then 'Hari ini: antar {{1}} ke {{2}} jam {{3}}.'
  when 'kinfolk_duty_reminder' then 'Hari ini: {{1}} jam {{2}}.'
  when 'kinfolk_visit_note' then '{{1}} menulis catatan kunjungan {{2}}. {{3}}.'
  when 'kinfolk_bp_high' then 'Tensi {{1}} malam ini {{2}}, 140 ke atas. Dicatat oleh {{3}}.'
  when 'kinfolk_task_reminder' then '{{1}} mengingatkan: {{2}}, tenggat {{3}}.'
  when 'kinfolk_recipient_ok' then '{{1}} baik-baik saja. Dikirim dari Mode {{1}}.'
  when 'kinfolk_recipient_help' then '{{1}} butuh bantuan. {{2}} sedang dihubungi.'
  when 'kinfolk_dose_reminder' then '{{1}}: {{2}} jam {{3}}. Balas 1 jika sudah diberikan.'
  when 'kinfolk_digest' then E'Kinfolk · Minggu {{1}}, {{2}}\n• {{3}}\n• {{4}}\n• {{5}}\n• {{6}}\nBerikutnya: {{7}}.\nKosong: {{8}}.'
end;
begin
  for i in 1..coalesce(array_length(params, 1), 0) loop
    body := replace(body, '{{' || i || '}}', params[i]);
  end loop;
  return body;
end $$;

-- Called by the minute job with queue_reminders. ponytail: only between 18.00 and 19.00, so a job down that hour skips the week.
create function public.queue_digests(at timestamptz) returns void language sql security definer set search_path = '' as $$
  with w as (
    select public.wib(at)::date as sun, public.wib(at)::date - 6 as mon
    where extract(isodow from public.wib(at)) = 7 and public.wib(at)::time >= '18:00' and public.wib(at)::time < '19:00'
  )
  insert into public.messages (user_id, kind, ref, day, template, params)
    select m.user_id, 'digest', r.id, w.sun, 'kinfolk_digest', array[
      r.name,
      case when extract(month from w.mon) = extract(month from w.sun) then extract(day from w.mon) || '–' || public.day_month(w.sun)
        else public.day_month(w.mon) || '–' || public.day_month(w.sun) end,
      k.n || ' dari 7 telepon malam selesai',
      coalesce('Rata-rata tensi ' || k.sys || '/' || k.dia, 'Belum ada tensi minggu ini'),
      case when d.due > 0 and public.member_sees(m.user_id, r.id, 'medications') then d.given || ' dari ' || d.due || ' dosis tercatat'
        else 'Belum ada dosis tercatat' end,
      coalesce(case when public.member_sees(m.user_id, r.id, 'appointments') and public.member_sees(m.user_id, r.id, 'visit_notes') then v.text end,
        'Belum ada catatan kunjungan'),
      coalesce(case when public.member_sees(m.user_id, r.id, 'appointments') then x.text end, 'belum ada janji'),
      coalesce(o.text, 'semua giliran terisi')]
    from w
    cross join public.care_recipients r
    join public.members m on m.circle_id = r.circle_id and m.left_at is null and m.user_id is distinct from r.member_id
    cross join lateral (select count(*) as n, round(avg(c.sys))::int as sys, round(avg(c.dia))::int as dia
      from public.check_ins c where c.recipient_id = r.id and c.day between w.mon and w.sun) k
    cross join lateral (select count(*) * 7 as due, (select count(*) from public.dose_logs l join public.medications x on x.id = l.medication_id
        where x.recipient_id = r.id and x.active and l.day between w.mon and w.sun) as given
      from public.medications md where md.recipient_id = r.id and md.active) d
    -- The Timeline's text (#8), as tell_visit_note.
    left join lateral (select public.one_line(a.title || case
        when cardinality(n.next_steps) > 0 then ': ' || array_to_string(n.next_steps, ', ')
        when n.notes <> '' then ': ' || n.notes
        else '' end) as text
      from public.visit_notes n join public.appointments a on a.id = n.appointment_id
      where a.recipient_id = r.id and a.cancelled_at is null and public.wib(n.updated_at)::date between w.mon and w.sun
      order by n.updated_at desc limit 1) v on true
    -- "Kontrol neurologi Sel 14.30, Budi mengantar"; the date too when it is not next week.
    left join lateral (select public.one_line(a.title || ' ' || (array['Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab', 'Min'])[extract(isodow from public.wib(a.starts_at))]
        || case when public.wib(a.starts_at)::date > w.sun + 7 then ' ' || public.day_month(public.wib(a.starts_at)::date) else '' end
        || ' ' || public.hm(public.wib(a.starts_at)::time)
        || coalesce(', ' || public.member_name(a.circle_id, a.driver_id) || ' mengantar', '')) as text
      from public.appointments a
      where a.recipient_id = r.id and a.cancelled_at is null and a.starts_at > at
      order by a.starts_at limit 1) x on true
    -- The first open day next week: a swap asked and not yet answered, as invitee's (#23).
    left join lateral (select public.in_sentence(dd.name) || ' ' || public.day_label(s.day) as text
      from public.duty_swaps s join public.duties dd on dd.id = s.duty_id
      where s.circle_id = r.circle_id and s.day between w.sun + 1 and w.sun + 7 and s.id = (public.open_swap(s.duty_id, s.day)).id
      order by s.day, dd.time_of_day limit 1) o on true
    where k.n > 0 or d.given > 0 or exists (select 1 from public.visit_notes n join public.appointments a on a.id = n.appointment_id
      where a.recipient_id = r.id and a.cancelled_at is null and public.wib(n.updated_at)::date between w.mon and w.sun)
  on conflict do nothing;
$$;

-- The last digest sent to me in [circle], as it arrived: Home's "Ringkasan hari Minggu" and "Cara lain" (#40).
create function public.my_digest(circle uuid) returns text language sql stable security definer set search_path = '' as $$
  select public.template_body(m.template, m.params) from public.messages m
  join public.care_recipients r on r.id = m.ref and r.circle_id = circle
  where m.user_id = auth.uid() and m.kind = 'digest' and m.sent_at is not null
  order by m.day desc, m.created_at desc, m.id limit 1
$$;

revoke execute on function public.queue_digests(timestamptz), public.my_digest(uuid) from public, anon, authenticated;
grant execute on function public.my_digest(uuid) to authenticated;
