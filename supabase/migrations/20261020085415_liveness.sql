-- Selfie liveness verification. Vendor (AWS Rekognition Face Liveness) when configured, else on-device ML Kit
-- gesture challenge + one selfie reviewed by a moderator. No biometric templates are ever stored:
-- we keep only pass/fail, a confidence number, gesture booleans, and (fallback only) one selfie until review (max 30 days).

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('selfies', 'selfies', false, 5242880, array['image/jpeg']) on conflict (id) do nothing;

create policy selfies_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'selfies' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy selfies_select_own_or_admin on storage.objects for select to authenticated
  using (bucket_id = 'selfies' and ((storage.foldername(name))[1] = (select auth.uid())::text or private.is_admin()));
create policy selfies_delete_own_or_admin on storage.objects for delete to authenticated
  using (bucket_id = 'selfies' and ((storage.foldername(name))[1] = (select auth.uid())::text or private.is_admin()));

create table public.liveness_checks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  method text not null check (method in ('on_device','vendor')),
  status text not null default 'started' check (status in ('started','pending_review','passed','failed','approved','rejected','expired')),
  challenge text[] not null default '{}',
  result jsonb not null default '{}'::jsonb,       -- gesture booleans / vendor confidence only; never face templates
  selfie_path text,                                -- fallback only; deleted after review or 30 days
  vendor_session_id text,
  consent_version text not null,
  consent_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id) on delete set null,
  purge_after timestamptz not null default now() + interval '30 days'
);
create index liveness_user_idx on public.liveness_checks(user_id, created_at desc);
create index liveness_pending_idx on public.liveness_checks(status) where status = 'pending_review';
create index liveness_reviewer_idx on public.liveness_checks(reviewed_by);
alter table public.liveness_checks enable row level security;
create policy liveness_select on public.liveness_checks for select to authenticated
  using (user_id = (select auth.uid()) or private.is_admin());
revoke all on public.liveness_checks from anon, authenticated;
grant select (id, user_id, method, status, challenge, created_at, reviewed_at) on public.liveness_checks to authenticated;
-- all writes go through the definer RPCs below or the liveness Edge Function (service role)

-- Start: requires explicit biometric consent; server picks the random gesture challenge; 5 attempts / 24h.
create function private.start_liveness_impl(p_method text, p_consent_version text)
returns table (id uuid, challenge text[]) language plpgsql security definer set search_path = '' as $$
declare me uuid := (select auth.uid()); g text[]; cid uuid;
begin
  if me is null then raise exception 'not signed in'; end if;
  if coalesce(p_consent_version,'') = '' then raise exception 'biometric consent required'; end if;
  if p_method not in ('on_device','vendor') then raise exception 'bad method'; end if;
  if exists (select 1 from public.profiles where profiles.id = me and (is_banned or verification_status = 'verified')) then
    raise exception 'already verified or not allowed'; end if;
  if (select count(*) from public.liveness_checks l where l.user_id = me and l.created_at > now() - interval '24 hours') >= 5 then
    raise exception 'too many attempts, try again tomorrow'; end if;
  select array_agg(x) into g from (select x from unnest(array['turn_left','turn_right','smile','blink']) x order by random() limit 3) s;
  insert into public.liveness_checks(user_id, method, challenge, consent_version) values (me, p_method, g, left(p_consent_version, 40))
    returning liveness_checks.id into cid;
  return query select cid, g;
end $$;

-- On-device submit: gesture results + selfie path -> moderator queue, profile -> pending.
create function private.submit_liveness_impl(p_check uuid, p_result jsonb, p_selfie_path text)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := (select auth.uid()); c public.liveness_checks;
begin
  select * into c from public.liveness_checks where id = p_check and user_id = me for update;
  if not found or c.method <> 'on_device' or c.status <> 'started' then raise exception 'invalid check'; end if;
  if c.created_at < now() - interval '30 minutes' then update public.liveness_checks set status = 'expired' where id = p_check; raise exception 'check expired'; end if;
  if p_selfie_path is null or split_part(p_selfie_path, '/', 1) <> me::text then raise exception 'bad selfie path'; end if;
  -- keep only the allowed keys (booleans per gesture + face count); drop anything else the client sends
  update public.liveness_checks set status = 'pending_review', selfie_path = p_selfie_path,
    result = (select coalesce(jsonb_object_agg(k, v), '{}'::jsonb) from jsonb_each(coalesce(p_result,'{}'::jsonb)) e(k, v)
              where k in ('turn_left','turn_right','smile','blink','single_face') and jsonb_typeof(v) = 'boolean')
  where id = p_check;
  update public.profiles set verification_status = 'pending' where id = me and verification_status in ('unverified','rejected');
end $$;

-- Moderator queue (pending fallback checks) and decision.
create function private.mod_pending_liveness_impl()
returns table (check_id uuid, user_id uuid, display_name text, challenge text[], result jsonb, selfie_path text, created_at timestamptz,
               photos text[]) language plpgsql stable security definer set search_path = '' as $$
begin perform private.require_admin();
  return query select l.id, l.user_id, p.display_name, l.challenge, l.result, l.selfie_path, l.created_at,
    coalesce((select array_agg(ph.storage_path order by ph.position) from public.photos ph where ph.user_id = l.user_id and ph.moderation_status <> 'rejected'), '{}')
  from public.liveness_checks l join public.profiles p on p.id = l.user_id
  where l.status = 'pending_review' order by l.created_at limit 100;
end $$;

-- Decision: verifies/rejects the profile, clears selfie_path (the app deletes the object right after; see purge list).
create function private.mod_review_liveness_impl(p_check uuid, p_ok boolean, p_reason text)
returns text language plpgsql security definer set search_path = '' as $$
declare c public.liveness_checks;
begin perform private.require_admin();
  if not p_ok and coalesce(trim(p_reason),'') = '' then raise exception 'reason required'; end if;
  select * into c from public.liveness_checks where id = p_check and status = 'pending_review' for update;
  if not found then raise exception 'not found'; end if;
  update public.liveness_checks set status = case when p_ok then 'approved' else 'rejected' end, reviewed_at = now(),
    reviewed_by = (select auth.uid()), selfie_path = null where id = p_check;
  update public.profiles set verification_status = case when p_ok then 'verified' else 'rejected' end::public.verification_status,
    moderation_note = case when p_ok then 'You''re verified.' else 'Selfie verification rejected: ' || left(p_reason, 300) end
  where id = c.user_id;
  perform private.log(case when p_ok then 'liveness.approve' else 'liveness.reject' end, c.user_id::text, jsonb_build_object('check', p_check, 'reason', p_reason));
  return c.selfie_path;
end $$;

-- Retention: selfies of checks past purge_after (or already decided) that still exist in storage.
create function private.mod_selfies_to_purge_impl() returns table (path text) language plpgsql stable security definer set search_path = '' as $$
begin perform private.require_admin();
  return query select o.name from storage.objects o
   where o.bucket_id = 'selfies' and not exists (select 1 from public.liveness_checks l where l.selfie_path = o.name and l.purge_after > now())
   limit 500;
end $$;
create function private.expire_liveness() returns void language sql security definer set search_path = '' as $$
  update public.liveness_checks set status = 'expired', selfie_path = null where purge_after <= now() and status in ('started','pending_review') $$;

revoke execute on function private.start_liveness_impl(text,text), private.submit_liveness_impl(uuid,jsonb,text), private.mod_pending_liveness_impl(),
  private.mod_review_liveness_impl(uuid,boolean,text), private.mod_selfies_to_purge_impl(), private.expire_liveness() from public, anon;
grant execute on function private.start_liveness_impl(text,text), private.submit_liveness_impl(uuid,jsonb,text), private.mod_pending_liveness_impl(),
  private.mod_review_liveness_impl(uuid,boolean,text), private.mod_selfies_to_purge_impl() to authenticated;

create function public.start_liveness(p_method text, p_consent_version text) returns table (id uuid, challenge text[])
  language sql volatile security invoker set search_path = '' as $$ select * from private.start_liveness_impl(p_method, p_consent_version) $$;
create function public.submit_liveness(p_check uuid, p_result jsonb, p_selfie_path text) returns void
  language sql volatile security invoker set search_path = '' as $$ select private.submit_liveness_impl(p_check, p_result, p_selfie_path) $$;
create function public.mod_pending_liveness() returns table (check_id uuid, user_id uuid, display_name text, challenge text[], result jsonb, selfie_path text, created_at timestamptz, photos text[])
  language sql stable security invoker set search_path = '' as $$ select * from private.mod_pending_liveness_impl() $$;
create function public.mod_review_liveness(p_check uuid, p_ok boolean, p_reason text default null) returns text
  language sql volatile security invoker set search_path = '' as $$ select private.mod_review_liveness_impl(p_check, p_ok, p_reason) $$;
create function public.mod_selfies_to_purge() returns table (path text)
  language sql stable security invoker set search_path = '' as $$ select * from private.mod_selfies_to_purge_impl() $$;
revoke execute on function public.start_liveness(text,text), public.submit_liveness(uuid,jsonb,text), public.mod_pending_liveness(),
  public.mod_review_liveness(uuid,boolean,text), public.mod_selfies_to_purge() from public, anon;
grant execute on function public.start_liveness(text,text), public.submit_liveness(uuid,jsonb,text), public.mod_pending_liveness(),
  public.mod_review_liveness(uuid,boolean,text), public.mod_selfies_to_purge() to authenticated;

-- Daily expiry of abandoned checks (pg_cron is free on all plans)
create extension if not exists pg_cron;
select cron.schedule('liveness-expire', '17 3 * * *', 'select private.expire_liveness()');
