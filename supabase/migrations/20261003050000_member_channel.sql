-- `circle` channel sub (#36): "App + WhatsApp" once a Member has signed in to the app, else "Hanya WhatsApp".
-- Computed field: select `*,in_app` on members.
create function public.in_app(m public.members) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from auth.users u where u.id = m.user_id and u.last_sign_in_at is not null)
$$;
