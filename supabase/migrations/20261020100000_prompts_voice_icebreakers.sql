-- Curated prompts
create table public.prompt_catalog (id text primary key, text text not null, active boolean not null default true);
alter table public.prompt_catalog enable row level security;
revoke all on public.prompt_catalog from anon;
create policy prompt_catalog_read on public.prompt_catalog for select to authenticated using (true);
revoke insert, update, delete on public.prompt_catalog from authenticated;
insert into public.prompt_catalog(id, text) values
 ('perfect_sunday','My perfect Sunday'),('green_flag','A green flag I look for'),('two_truths','Two truths and a lie'),
 ('geek_out','I geek out on'),('unpopular_opinion','My most unpopular opinion'),('first_date','Best first date idea'),
 ('win_me_over','You can win me over with'),('travel','The last place I travelled was'),('comfort_food','My comfort food is'),
 ('life_goal','A life goal of mine'),('weird_skill','My weirdest skill'),('song','The song I can''t stop playing'),
 ('looking_for','I''m looking for'),('hot_take','My hot take'),('dealbreaker','A dealbreaker for me');

create table public.profile_prompts (
  user_id uuid not null references public.profiles(id) on delete cascade,
  slot smallint not null check (slot between 1 and 3),
  prompt_id text not null references public.prompt_catalog(id),
  answer text not null check (char_length(trim(answer)) between 1 and 200),
  updated_at timestamptz not null default now(),
  primary key (user_id, slot),
  unique (user_id, prompt_id)
);
create index profile_prompts_prompt_idx on public.profile_prompts(prompt_id);
alter table public.profile_prompts enable row level security;
revoke all on public.profile_prompts from anon;
create policy pp_select on public.profile_prompts for select to authenticated
  using (user_id = (select auth.uid()) or private.is_visible_profile(user_id) or private.is_admin());
create policy pp_insert on public.profile_prompts for insert to authenticated with check (user_id = (select auth.uid()));
create policy pp_update on public.profile_prompts for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy pp_delete on public.profile_prompts for delete to authenticated using (user_id = (select auth.uid()));

-- Voice intro (<=30 s), moderated like photos
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('voice','voice', false, 1048576, array['audio/mp4','audio/aac','audio/m4a','audio/x-m4a','audio/mpeg','audio/ogg','audio/webm','audio/3gpp'])
on conflict (id) do nothing;
create table public.voice_intros (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  storage_path text not null unique,
  duration_s numeric(4,1) not null check (duration_s > 0 and duration_s <= 30),
  moderation_status text not null default 'pending' check (moderation_status in ('pending','approved','rejected')),
  rejection_reason text check (char_length(rejection_reason) <= 500),
  created_at timestamptz not null default now()
);
alter table public.voice_intros enable row level security;
revoke all on public.voice_intros from anon;
create policy vi_select on public.voice_intros for select to authenticated
  using (user_id = (select auth.uid()) or (moderation_status = 'approved' and private.is_visible_profile(user_id)) or private.is_admin());
create policy vi_insert on public.voice_intros for insert to authenticated
  with check (user_id = (select auth.uid()) and moderation_status = 'pending' and storage_path like (select auth.uid())::text || '/%');
create policy vi_delete on public.voice_intros for delete to authenticated using (user_id = (select auth.uid()));
create policy voice_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'voice' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy voice_obj_update on storage.objects for update to authenticated
  using (bucket_id = 'voice' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy voice_obj_delete on storage.objects for delete to authenticated
  using (bucket_id = 'voice' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy voice_obj_select on storage.objects for select to authenticated
  using (bucket_id = 'voice' and ((storage.foldername(name))[1] = (select auth.uid())::text or private.is_admin()
    or exists (select 1 from public.voice_intros v where v.storage_path = name and v.moderation_status = 'approved' and private.is_visible_profile(v.user_id))));

create function private.mod_pending_voice_impl() returns table (user_id uuid, display_name text, storage_path text, duration_s numeric, created_at timestamptz)
language plpgsql stable security definer set search_path = '' as
$$ begin perform private.require_admin();
  return query select v.user_id, p.display_name, v.storage_path, v.duration_s, v.created_at from public.voice_intros v join public.profiles p on p.id = v.user_id
  where v.moderation_status = 'pending' order by v.created_at limit 200; end $$;
create function private.mod_review_voice_impl(p_user uuid, p_approve boolean, p_reason text) returns void language plpgsql security definer set search_path = '' as
$$ begin perform private.require_admin();
  if not p_approve and coalesce(trim(p_reason),'') = '' then raise exception 'reason required'; end if;
  update public.voice_intros set moderation_status = case when p_approve then 'approved' else 'rejected' end,
    rejection_reason = case when p_approve then null else left(p_reason,500) end where user_id = p_user;
  if not found then raise exception 'not found'; end if;
  perform private.log(case when p_approve then 'voice.approve' else 'voice.reject' end, p_user::text, jsonb_build_object('reason', p_reason)); end $$;
grant execute on function private.mod_pending_voice_impl(), private.mod_review_voice_impl(uuid,boolean,text) to authenticated;
create function public.mod_pending_voice() returns table (user_id uuid, display_name text, storage_path text, duration_s numeric, created_at timestamptz)
  language sql stable security invoker set search_path = '' as $$ select * from private.mod_pending_voice_impl() $$;
create function public.mod_review_voice(p_user uuid, p_approve boolean, p_reason text default null) returns void
  language sql security invoker set search_path = '' as $$ select private.mod_review_voice_impl(p_user, p_approve, p_reason) $$;
revoke execute on function public.mod_pending_voice(), public.mod_review_voice(uuid,boolean,text) from public, anon;
grant execute on function public.mod_pending_voice(), public.mod_review_voice(uuid,boolean,text) to authenticated;

-- Like with comment on a prompt. Comments are only revealed if it becomes a match (as the first chat messages).
alter table public.swipes add column comment text check (char_length(comment) <= 300),
  add column prompt_id text references public.prompt_catalog(id);
create index swipes_prompt_idx on public.swipes(prompt_id);
create or replace function private.on_swipe() returns trigger language plpgsql security definer set search_path = '' as
$$ declare v_match uuid;
begin
  if new.liked and exists (select 1 from public.swipes s where s.swiper_id = new.target_id and s.target_id = new.swiper_id and s.liked)
     and not private.is_blocked_between(new.swiper_id, new.target_id) then
    insert into public.matches(user_a, user_b) values (least(new.swiper_id,new.target_id), greatest(new.swiper_id,new.target_id))
    on conflict (user_a,user_b) do nothing returning id into v_match;
    if v_match is not null then
      insert into public.messages(match_id, sender_id, body)
      select v_match, s.swiper_id,
        left(coalesce('“' || pc.text || '” — ', '') || s.comment, 2000)
      from public.swipes s left join public.prompt_catalog pc on pc.id = s.prompt_id
      where s.liked and nullif(trim(s.comment),'') is not null
        and ((s.swiper_id = new.swiper_id and s.target_id = new.target_id) or (s.swiper_id = new.target_id and s.target_id = new.swiper_id))
      order by s.created_at;
    end if;
  end if;
  return new; end $$;
-- Comments without a like make no sense.
alter table public.swipes add constraint swipes_comment_needs_like check (comment is null or liked);

-- Discovery now includes prompts and the approved voice intro path.
drop function public.discover(int);
drop function private.discover_impl(int);
create function private.discover_impl(p_limit int)
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
   where p.id <> me.id and p.verification_status = 'verified' and not p.is_banned
     and not private.is_blocked_between(p.id, me.id)
     and not exists (select 1 from public.swipes s where s.swiper_id = me.id and s.target_id = p.id)
     and extract(year from age(p.birthdate)) between coalesce(pf.min_age,18) and coalesce(pf.max_age,99)
     and (pf.genders is null or cardinality(pf.genders)=0 or p.gender = any(pf.genders))
     and (me.location is null or p.location is null
          or extensions.st_dwithin(me.location, p.location, coalesce(pf.max_distance_km,50)*1000))
   order by p.created_at desc
   limit least(greatest(p_limit,1),50) $$;
revoke execute on function private.discover_impl(int) from public, anon;
grant execute on function private.discover_impl(int) to authenticated;
create function public.discover(p_limit int default 20)
returns table (id uuid, display_name text, age int, bio text, city text, distance_km int, photos text[], prompts jsonb, voice_path text)
language sql stable security invoker set search_path = '' as $$ select * from private.discover_impl(p_limit) $$;
revoke execute on function public.discover(int) from public, anon;
grant execute on function public.discover(int) to authenticated;
