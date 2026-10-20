-- Moderation: all actions are security-definer RPCs in the private schema that check private.is_admin()
-- and write private.audit_log. Public wrappers are security invoker. No client-side trust.
alter table public.profiles add column moderation_note text check (char_length(moderation_note) <= 500),
  add column warnings int not null default 0;
alter table public.photos add column rejection_reason text check (char_length(rejection_reason) <= 500);
-- moderation_note / warnings are NOT in the column SELECT grant; owners read them via my_moderation_status().

create function private.require_admin() returns void language plpgsql stable security definer set search_path = '' as
$$ begin if not private.is_admin() then raise exception 'forbidden' using errcode = '42501'; end if; end $$;
create function private.log(p_action text, p_target text, p_detail jsonb) returns void language sql security definer set search_path = '' as
$$ insert into private.audit_log(actor_id, action, target, detail) values ((select auth.uid()), p_action, p_target, p_detail) $$;

create function private.mod_pending_profiles_impl()
returns table (id uuid, display_name text, age int, gender text, bio text, city text, created_at timestamptz, photos text[], warnings int)
language plpgsql stable security definer set search_path = '' as
$$ begin perform private.require_admin();
  return query select p.id, p.display_name, extract(year from age(p.birthdate))::int, p.gender, p.bio, p.city, p.created_at,
    coalesce((select array_agg(ph.storage_path order by ph.position, ph.created_at) from public.photos ph where ph.user_id = p.id and ph.moderation_status <> 'rejected'), '{}'),
    p.warnings
  from public.profiles p where p.verification_status in ('unverified','pending') and not p.is_banned order by p.created_at limit 100;
end $$;

create function private.mod_set_verification_impl(p_user uuid, p_verified boolean, p_reason text)
returns void language plpgsql security definer set search_path = '' as
$$ begin perform private.require_admin();
  if not p_verified and coalesce(trim(p_reason),'') = '' then raise exception 'reason required'; end if;
  update public.profiles set verification_status = case when p_verified then 'verified' else 'rejected' end::public.verification_status,
    moderation_note = case when p_verified then 'Your profile was verified.' else 'Verification rejected: ' || left(p_reason, 400) end
  where id = p_user;
  if not found then raise exception 'not found'; end if;
  perform private.log(case when p_verified then 'profile.verify' else 'profile.reject' end, p_user::text, jsonb_build_object('reason', p_reason));
end $$;

create function private.mod_pending_photos_impl()
returns table (photo_id uuid, user_id uuid, display_name text, storage_path text, created_at timestamptz)
language plpgsql stable security definer set search_path = '' as
$$ begin perform private.require_admin();
  return query select ph.id, ph.user_id, p.display_name, ph.storage_path, ph.created_at
  from public.photos ph join public.profiles p on p.id = ph.user_id where ph.moderation_status = 'pending' order by ph.created_at limit 200;
end $$;

create function private.mod_review_photo_impl(p_photo uuid, p_approve boolean, p_reason text)
returns void language plpgsql security definer set search_path = '' as
$$ begin perform private.require_admin();
  if not p_approve and coalesce(trim(p_reason),'') = '' then raise exception 'reason required'; end if;
  update public.photos set moderation_status = case when p_approve then 'approved' else 'rejected' end,
    rejection_reason = case when p_approve then null else left(p_reason, 500) end where id = p_photo;
  if not found then raise exception 'not found'; end if;
  perform private.log(case when p_approve then 'photo.approve' else 'photo.reject' end, p_photo::text, jsonb_build_object('reason', p_reason));
end $$;

create function private.mod_reports_impl(p_include_closed boolean)
returns table (id uuid, reporter_id uuid, reported_id uuid, reason text, details text, status public.report_status, snapshot jsonb,
               created_at timestamptz, reported_name text, reported_banned boolean, reported_warnings int)
language plpgsql stable security definer set search_path = '' as
$$ begin perform private.require_admin();
  return query select r.id, r.reporter_id, r.reported_id, r.reason, r.details, r.status, r.reported_snapshot, r.created_at,
    p.display_name, p.is_banned, p.warnings
  from public.reports r left join public.profiles p on p.id = r.reported_id
  where p_include_closed or r.status in ('open','reviewing') order by r.created_at limit 200;
end $$;

create function private.mod_set_ban_impl(p_user uuid, p_banned boolean, p_reason text)
returns void language plpgsql security definer set search_path = '' as
$$ begin perform private.require_admin();
  if p_user = (select auth.uid()) then raise exception 'cannot ban yourself'; end if;
  update public.profiles set is_banned = p_banned,
    moderation_note = case when p_banned then 'Your account was suspended: ' || left(coalesce(p_reason,'policy violation'), 400) else 'Your account was reinstated.' end
  where id = p_user;
  if not found then raise exception 'not found'; end if;
  perform private.log(case when p_banned then 'user.ban' else 'user.unban' end, p_user::text, jsonb_build_object('reason', p_reason));
end $$;

create function private.mod_resolve_report_impl(p_report uuid, p_action text, p_note text)
returns void language plpgsql security definer set search_path = '' as
$$ declare v_user uuid;
begin perform private.require_admin();
  if p_action not in ('dismiss','warn','ban') then raise exception 'bad action'; end if;
  select reported_id into v_user from public.reports where id = p_report;
  if not found then raise exception 'not found'; end if;
  if p_action in ('warn','ban') and v_user is null then raise exception 'reported account no longer exists'; end if;
  if p_action = 'warn' then
    update public.profiles set warnings = warnings + 1,
      moderation_note = 'Warning from moderators: ' || left(coalesce(nullif(trim(p_note),''),'please follow the community guidelines'), 400) where id = v_user;
  elsif p_action = 'ban' then
    perform private.mod_set_ban_impl(v_user, true, coalesce(p_note, 'report'));
  end if;
  update public.reports set status = case when p_action = 'dismiss' then 'dismissed' else 'actioned' end::public.report_status where id = p_report;
  perform private.log('report.' || p_action, p_report::text, jsonb_build_object('user', v_user, 'note', p_note));
end $$;

-- Owner-only status for in-app notice.
create function private.my_moderation_status_impl()
returns table (verification_status public.verification_status, is_banned boolean, warnings int, note text)
language sql stable security definer set search_path = '' as
$$ select verification_status, is_banned, warnings, moderation_note from public.profiles where id = (select auth.uid()) $$;

revoke execute on all functions in schema private from public, anon;
grant execute on function private.require_admin(), private.mod_pending_profiles_impl(), private.mod_set_verification_impl(uuid,boolean,text),
  private.mod_pending_photos_impl(), private.mod_review_photo_impl(uuid,boolean,text), private.mod_reports_impl(boolean),
  private.mod_set_ban_impl(uuid,boolean,text), private.mod_resolve_report_impl(uuid,text,text), private.my_moderation_status_impl() to authenticated;
-- log() is only callable from inside the definer functions.
revoke execute on function private.log(text,text,jsonb) from authenticated;

-- Public invoker wrappers.
create function public.is_admin() returns boolean language sql stable security invoker set search_path = '' as $$ select private.is_admin() $$;
create function public.mod_pending_profiles() returns table (id uuid, display_name text, age int, gender text, bio text, city text, created_at timestamptz, photos text[], warnings int)
  language sql stable security invoker set search_path = '' as $$ select * from private.mod_pending_profiles_impl() $$;
create function public.mod_set_verification(p_user uuid, p_verified boolean, p_reason text default null) returns void
  language sql security invoker set search_path = '' as $$ select private.mod_set_verification_impl(p_user, p_verified, p_reason) $$;
create function public.mod_pending_photos() returns table (photo_id uuid, user_id uuid, display_name text, storage_path text, created_at timestamptz)
  language sql stable security invoker set search_path = '' as $$ select * from private.mod_pending_photos_impl() $$;
create function public.mod_review_photo(p_photo uuid, p_approve boolean, p_reason text default null) returns void
  language sql security invoker set search_path = '' as $$ select private.mod_review_photo_impl(p_photo, p_approve, p_reason) $$;
create function public.mod_reports(p_include_closed boolean default false) returns table (id uuid, reporter_id uuid, reported_id uuid, reason text, details text, status public.report_status, snapshot jsonb, created_at timestamptz, reported_name text, reported_banned boolean, reported_warnings int)
  language sql stable security invoker set search_path = '' as $$ select * from private.mod_reports_impl(p_include_closed) $$;
create function public.mod_set_ban(p_user uuid, p_banned boolean, p_reason text default null) returns void
  language sql security invoker set search_path = '' as $$ select private.mod_set_ban_impl(p_user, p_banned, p_reason) $$;
create function public.mod_resolve_report(p_report uuid, p_action text, p_note text default null) returns void
  language sql security invoker set search_path = '' as $$ select private.mod_resolve_report_impl(p_report, p_action, p_note) $$;
create function public.my_moderation_status() returns table (verification_status public.verification_status, is_banned boolean, warnings int, note text)
  language sql stable security invoker set search_path = '' as $$ select * from private.my_moderation_status_impl() $$;
revoke execute on function public.is_admin(), public.mod_pending_profiles(), public.mod_set_verification(uuid,boolean,text), public.mod_pending_photos(),
  public.mod_review_photo(uuid,boolean,text), public.mod_reports(boolean), public.mod_set_ban(uuid,boolean,text), public.mod_resolve_report(uuid,text,text),
  public.my_moderation_status() from public, anon;
grant execute on function public.is_admin(), public.mod_pending_profiles(), public.mod_set_verification(uuid,boolean,text), public.mod_pending_photos(),
  public.mod_review_photo(uuid,boolean,text), public.mod_reports(boolean), public.mod_set_ban(uuid,boolean,text), public.mod_resolve_report(uuid,text,text),
  public.my_moderation_status() to authenticated;

-- Moderators can view any photo object (to review pending ones) via signed URLs.
create policy photos_obj_admin_select on storage.objects for select to authenticated using (bucket_id = 'photos' and private.is_admin());
