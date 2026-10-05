-- Subscription (#50, ADR 0006; see CONTEXT.md): one per Care Circle, bought in the app through RevenueCat with the
-- circle's id as RevenueCat's app user id, so any Member may pay. RevenueCat's webhook (the `subscription` Edge
-- Function) writes it here; recording and transcription need it with the transcription add-on.

-- [trial]: in the store's free trial, which ends at [expires_at].
create table public.subscriptions (
  circle_id uuid primary key references public.care_circles (id) on delete cascade,
  transcription boolean not null,
  trial boolean not null,
  expires_at timestamptz not null
);
alter table public.subscriptions enable row level security;
revoke insert, update, delete on public.subscriptions from authenticated;

create policy "members read their subscription" on public.subscriptions
  for select to authenticated using (public.is_member(circle_id));

-- Paid up, with the add-on.
create function public.transcribes(circle uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.subscriptions s where s.circle_id = circle and s.transcription and s.expires_at > now());
$$;

-- Called by `subscription` with RevenueCat's current state of the circle; a null [expires_at]: nothing was ever bought.
create function public.save_subscription(circle uuid, transcription boolean, trial boolean, expires_at timestamptz)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if expires_at is null then delete from public.subscriptions where circle_id = circle; return; end if;
  insert into public.subscriptions values (circle, transcription, trial, expires_at)
    on conflict (circle_id) do update set transcription = excluded.transcription, trial = excluded.trial, expires_at = excluded.expires_at;
end $$;
revoke execute on function public.save_subscription(uuid, boolean, boolean, timestamptz) from public, anon, authenticated;

-- Recording needs it: no job starts (start_recording) and no audio is uploaded without it.
create function public.recording_needs_subscription() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.status = 'processing' and not public.transcribes(new.circle_id) then raise exception 'needs a subscription'; end if;
  return new;
end $$;
create trigger needs_subscription before insert or update of status on public.recordings
  for each row execute function public.recording_needs_subscription();

-- As in 20261005030000_delete_recording.sql, plus the subscription.
drop policy "attendee uploads recording files" on storage.objects;
create policy "attendee uploads recording files" on storage.objects
  for insert to authenticated with check (
    bucket_id = 'recordings' and public.is_attendee((storage.filename(name))::uuid)
    and not exists (select 1 from public.recordings r where r.path = objects.name and r.status <> 'failed')
    and public.transcribes((storage.foldername(name))[1]::uuid));
