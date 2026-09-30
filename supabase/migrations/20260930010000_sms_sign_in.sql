-- The Auth send-SMS hook doesn't receive the channel the app asked for, so "Kirim lewat SMS" is recorded
-- here first and the send-otp Edge Function reads it. Resends within 10 minutes stay on SMS.

create table public.sms_sign_in_requests (
  phone text primary key, -- digits only, as Auth stores it (6281...)
  requested_at timestamptz not null default now()
);
alter table public.sms_sign_in_requests enable row level security; -- no policies: only the functions below and the service role

-- ponytail: anyone can switch a number's next sign-in code to SMS for 10 minutes; Auth's per-number send
-- limit still applies. Tie it to a pending OTP if SMS pumping shows up.
create function public.request_sms_sign_in(phone text) returns void
language sql security definer set search_path = '' as $$
  insert into public.sms_sign_in_requests (phone) values (regexp_replace(phone, '\D', '', 'g'))
  on conflict (phone) do update set requested_at = now();
$$;
grant execute on function public.request_sms_sign_in(text) to anon, authenticated;

create function public.sign_in_via_sms(phone text) returns boolean
language sql security definer set search_path = '' as $$
  select exists (select 1 from public.sms_sign_in_requests r
    where r.phone = regexp_replace(sign_in_via_sms.phone, '\D', '', 'g') and r.requested_at > now() - interval '10 minutes');
$$;
revoke execute on function public.sign_in_via_sms(text) from public, anon, authenticated;
