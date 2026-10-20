-- In-app calls (LiveKit). All state changes go through definer RPCs; direct writes are removed.
drop policy calls_insert on public.call_sessions;
drop policy calls_update on public.call_sessions;
revoke insert, update, delete on public.call_sessions from authenticated;
alter table public.call_sessions add column video_ok_initiator boolean not null default false,
  add column video_ok_callee boolean not null default false,
  add column answered_at timestamptz;
create unique index call_sessions_one_live_per_match on public.call_sessions(match_id) where status in ('ringing','active');

-- Both people must opt in to calls for a match.
create table public.call_consents (
  match_id uuid not null references public.matches(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (match_id, user_id)
);
create index call_consents_user_idx on public.call_consents(user_id);
alter table public.call_consents enable row level security;
revoke all on public.call_consents from anon;
create policy cc_select on public.call_consents for select to authenticated using (private.is_match_member(match_id));
create policy cc_insert on public.call_consents for insert to authenticated with check (user_id = (select auth.uid()) and private.is_match_member(match_id));
create policy cc_delete on public.call_consents for delete to authenticated using (user_id = (select auth.uid()));

create function private.start_call_impl(p_match uuid, p_kind text) returns uuid language plpgsql security definer set search_path = '' as
$$ declare v_id uuid; v_today int;
begin
  if not private.is_match_member(p_match) then raise exception 'not your match'; end if;
  if p_kind not in ('audio','video') then raise exception 'bad kind'; end if;
  if (select count(*) from public.call_consents where match_id = p_match) < 2 then raise exception 'both people must allow calls'; end if;
  if exists (select 1 from public.profiles where id = (select auth.uid()) and is_banned) then raise exception 'account suspended'; end if;
  select count(*) into v_today from public.call_sessions where initiator_id = (select auth.uid()) and started_at > now() - interval '24 hours';
  if v_today >= 20 then raise exception 'daily call limit reached'; end if;
  -- expire stale ringing calls
  update public.call_sessions set status = 'ended', ended_at = now() where match_id = p_match and status = 'ringing' and started_at < now() - interval '60 seconds';
  insert into public.call_sessions(match_id, initiator_id, kind) values (p_match, (select auth.uid()), p_kind) returning id into v_id;
  return v_id;
end $$;

create function private.respond_call_impl(p_call uuid, p_accept boolean) returns void language plpgsql security definer set search_path = '' as
$$ begin
  update public.call_sessions c set status = case when p_accept then 'active' else 'declined' end,
    answered_at = case when p_accept then now() end, ended_at = case when p_accept then null else now() end
  where c.id = p_call and c.status = 'ringing' and c.initiator_id <> (select auth.uid()) and private.is_match_member(c.match_id)
    and c.started_at > now() - interval '60 seconds';
  if not found then raise exception 'call not available'; end if;
end $$;

create function private.end_call_impl(p_call uuid) returns void language plpgsql security definer set search_path = '' as
$$ begin
  update public.call_sessions c set status = 'ended', ended_at = now()
  where c.id = p_call and c.status in ('ringing','active') and exists (select 1 from public.matches m where m.id = c.match_id and (select auth.uid()) in (m.user_a, m.user_b));
end $$;

-- Video stays blurred until BOTH sides set video_ok.
create function private.set_video_ok_impl(p_call uuid, p_ok boolean) returns void language plpgsql security definer set search_path = '' as
$$ begin
  update public.call_sessions c set
    video_ok_initiator = case when c.initiator_id = (select auth.uid()) then p_ok else c.video_ok_initiator end,
    video_ok_callee = case when c.initiator_id <> (select auth.uid()) then p_ok else c.video_ok_callee end
  where c.id = p_call and c.status = 'active' and private.is_match_member(c.match_id);
  if not found then raise exception 'call not active'; end if;
end $$;

grant execute on function private.start_call_impl(uuid,text), private.respond_call_impl(uuid,boolean), private.end_call_impl(uuid), private.set_video_ok_impl(uuid,boolean) to authenticated;
create function public.start_call(p_match uuid, p_kind text default 'audio') returns uuid language sql security invoker set search_path = '' as $$ select private.start_call_impl(p_match, p_kind) $$;
create function public.respond_call(p_call uuid, p_accept boolean) returns void language sql security invoker set search_path = '' as $$ select private.respond_call_impl(p_call, p_accept) $$;
create function public.end_call(p_call uuid) returns void language sql security invoker set search_path = '' as $$ select private.end_call_impl(p_call) $$;
create function public.set_video_ok(p_call uuid, p_ok boolean) returns void language sql security invoker set search_path = '' as $$ select private.set_video_ok_impl(p_call, p_ok) $$;
revoke execute on function public.start_call(uuid,text), public.respond_call(uuid,boolean), public.end_call(uuid), public.set_video_ok(uuid,boolean) from public, anon;
grant execute on function public.start_call(uuid,text), public.respond_call(uuid,boolean), public.end_call(uuid), public.set_video_ok(uuid,boolean) to authenticated;

-- Blocking ends live calls.
create or replace function private.on_block() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  update public.matches set unmatched_at = now() where unmatched_at is null
    and user_a = least(new.blocker_id,new.blocked_id) and user_b = greatest(new.blocker_id,new.blocked_id);
  update public.call_sessions c set status = 'ended', ended_at = now() from public.matches m
    where m.id = c.match_id and c.status in ('ringing','active')
      and m.user_a = least(new.blocker_id,new.blocked_id) and m.user_b = greatest(new.blocker_id,new.blocked_id);
  return new; end $$;

alter publication supabase_realtime add table public.call_sessions;
