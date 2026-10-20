-- Reports survive account deletion (legal/T&S retention); identities are nulled.
alter table public.reports alter column reporter_id drop not null, alter column reported_id drop not null;
alter table public.reports drop constraint reports_reporter_id_fkey, drop constraint reports_reported_id_fkey;
alter table public.reports add constraint reports_reporter_id_fkey foreign key (reporter_id) references public.profiles(id) on delete set null,
  add constraint reports_reported_id_fkey foreign key (reported_id) references public.profiles(id) on delete set null;
alter table public.reports add column reported_snapshot jsonb;  -- filled by trigger so moderators keep context

create function private.snapshot_report() returns trigger language plpgsql security definer set search_path = '' as
$$ begin
  select jsonb_build_object('display_name', p.display_name, 'bio', p.bio) into new.reported_snapshot from public.profiles p where p.id = new.reported_id;
  return new; end $$;
create trigger reports_snapshot before insert on public.reports for each row execute function private.snapshot_report();

-- Approximate location only: snap to a 0.01° grid (~1.1 km) on every write.
alter table public.profiles add column city text check (char_length(city) <= 80);
create function private.snap_location() returns trigger language plpgsql set search_path = '' as
$$ begin
  if new.location is not null then
    new.location := extensions.st_snaptogrid(new.location::extensions.geometry, 0.01)::extensions.geography;
  end if;
  return new; end $$;
create trigger profiles_snap_location before insert or update of location on public.profiles for each row execute function private.snap_location();

-- Other users must never read coordinates: column-level SELECT without location.
revoke select on public.profiles from authenticated;
grant select (id, display_name, birthdate, gender, bio, prompts, verification_status, is_banned, imported_from, city, created_at, updated_at) on public.profiles to authenticated;

-- Manual city fallback (city centroids, public reference data).
create table public.cities (name text primary key, location extensions.geography(Point,4326) not null);
alter table public.cities enable row level security;
create policy cities_read on public.cities for select to authenticated using (true);
revoke all on public.cities from anon;
grant select (name) on public.cities to authenticated;
insert into public.cities(name, location) select n, extensions.st_setsrid(extensions.st_makepoint(lon, lat), 4326)::extensions.geography from (values
 ('New York, NY',40.71,-74.01),('Los Angeles, CA',34.05,-118.24),('Chicago, IL',41.88,-87.63),('Houston, TX',29.76,-95.37),('Phoenix, AZ',33.45,-112.07),
 ('Philadelphia, PA',39.95,-75.17),('San Antonio, TX',29.42,-98.49),('San Diego, CA',32.72,-117.16),('Dallas, TX',32.78,-96.80),('Austin, TX',30.27,-97.74),
 ('Jacksonville, FL',30.33,-81.66),('San Jose, CA',37.34,-121.89),('Columbus, OH',39.96,-83.00),('Charlotte, NC',35.23,-80.84),('Indianapolis, IN',39.77,-86.16),
 ('San Francisco, CA',37.77,-122.42),('Seattle, WA',47.61,-122.33),('Denver, CO',39.74,-104.99),('Washington, DC',38.91,-77.04),('Boston, MA',42.36,-71.06),
 ('Nashville, TN',36.16,-86.78),('Detroit, MI',42.33,-83.05),('Portland, OR',45.52,-122.68),('Las Vegas, NV',36.17,-115.14),('Atlanta, GA',33.75,-84.39),
 ('Miami, FL',25.76,-80.19),('Minneapolis, MN',44.98,-93.27),('New Orleans, LA',29.95,-90.07),('Orlando, FL',28.54,-81.38),('Tampa, FL',27.95,-82.46),
 ('Pittsburgh, PA',40.44,-80.00),('Baltimore, MD',39.29,-76.61),('St. Louis, MO',38.63,-90.20),('Salt Lake City, UT',40.76,-111.89),('Raleigh, NC',35.78,-78.64),
 ('London, UK',51.51,-0.13),('Toronto, ON',43.65,-79.38),('Mexico City, MX',19.43,-99.13),('Berlin, DE',52.52,13.40),('Paris, FR',48.86,2.35)
) v(n,lat,lon);

-- Set own approximate location (device) or city centroid (fallback).
create function public.set_my_location(lat double precision, lon double precision) returns void language sql security invoker set search_path = '' as
$$ update public.profiles set location = extensions.st_setsrid(extensions.st_makepoint(round(lon::numeric,2)::float8, round(lat::numeric,2)::float8),4326)::extensions.geography, city = null
   where id = (select auth.uid()) and lat between -90 and 90 and lon between -180 and 180 $$;
create function private.city_location(c text) returns extensions.geography language sql stable security definer set search_path = '' as
$$ select location from public.cities where name = c $$;
create function public.set_my_city(city_name text) returns void language plpgsql security invoker set search_path = '' as
$$ declare g extensions.geography := private.city_location(city_name);
begin
  if g is null then raise exception 'Unknown city'; end if;
  update public.profiles set location = g, city = city_name where id = (select auth.uid());
end $$;
revoke execute on function public.set_my_location(double precision,double precision), public.set_my_city(text) from public, anon;
grant execute on function public.set_my_location(double precision,double precision), public.set_my_city(text) to authenticated;

-- Discovery: definer impl in private schema (needs coordinates), returns only rounded distance + approved photo paths.
drop function public.discover(int);
create function private.discover_impl(p_limit int)
returns table (id uuid, display_name text, age int, bio text, city text, distance_km int, photos text[])
language sql stable security definer set search_path = '' as
$$ select p.id, p.display_name, extract(year from age(p.birthdate))::int, p.bio, p.city,
          case when me.location is null or p.location is null then null
               else greatest(1, round(extensions.st_distance(me.location, p.location)/1000))::int end,
          coalesce((select array_agg(ph.storage_path order by ph.position, ph.created_at) from public.photos ph
                    where ph.user_id = p.id and ph.moderation_status = 'approved'), '{}')
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
create function public.discover(p_limit int default 20)
returns table (id uuid, display_name text, age int, bio text, city text, distance_km int, photos text[])
language sql stable security invoker set search_path = '' as $$ select * from private.discover_impl(p_limit) $$;
revoke execute on function public.discover(int) from public, anon;
grant execute on function public.discover(int) to authenticated;

