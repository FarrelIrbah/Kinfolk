-- Local sample circle, loaded by `npx supabase db reset` (config.toml [db.seed]). Not for production.
-- Sri (admin, +62 812 3456 7890), Budi (+62 812 0000 0001) and Dewi (+62 812 0000 0002) sign in with code 123456
-- (config.toml [auth.sms.test_otp]); Rina is a viewer who never signs in. Dates follow today in Jakarta.

insert into auth.users (instance_id, id, aud, role, phone, phone_confirmed_at, encrypted_password, created_at, updated_at,
  raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token, email_change_token_new, email_change,
  phone_change, phone_change_token, email_change_token_current, reauthentication_token)
select '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated', u.phone, now(), '', now(), now(),
  '{"provider":"phone","providers":["phone"]}', '{}', '', '', '', '', '', '', '', ''
from (values
  ('a0000000-0000-0000-0000-000000000001'::uuid, '6281234567890'),
  ('a0000000-0000-0000-0000-000000000002'::uuid, '6281200000001'),
  ('a0000000-0000-0000-0000-000000000003'::uuid, '6281200000002'),
  ('a0000000-0000-0000-0000-000000000004'::uuid, '6281200000003')) u(id, phone);

insert into auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at)
select gen_random_uuid(), id, id::text, 'phone', jsonb_build_object('sub', id::text, 'phone', phone), now(), now(), now()
from auth.users where id::text like 'a0000000-%';

do $$
declare
  sri constant uuid := 'a0000000-0000-0000-0000-000000000001';
  budi constant uuid := 'a0000000-0000-0000-0000-000000000002';
  dewi constant uuid := 'a0000000-0000-0000-0000-000000000003';
  rina constant uuid := 'a0000000-0000-0000-0000-000000000004';
  today constant date := (now() at time zone 'Asia/Jakarta')::date;
  monday constant date := today - (extract(isodow from today)::int - 1);
  circle uuid;
  tukiman uuid;
  rao uuid;
  visit uuid;
  physio uuid;
  amlo uuid;
  clop uuid;
  omep uuid;
  ator uuid;
  visit_at timestamptz;
begin
  -- As Sri, so defaults like `by = auth.uid()` are hers.
  perform set_config('request.jwt.claims', json_build_object('sub', sri, 'role', 'authenticated')::text, true);

  insert into public.care_circles (name, needs) values ('Tukiman', '{visits,medicines,check_ins,paperwork}') returning id into circle;
  insert into public.members (circle_id, user_id, role, name, created_at) values
    (circle, sri, 'admin', 'Sri', now() - interval '60 days'),
    (circle, budi, 'sibling', 'Budi', now() - interval '59 days'),
    (circle, dewi, 'sibling', 'Dewi', now() - interval '58 days'),
    (circle, rina, 'viewer', 'Rina', now() - interval '57 days');
  insert into public.care_recipients (circle_id, name, relation, allergies, conditions)
    values (circle, 'Tukiman', 'father', 'Penisilin', 'Stroke iskemik (Mei), hipertensi') returning id into tukiman;

  -- Rota: Sri, Budi, Dewi in turn from this Monday.
  with d as (insert into public.duties (circle_id, name, time_of_day, starts_on) values (circle, 'Telepon cek malam', '19:00', monday) returning id)
  insert into public.duty_rotation (duty_id, circle_id, user_id, pos) select d.id, circle, u, o from d, unnest(array[sri, budi, dewi]) with ordinality x(u, o);

  insert into public.providers (circle_id, name) values (circle, 'Dr. Anand Rao') returning id into rao;
  insert into public.providers (circle_id, name) values (circle, 'Fisioterapi Sehat') returning id into physio;
  visit_at := (today + time '16:00') at time zone 'Asia/Jakarta';
  insert into public.appointments (circle_id, recipient_id, provider_id, title, location, starts_at, departs_at, driver_id, attendee_id, bring, created_by, driver_confirmed_at)
    values (circle, tukiman, rao, 'Kontrol neurologi', 'RS Peninsula, Lt. 3', visit_at, visit_at - interval '45 minutes', budi, sri, 'Hasil MRI, daftar obat', sri, now())
    returning id into visit;
  insert into public.appointments (circle_id, recipient_id, provider_id, title, location, starts_at, driver_id, attendee_id, created_by)
    values (circle, tukiman, physio, 'Fisioterapi', 'Klinik Fisioterapi Sehat', ((today + 4) + time '10:00') at time zone 'Asia/Jakarta', dewi, dewi, sri);
  insert into public.questions (circle_id, appointment_id, asked_by, text) values
    (circle, visit, budi, 'Kapan Bapak boleh menyetir lagi?'),
    (circle, visit, sri, 'Boleh berhenti clopidogrel sebelum perawatan gigi?');

  insert into public.medications (circle_id, recipient_id, name, dose, schedule, time_of_day, note) values
    (circle, tukiman, 'Omeprazole', '20 mg', 'sebelum sarapan', '06:30', '') returning id into omep;
  insert into public.medications (circle_id, recipient_id, name, dose, schedule, time_of_day) values
    (circle, tukiman, 'Amlodipine', '5 mg', 'pagi', '07:00') returning id into amlo;
  insert into public.medications (circle_id, recipient_id, name, dose, schedule, time_of_day, blood_thinner, note) values
    (circle, tukiman, 'Clopidogrel', '75 mg', 'pagi', '08:00', true, 'Jangan dihentikan tanpa tanya Dr. Rao.') returning id into clop;
  insert into public.medications (circle_id, recipient_id, name, dose, schedule, time_of_day, refill_on, refill_by) values
    (circle, tukiman, 'Atorvastatin', '20 mg', 'malam', '20:00', today + 4, budi) returning id into ator;
  insert into public.medications (circle_id, recipient_id, name, dose, schedule, time_of_day, active) values
    (circle, tukiman, 'Paracetamol', '500 mg', 'bila perlu', '12:00', false);
  insert into public.dose_logs (circle_id, medication_id, day, given_by)
    select circle, m, today - g, (array[sri, budi, dewi])[1 + g % 3]
    from unnest(array[omep, amlo, clop, ator]) m, generate_series(1, 6) g;

  insert into public.check_ins (circle_id, recipient_id, day, by, sys, dia, ate, walked, mood)
    select circle, tukiman, today - g, (array[sri, budi, dewi])[1 + g % 3], 120 + (g * 7) % 15, 76 + g % 8,
      case when g % 5 = 0 then 'some' else 'yes' end, g % 4 <> 0, case when g % 6 = 0 then 'okay' else 'good' end
    from generate_series(1, 20) g;

  insert into public.tasks (circle_id, recipient_id, text, owner_id, due, source, added_by) values
    (circle, tukiman, 'Perpanjang izin parkir disabilitas', budi, today + 4, 'added', sri),
    (circle, tukiman, 'Jadwalkan MRI ulang', sri, today + 10, 'added', sri),
    (circle, tukiman, 'Beli kursi mandi', dewi, today + 2, 'added', budi),
    (circle, tukiman, 'Tanya dokter gigi soal clopidogrel', sri, today + 6, 'added', dewi),
    (circle, tukiman, 'Ambil isi ulang Atorvastatin', budi, today + 4, 'refill', sri);

  insert into public.expenses (circle_id, recipient_id, what, amount, paid_by, day) values
    (circle, tukiman, 'Biaya MRI', 250000, sri, today - 3),
    (circle, tukiman, 'Alarm jatuh (DP)', 45000, dewi, today - 1),
    (circle, tukiman, 'Biaya fisioterapi ×4', 160000, budi, today - 2);

  insert into public.care_contacts (circle_id, name, relationship, phone, grp, emergency, note) values
    (circle, 'Dr. Anand Rao', 'Dokter saraf', '+62215550100', 'medical', true, 'Minta tolong Maria di resepsionis'),
    (circle, 'Apotek Kimia Farma', 'Apotek', '+62215550142', 'medical', false, ''),
    (circle, 'Bu Ratna', 'Tetangga', '+6281255500119', 'home', false, 'Memegang kunci cadangan rumah');

  insert into public.notes (circle_id, by, text, private) values
    (circle, sri, 'Bapak lebih tenang kalau ditelepon sebelum makan malam.', false);

  -- In the add-on's trial (#50), so "Rekam" opens consent; delete the row to see the paywall.
  insert into public.subscriptions values (circle, true, true, now() + interval '14 days' - interval '1 minute');
end $$;
