-- Victorious+ : convenience features only (who liked you, rewind, extra filters). Never affects ranking.
create table public.entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  product_id text not null,
  status text not null check (status in ('active','grace','on_hold','paused','canceled','expired','revoked')),
  expires_at timestamptz,
  auto_renewing boolean not null default false,
  purchase_token text unique,           -- server-only (column grant below)
  order_id text,                        -- server-only
  updated_at timestamptz not null default now()
);
alter table public.entitlements enable row level security;
create policy entitlements_select_own on public.entitlements for select to authenticated using (user_id = (select auth.uid()));
revoke all on public.entitlements from anon, authenticated;
grant select (user_id, product_id, status, expires_at, auto_renewing, updated_at) on public.entitlements to authenticated;
-- writes only from the play-verify Edge Function (service role)

create function private.has_plus(p_user uuid) returns boolean language sql stable security definer set search_path = '' as
$$ select exists (select 1 from public.entitlements e where e.user_id = p_user
     and e.status in ('active','grace','canceled') and (e.expires_at is null or e.expires_at > now())) $$;
revoke execute on function private.has_plus(uuid) from public, anon;
grant execute on function private.has_plus(uuid) to authenticated;

create function public.my_plus() returns boolean language sql stable security invoker set search_path = '' as
$$ select private.has_plus((select auth.uid())) $$;
revoke execute on function public.my_plus() from public, anon;
grant execute on function public.my_plus() to authenticated;

-- Extra filters (stored for everyone, honoured only while Plus is active)
alter table public.preferences add column only_with_voice boolean not null default false,
                               add column only_with_prompts boolean not null default false;

create or replace function private.discover_impl(p_limit int)
returns table (id uuid, display_name text, age int, bio text, city text, distance_km int, photos text[], prompts jsonb, voice_path text)
language sql stable security definer set search_path = '' as
$$ select p.id, p.display_name, extract(year from age(p.birthdate))::int, p.bio, p.city,
          case when me.location is null or p.location is null then null
               else greatest(1, round(extensions.st_distance(me.location, p.location)/1000))::int end,
          coalesce((select array_agg(ph.storage_path order by ph.position, ph.created_at) from public.photos ph
                    where ph.user_id = p.id and ph.moderation_status = 'approved'), '{}'),
          coalesce((select jsonb_agg(jsonb_build_object('prompt_id', pp.prompt_id, 'prompt', pc.text, 'answer', pp.answer) order by pp.slot)
                    from public.profile_prompts pp join public.prompt_catalog pc on pc.id = pp.prompt_id where pp.user_id = p.id), '[]'::jsonb),
          (select v.storage_path from public.voice_intros v where v.user_id = p.id and v.moderation_status = 'approved')
   from public.profiles p
   join public.profiles me on me.id = (select auth.uid())
   left join public.preferences pf on pf.user_id = me.id
   cross join lateral (select private.has_plus(me.id) as plus) x
   where p.id <> me.id and p.verification_status = 'verified' and not p.is_banned
     and not private.is_blocked_between(p.id, me.id)
     and not exists (select 1 from public.swipes s where s.swiper_id = me.id and s.target_id = p.id)
     and extract(year from age(p.birthdate)) between coalesce(pf.min_age,18) and coalesce(pf.max_age,99)
     and (pf.genders is null or cardinality(pf.genders)=0 or p.gender = any(pf.genders))
     and (me.location is null or p.location is null
          or extensions.st_dwithin(me.location, p.location, coalesce(pf.max_distance_km,50)*1000))
     and (not x.plus or not coalesce(pf.only_with_voice,false)
          or exists (select 1 from public.voice_intros v where v.user_id = p.id and v.moderation_status = 'approved'))
     and (not x.plus or not coalesce(pf.only_with_prompts,false)
          or exists (select 1 from public.profile_prompts pp where pp.user_id = p.id))
   order by p.created_at desc   -- same order for everyone: no pay-to-rank
   limit least(greatest(p_limit,1),50) $$;

-- See who liked you (Plus)
create function private.likes_received_impl()
returns table (id uuid, display_name text, age int, city text, photo text, liked_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
declare me uuid := (select auth.uid());
begin
  if me is null then raise exception 'not signed in'; end if;
  if not private.has_plus(me) then raise exception 'Victorious+ required' using errcode = '42501'; end if;
  return query
  select p.id, p.display_name, extract(year from age(p.birthdate))::int, p.city,
         (select ph.storage_path from public.photos ph where ph.user_id = p.id and ph.moderation_status = 'approved'
          order by ph.position, ph.created_at limit 1), s.created_at
    from public.swipes s join public.profiles p on p.id = s.swiper_id
   where s.target_id = me and s.liked and not p.is_banned and p.verification_status = 'verified'
     and not private.is_blocked_between(p.id, me)
     and not exists (select 1 from public.swipes m where m.swiper_id = me and m.target_id = p.id)
   order by s.created_at desc limit 100;
end $$;
revoke execute on function private.likes_received_impl() from public, anon;
grant execute on function private.likes_received_impl() to authenticated;
create function public.likes_received()
returns table (id uuid, display_name text, age int, city text, photo text, liked_at timestamptz)
language sql stable security invoker set search_path = '' as $$ select * from private.likes_received_impl() $$;
revoke execute on function public.likes_received() from public, anon;
grant execute on function public.likes_received() to authenticated;

-- Rewind the most recent pass (within 24h) (Plus)
create function private.rewind_impl() returns uuid language plpgsql security definer set search_path = '' as $$
declare me uuid := (select auth.uid()); t uuid;
begin
  if me is null then raise exception 'not signed in'; end if;
  if not private.has_plus(me) then raise exception 'Victorious+ required' using errcode = '42501'; end if;
  select s.target_id into t from public.swipes s where s.swiper_id = me and not s.liked and s.created_at > now() - interval '24 hours'
   order by s.created_at desc limit 1;
  if t is null then raise exception 'nothing to rewind'; end if;
  delete from public.swipes where swiper_id = me and target_id = t;
  return t;
end $$;
revoke execute on function private.rewind_impl() from public, anon;
grant execute on function private.rewind_impl() to authenticated;
create function public.rewind_last_pass() returns uuid language sql volatile security invoker set search_path = '' as $$ select private.rewind_impl() $$;
revoke execute on function public.rewind_last_pass() from public, anon;
grant execute on function public.rewind_last_pass() to authenticated;
