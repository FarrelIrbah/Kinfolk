-- WhatsApp "T: …" (#39, v3 sms `qM`): a Member's text becomes a Question on their next Appointment, answered
-- "Ditambahkan ke pertanyaan untuk kunjungan Tukiman berikutnya dengan Dr. Anand Rao." (v3 sms `qR`).

-- Returns the free-form reply, or null when [phone] has no Appointment ahead they may add to.
-- ponytail: the soonest Appointment across all their Care Circles; ask which Care Recipient once a Member holds two.
create function public.whatsapp_question(phone text, text text) returns text
language plpgsql security definer set search_path = '' as $$
declare
  member uuid;
  a record;
begin
  select u.id into member from auth.users u where u.phone = regexp_replace(whatsapp_question.phone, '\D', '', 'g');
  select x.id, x.circle_id, r.name as recipient, p.name as provider into a
    from public.appointments x
    join public.care_recipients r on r.id = x.recipient_id
    join public.providers p on p.id = x.provider_id
    join public.members m on m.circle_id = x.circle_id and m.user_id = member and m.left_at is null and m.role <> 'viewer'
    where x.cancelled_at is null and x.starts_at > now() and public.member_sees(member, x.recipient_id, 'appointments')
    order by x.starts_at, x.id limit 1;
  if not found then return null; end if;
  insert into public.questions (circle_id, appointment_id, asked_by, text) values (a.circle_id, a.id, member, whatsapp_question.text);
  return format('Ditambahkan ke pertanyaan untuk kunjungan %s berikutnya dengan %s.', a.recipient, a.provider);
end $$;

revoke execute on function public.whatsapp_question(text, text) from public, anon, authenticated;
