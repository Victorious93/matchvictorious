-- Run as invoker so current_user reflects the API role (security definer made it 'postgres').
create or replace function private.protect_profile_columns() returns trigger language plpgsql security invoker set search_path = '' as
$$ begin
  if current_user in ('authenticated','anon') and not private.is_admin() then
    if tg_op = 'INSERT' then new.verification_status := 'unverified'; new.is_banned := false;
    else new.verification_status := old.verification_status; new.is_banned := old.is_banned; end if;
  end if;
  return new; end $$;
