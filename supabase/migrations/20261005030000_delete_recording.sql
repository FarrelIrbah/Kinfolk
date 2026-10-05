-- "Hapus rekaman" (#48, owner-approved deviation): after sharing, the Attendee who recorded deletes the audio from
-- Storage; the transcript and summary stay. [audio_deleted_at] hides the link and keeps the audio from coming back.

alter table public.recordings add column audio_deleted_at timestamptz;

-- As in 20261005000000_recordings.sql, but no new upload once a job is running or done (as for replacing), so a
-- deleted audio stays deleted.
drop policy "attendee uploads recording files" on storage.objects;
create policy "attendee uploads recording files" on storage.objects
  for insert to authenticated with check (
    bucket_id = 'recordings' and public.is_attendee((storage.filename(name))::uuid)
    and not exists (select 1 from public.recordings r where r.path = objects.name and r.status <> 'failed'));

create policy "recorder deletes shared recording files" on storage.objects
  for delete to authenticated using (
    bucket_id = 'recordings'
    and exists (select 1 from public.recordings r where r.path = objects.name and r.recorded_by = auth.uid() and r.shared_at is not null));

-- Called once the file is removed through the Storage API (which owns the file itself); refuses while it is still there.
create function public.recording_audio_deleted(appointment uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.recordings r set audio_deleted_at = coalesce(r.audio_deleted_at, now())
    where r.appointment_id = appointment and r.recorded_by = auth.uid() and r.shared_at is not null
      and not exists (select 1 from storage.objects o where o.bucket_id = 'recordings' and o.name = r.path);
  if not found then raise exception 'audio not deleted'; end if;
end $$;

revoke execute on function public.recording_audio_deleted(uuid) from public, anon;
grant execute on function public.recording_audio_deleted(uuid) to authenticated;
