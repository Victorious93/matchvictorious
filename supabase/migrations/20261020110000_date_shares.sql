-- "Share my date": the user creates a share; the app opens the Android share sheet. Nothing is auto-sent.
create table public.date_shares (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  token text not null unique default encode(extensions.gen_random_bytes(18), 'hex'),
  match_id uuid references public.matches(id) on delete set null,
  date_name text not null check (char_length(date_name) between 1 and 60),
  place text not null check (char_length(place) between 1 and 200),
  starts_at timestamptz not null,
  checkin_at timestamptz,
  note text check (char_length(note) <= 300),
  expires_at timestamptz not null,
  revoked boolean not null default false,
  created_at timestamptz not null default now(),
  check (expires_at > starts_at and expires_at <= starts_at + interval '24 hours'),
  check (starts_at > created_at - interval '1 hour' and starts_at < created_at + interval '30 days')
);
create index date_shares_user_idx on public.date_shares(user_id);
create index date_shares_match_idx on public.date_shares(match_id);
alter table public.date_shares enable row level security;
revoke all on public.date_shares from anon;
create policy ds_select on public.date_shares for select to authenticated using (user_id = (select auth.uid()));
create policy ds_insert on public.date_shares for insert to authenticated
  with check (user_id = (select auth.uid()) and (match_id is null or private.is_match_member(match_id)));
create policy ds_update on public.date_shares for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy ds_delete on public.date_shares for delete to authenticated using (user_id = (select auth.uid()));
revoke update on public.date_shares from authenticated;
grant update (revoked, checkin_at, note) on public.date_shares to authenticated;
