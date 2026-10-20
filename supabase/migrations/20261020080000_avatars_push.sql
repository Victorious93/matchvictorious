-- 1) Match/chat cards with the other person's first approved photo (path only; client signs a short URL).
create function private.match_cards_impl()
returns table (match_id uuid, other_id uuid, other_name text, photo_path text, created_at timestamptz, expires_at timestamptz, last_message_at timestamptz)
language sql stable security definer set search_path = '' as
$$ select m.id, o.id, o.display_name,
     (select ph.storage_path from public.photos ph where ph.user_id = o.id and ph.moderation_status = 'approved' order by ph.position, ph.created_at limit 1),
     m.created_at, m.expires_at,
     (select max(msg.created_at) from public.messages msg where msg.match_id = m.id)
   from public.matches m
   join public.profiles o on o.id = case when m.user_a = (select auth.uid()) then m.user_b else m.user_a end
   where (select auth.uid()) in (m.user_a, m.user_b) and m.unmatched_at is null
     and not private.is_blocked_between(m.user_a, m.user_b) and not o.is_banned
   order by coalesce((select max(msg.created_at) from public.messages msg where msg.match_id = m.id), m.created_at) desc $$;
revoke execute on function private.match_cards_impl() from public, anon;
grant execute on function private.match_cards_impl() to authenticated;
create function public.match_cards()
returns table (match_id uuid, other_id uuid, other_name text, photo_path text, created_at timestamptz, expires_at timestamptz, last_message_at timestamptz)
language sql stable security invoker set search_path = '' as $$ select * from private.match_cards_impl() $$;
revoke execute on function public.match_cards() from public, anon;
grant execute on function public.match_cards() to authenticated;

-- 2) Push: device tokens (own rows only) and notification preferences.
create table public.device_tokens (
  token text primary key check (char_length(token) between 10 and 4096),
  user_id uuid not null references auth.users(id) on delete cascade,
  platform text not null default 'android' check (platform in ('android')),
  updated_at timestamptz not null default now()
);
create index device_tokens_user_idx on public.device_tokens(user_id);
alter table public.device_tokens enable row level security;
revoke all on public.device_tokens from anon;
create policy tokens_select on public.device_tokens for select to authenticated using (user_id = (select auth.uid()));
create policy tokens_insert on public.device_tokens for insert to authenticated with check (user_id = (select auth.uid()));
create policy tokens_update on public.device_tokens for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy tokens_delete on public.device_tokens for delete to authenticated using (user_id = (select auth.uid()));

-- A token moves to whoever signs in on that device (removes it from a previous account).
create function private.register_token_impl(p_token text) returns void language plpgsql security definer set search_path = '' as
$$ begin
  if (select auth.uid()) is null then raise exception 'not signed in'; end if;
  insert into public.device_tokens(token, user_id) values (p_token, (select auth.uid()))
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end $$;
revoke execute on function private.register_token_impl(text) from public, anon;
grant execute on function private.register_token_impl(text) to authenticated;
create function public.register_device_token(p_token text) returns void language sql security invoker set search_path = '' as
$$ select private.register_token_impl(p_token) $$;
revoke execute on function public.register_device_token(text) from public, anon;
grant execute on function public.register_device_token(text) to authenticated;

create table public.notification_prefs (
  user_id uuid primary key references auth.users(id) on delete cascade,
  show_previews boolean not null default false,
  updated_at timestamptz not null default now()
);
alter table public.notification_prefs enable row level security;
revoke all on public.notification_prefs from anon;
create policy nprefs_all on public.notification_prefs for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Idempotency markers: the send-push function only sends once per row (so calling it again is harmless).
alter table public.matches add column push_sent_at timestamptz;
alter table public.messages add column push_sent_at timestamptz;
revoke update on public.messages from authenticated;

-- 3) Triggers -> Edge Function via pg_net (payload is only the row id; the function re-reads the row).
create extension if not exists pg_net with schema extensions;
create function private.notify_push() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  perform net.http_post(
    url := 'https://swcotjvqumzgszzhnmks.supabase.co/functions/v1/send-push',
    body := jsonb_build_object('type', tg_table_name, 'id', new.id::text),
    headers := '{"Content-Type":"application/json"}'::jsonb,
    timeout_milliseconds := 5000);
  return new;
exception when others then
  return new; -- never block a match/message because push failed
end $$;
create trigger matches_push after insert on public.matches for each row execute function private.notify_push();
create trigger messages_push after insert on public.messages for each row execute function private.notify_push();
