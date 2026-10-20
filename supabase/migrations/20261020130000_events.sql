-- Local events / group dates. Moderators create events; verified users RSVP; attendees see each other and chat.
create table public.events (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 3 and 100),
  description text check (char_length(description) <= 2000),
  city text not null check (char_length(city) between 2 and 80),
  venue text not null check (char_length(venue) between 2 and 200),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  capacity int not null check (capacity between 2 and 500),
  cancelled boolean not null default false,
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  check (ends_at > starts_at)
);
create index events_city_start_idx on public.events(city, starts_at);
create index events_created_by_idx on public.events(created_by);
alter table public.events enable row level security;
revoke all on public.events from anon;
create policy events_select on public.events for select to authenticated using (not cancelled or private.is_admin());
create policy events_insert on public.events for insert to authenticated with check (private.is_admin() and created_by = (select auth.uid()));
create policy events_update on public.events for update to authenticated using (private.is_admin()) with check (private.is_admin());
create trigger events_audit after insert or update on public.events for each row execute function private.audit();

create table public.event_rsvps (
  event_id uuid not null references public.events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (event_id, user_id)
);
create index event_rsvps_user_idx on public.event_rsvps(user_id);
alter table public.event_rsvps enable row level security;
revoke all on public.event_rsvps from anon;
revoke insert, update on public.event_rsvps from authenticated;

create function private.is_verified_attendee(p_event uuid) returns boolean language sql stable security definer set search_path = '' as
$$ select exists (select 1 from public.event_rsvps r join public.profiles p on p.id = r.user_id
     where r.event_id = p_event and r.user_id = (select auth.uid()) and p.verification_status = 'verified' and not p.is_banned) $$;
grant execute on function private.is_verified_attendee(uuid) to authenticated;

-- Attendee list: only for verified attendees of the same event; blocked pairs hidden from each other.
create policy rsvps_select on public.event_rsvps for select to authenticated
  using (user_id = (select auth.uid())
     or (private.is_verified_attendee(event_id) and not private.is_blocked_between(user_id, (select auth.uid())))
     or private.is_admin());
create policy rsvps_delete on public.event_rsvps for delete to authenticated using (user_id = (select auth.uid()));

create function private.rsvp_impl(p_event uuid) returns void language plpgsql security definer set search_path = '' as
$$ declare e public.events%rowtype;
begin
  if not exists (select 1 from public.profiles where id = (select auth.uid()) and verification_status = 'verified' and not is_banned) then
    raise exception 'only verified members can RSVP'; end if;
  select * into e from public.events where id = p_event for update;
  if not found or e.cancelled then raise exception 'event not available'; end if;
  if e.ends_at < now() then raise exception 'event is over'; end if;
  if (select count(*) from public.event_rsvps where event_id = p_event) >= e.capacity then raise exception 'event is full'; end if;
  insert into public.event_rsvps(event_id, user_id) values (p_event, (select auth.uid())) on conflict do nothing;
end $$;
grant execute on function private.rsvp_impl(uuid) to authenticated;
create function public.rsvp_event(p_event uuid) returns void language sql security invoker set search_path = '' as $$ select private.rsvp_impl(p_event) $$;
revoke execute on function public.rsvp_event(uuid) from public, anon;
grant execute on function public.rsvp_event(uuid) to authenticated;

create function private.list_events_impl(p_city text) returns table (id uuid, title text, description text, city text, venue text,
  starts_at timestamptz, ends_at timestamptz, capacity int, going int, i_am_going boolean)
language sql stable security definer set search_path = '' as
$$ select e.id, e.title, e.description, e.city, e.venue, e.starts_at, e.ends_at, e.capacity,
     (select count(*)::int from public.event_rsvps r where r.event_id = e.id),
     exists (select 1 from public.event_rsvps r where r.event_id = e.id and r.user_id = (select auth.uid()))
   from public.events e
   where not e.cancelled and e.ends_at > now() and (p_city is null or e.city = p_city)
   order by e.starts_at limit 100 $$;
grant execute on function private.list_events_impl(text) to authenticated;
create function public.list_events(p_city text default null) returns table (id uuid, title text, description text, city text, venue text,
  starts_at timestamptz, ends_at timestamptz, capacity int, going int, i_am_going boolean)
  language sql stable security invoker set search_path = '' as $$ select * from private.list_events_impl(p_city) $$;
revoke execute on function public.list_events(text) from public, anon;
grant execute on function public.list_events(text) to authenticated;

create table public.event_messages (
  id bigint generated always as identity primary key,
  event_id uuid not null references public.events(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  created_at timestamptz not null default now()
);
create index event_messages_event_idx on public.event_messages(event_id, id);
create index event_messages_sender_idx on public.event_messages(sender_id);
alter table public.event_messages enable row level security;
revoke all on public.event_messages from anon;
revoke update on public.event_messages from authenticated;
create policy em_select on public.event_messages for select to authenticated
  using ((private.is_verified_attendee(event_id) and not private.is_blocked_between(sender_id, (select auth.uid()))) or private.is_admin());
create policy em_insert on public.event_messages for insert to authenticated
  with check (sender_id = (select auth.uid()) and private.is_verified_attendee(event_id));
create policy em_delete on public.event_messages for delete to authenticated using (sender_id = (select auth.uid()) or private.is_admin());
alter publication supabase_realtime add table public.event_messages;
