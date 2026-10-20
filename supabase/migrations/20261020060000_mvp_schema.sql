-- MatchVictorious MVP schema. No phone numbers anywhere by design.
create extension if not exists postgis with schema extensions;
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to authenticated;

create type public.verification_status as enum ('unverified','pending','verified','rejected');
create type public.report_status as enum ('open','reviewing','actioned','dismissed');

-- Admins: membership managed only by service role / SQL.
create table private.admins (user_id uuid primary key references auth.users(id) on delete cascade, created_at timestamptz not null default now());

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 40),
  birthdate date not null check (birthdate <= (current_date - interval '18 years')),
  gender text check (char_length(gender) <= 40),
  bio text check (char_length(bio) <= 500),
  prompts jsonb not null default '[]'::jsonb,
  location extensions.geography(Point,4326),
  verification_status public.verification_status not null default 'unverified',
  is_banned boolean not null default false,
  imported_from text check (char_length(imported_from) <= 40),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index profiles_location_gix on public.profiles using gist (location);

create table public.preferences (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  min_age int not null default 18 check (min_age >= 18),
  max_age int not null default 99 check (max_age >= min_age),
  max_distance_km int not null default 50 check (max_distance_km between 1 and 500),
  genders text[] not null default '{}',
  updated_at timestamptz not null default now()
);

create table public.photos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  storage_path text not null unique,
  position smallint not null default 0 check (position between 0 and 8),
  moderation_status text not null default 'pending' check (moderation_status in ('pending','approved','rejected')),
  created_at timestamptz not null default now()
);
create index photos_user_idx on public.photos(user_id);

create table public.blocks (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index blocks_blocked_idx on public.blocks(blocked_id);

create table public.swipes (
  swiper_id uuid not null references public.profiles(id) on delete cascade,
  target_id uuid not null references public.profiles(id) on delete cascade,
  liked boolean not null,
  created_at timestamptz not null default now(),
  primary key (swiper_id, target_id),
  check (swiper_id <> target_id)
);
create index swipes_target_idx on public.swipes(target_id) where liked;

create table public.matches (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references public.profiles(id) on delete cascade,
  user_b uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '14 days',
  unmatched_at timestamptz,
  check (user_a < user_b),
  unique (user_a, user_b)
);
create index matches_user_b_idx on public.matches(user_b);

create table public.messages (
  id bigint generated always as identity primary key,
  match_id uuid not null references public.matches(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now()
);
create index messages_match_idx on public.messages(match_id, created_at);
create index messages_sender_idx on public.messages(sender_id);

create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reported_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null check (reason in ('spam','scam','fake','harassment','underage','explicit','other')),
  details text check (char_length(details) <= 2000),
  status public.report_status not null default 'open',
  created_at timestamptz not null default now()
);
create index reports_reporter_idx on public.reports(reporter_id);
create index reports_reported_idx on public.reports(reported_id);

create table public.call_sessions (
  id uuid primary key default gen_random_uuid(),
  match_id uuid not null references public.matches(id) on delete cascade,
  initiator_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null default 'audio' check (kind in ('audio','video')),
  room_name text not null unique default gen_random_uuid()::text,
  status text not null default 'ringing' check (status in ('ringing','active','ended','declined')),
  started_at timestamptz not null default now(),
  ended_at timestamptz
);
create index call_sessions_match_idx on public.call_sessions(match_id);
create index call_sessions_initiator_idx on public.call_sessions(initiator_id);

create table private.audit_log (
  id bigint generated always as identity primary key,
  actor_id uuid,
  action text not null,
  target text,
  detail jsonb,
  created_at timestamptz not null default now()
);

-- Helpers (security definer, private schema, fixed search_path)
create function private.is_admin() returns boolean language sql stable security definer set search_path = '' as
$$ select exists (select 1 from private.admins where user_id = (select auth.uid())) $$;

create function private.is_blocked_between(a uuid, b uuid) returns boolean language sql stable security definer set search_path = '' as
$$ select exists (select 1 from public.blocks where (blocker_id=a and blocked_id=b) or (blocker_id=b and blocked_id=a)) $$;

create function private.is_match_member(m uuid) returns boolean language sql stable security definer set search_path = '' as
$$ select exists (select 1 from public.matches x where x.id = m and x.unmatched_at is null
     and (select auth.uid()) in (x.user_a, x.user_b)
     and not private.is_blocked_between(x.user_a, x.user_b)) $$;

create function private.is_visible_profile(p uuid) returns boolean language sql stable security definer set search_path = '' as
$$ select exists (select 1 from public.profiles pr where pr.id = p and not pr.is_banned
     and pr.verification_status = 'verified'
     and not private.is_blocked_between(p, (select auth.uid())))
   or exists (select 1 from public.matches x where x.unmatched_at is null and not private.is_blocked_between(x.user_a,x.user_b)
     and ((x.user_a=p and x.user_b=(select auth.uid())) or (x.user_b=p and x.user_a=(select auth.uid())))) $$;
grant execute on all functions in schema private to authenticated;

-- Triggers
create function private.touch_updated_at() returns trigger language plpgsql set search_path = '' as
$$ begin new.updated_at = now(); return new; end $$;
create trigger profiles_touch before update on public.profiles for each row execute function private.touch_updated_at();
create trigger preferences_touch before update on public.preferences for each row execute function private.touch_updated_at();

-- Users cannot self-verify or unban.
create function private.protect_profile_columns() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  if not private.is_admin() and current_user in ('authenticated','anon') then
    if tg_op = 'INSERT' then new.verification_status := 'unverified'; new.is_banned := false;
    else new.verification_status := old.verification_status; new.is_banned := old.is_banned; end if;
  end if;
  return new; end $$;
create trigger profiles_protect before insert or update on public.profiles for each row execute function private.protect_profile_columns();

create function private.on_swipe() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  if new.liked and exists (select 1 from public.swipes s where s.swiper_id = new.target_id and s.target_id = new.swiper_id and s.liked)
     and not private.is_blocked_between(new.swiper_id, new.target_id) then
    insert into public.matches(user_a, user_b) values (least(new.swiper_id,new.target_id), greatest(new.swiper_id,new.target_id))
    on conflict (user_a,user_b) do nothing;
  end if;
  return new; end $$;
create trigger swipes_match after insert or update on public.swipes for each row execute function private.on_swipe();

create function private.on_block() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  update public.matches set unmatched_at = now() where unmatched_at is null
    and user_a = least(new.blocker_id,new.blocked_id) and user_b = greatest(new.blocker_id,new.blocked_id);
  return new; end $$;
create trigger blocks_unmatch after insert on public.blocks for each row execute function private.on_block();

create function private.audit() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  insert into private.audit_log(actor_id, action, target, detail) values ((select auth.uid()), tg_table_name||'.'||lower(tg_op), coalesce(new.id::text, old.id::text), null);
  return coalesce(new, old); end $$;
create trigger reports_audit after insert or update on public.reports for each row execute function private.audit();

-- Discovery RPC (security invoker; relies on RLS)
create function public.discover(p_limit int default 20)
returns table (id uuid, display_name text, age int, bio text, distance_km double precision)
language sql stable security invoker set search_path = '' as
$$ select p.id, p.display_name, extract(year from age(p.birthdate))::int, p.bio,
          case when me.location is null or p.location is null then null
               else extensions.st_distance(me.location, p.location)/1000 end
   from public.profiles p
   join public.profiles me on me.id = (select auth.uid())
   left join public.preferences pf on pf.user_id = me.id
   where p.id <> me.id and p.verification_status = 'verified' and not p.is_banned
     and not exists (select 1 from public.swipes s where s.swiper_id = me.id and s.target_id = p.id)
     and extract(year from age(p.birthdate)) between coalesce(pf.min_age,18) and coalesce(pf.max_age,99)
     and (pf.genders is null or cardinality(pf.genders)=0 or p.gender = any(pf.genders))
     and (me.location is null or p.location is null
          or extensions.st_dwithin(me.location, p.location, coalesce(pf.max_distance_km,50)*1000))
   order by p.created_at desc
   limit least(greatest(p_limit,1),50) $$;
revoke execute on function public.discover(int) from public, anon;
grant execute on function public.discover(int) to authenticated;

-- RLS
alter table public.profiles enable row level security;
alter table public.preferences enable row level security;
alter table public.photos enable row level security;
alter table public.blocks enable row level security;
alter table public.swipes enable row level security;
alter table public.matches enable row level security;
alter table public.messages enable row level security;
alter table public.reports enable row level security;
alter table public.call_sessions enable row level security;
alter table private.admins enable row level security;
alter table private.audit_log enable row level security;
revoke all on all tables in schema public from anon;

create policy profiles_select on public.profiles for select to authenticated
  using (id = (select auth.uid()) or private.is_visible_profile(id) or private.is_admin());
create policy profiles_insert on public.profiles for insert to authenticated with check (id = (select auth.uid()));
create policy profiles_update on public.profiles for update to authenticated
  using (id = (select auth.uid()) or private.is_admin()) with check (id = (select auth.uid()) or private.is_admin());
create policy profiles_delete on public.profiles for delete to authenticated using (id = (select auth.uid()));

create policy prefs_all on public.preferences for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create policy photos_select on public.photos for select to authenticated
  using (user_id = (select auth.uid()) or (moderation_status = 'approved' and private.is_visible_profile(user_id)) or private.is_admin());
create policy photos_insert on public.photos for insert to authenticated
  with check (user_id = (select auth.uid()) and moderation_status = 'pending' and storage_path like (select auth.uid())::text || '/%');
create policy photos_delete on public.photos for delete to authenticated using (user_id = (select auth.uid()));

create policy blocks_select on public.blocks for select to authenticated using (blocker_id = (select auth.uid()));
create policy blocks_insert on public.blocks for insert to authenticated with check (blocker_id = (select auth.uid()));
create policy blocks_delete on public.blocks for delete to authenticated using (blocker_id = (select auth.uid()));

create policy swipes_select on public.swipes for select to authenticated using (swiper_id = (select auth.uid()));
create policy swipes_insert on public.swipes for insert to authenticated
  with check (swiper_id = (select auth.uid()) and not private.is_blocked_between(swiper_id, target_id));

create policy matches_select on public.matches for select to authenticated
  using ((select auth.uid()) in (user_a, user_b) and not private.is_blocked_between(user_a, user_b));
create policy matches_update on public.matches for update to authenticated
  using ((select auth.uid()) in (user_a, user_b)) with check ((select auth.uid()) in (user_a, user_b));
-- Column-level: users may only set unmatched_at.
revoke update on public.matches from authenticated;
grant update (unmatched_at) on public.matches to authenticated;

create policy messages_select on public.messages for select to authenticated using (private.is_match_member(match_id));
create policy messages_insert on public.messages for insert to authenticated
  with check (sender_id = (select auth.uid()) and private.is_match_member(match_id));

create policy reports_insert on public.reports for insert to authenticated
  with check (reporter_id = (select auth.uid()) and status = 'open');
create policy reports_select on public.reports for select to authenticated
  using (reporter_id = (select auth.uid()) or private.is_admin());
create policy reports_update on public.reports for update to authenticated
  using (private.is_admin()) with check (private.is_admin());

create policy calls_select on public.call_sessions for select to authenticated using (private.is_match_member(match_id));
create policy calls_insert on public.call_sessions for insert to authenticated
  with check (initiator_id = (select auth.uid()) and private.is_match_member(match_id));
create policy calls_update on public.call_sessions for update to authenticated
  using (private.is_match_member(match_id)) with check (private.is_match_member(match_id));
revoke update on public.call_sessions from authenticated;
grant update (status, ended_at) on public.call_sessions to authenticated;

create policy admins_self on private.admins for select to authenticated using (user_id = (select auth.uid()));
create policy audit_admin on private.audit_log for select to authenticated using (private.is_admin());

-- Realtime for chat
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.matches;

-- Storage: private photos bucket, path = <uid>/<file>
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos','photos', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;
create policy photos_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy photos_obj_update on storage.objects for update to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy photos_obj_delete on storage.objects for delete to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy photos_obj_select on storage.objects for select to authenticated
  using (bucket_id = 'photos' and (
    (storage.foldername(name))[1] = (select auth.uid())::text
    or exists (select 1 from public.photos ph where ph.storage_path = name and ph.moderation_status = 'approved'
               and private.is_visible_profile(ph.user_id))));
