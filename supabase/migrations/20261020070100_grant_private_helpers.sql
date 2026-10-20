-- New private helpers need explicit EXECUTE for API callers.
grant execute on function private.discover_impl(int), private.city_location(text) to authenticated;
