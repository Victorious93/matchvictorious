// Public read of a "Share my date" link: /functions/v1/date-share?t=<token>
// Returns only what the user chose to share, and only until it expires or is revoked.
import { createClient } from "npm:@supabase/supabase-js@2";
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type", "Access-Control-Allow-Methods": "GET, OPTIONS" };
const json = (s: number, b: unknown) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "GET") return json(405, { error: "GET only" });
  const t = new URL(req.url).searchParams.get("t") ?? "";
  if (!/^[0-9a-f]{36}$/.test(t)) return json(404, { error: "Not found or expired" });
  const { data } = await admin.from("date_shares").select("date_name,place,starts_at,checkin_at,note,expires_at,revoked").eq("token", t).maybeSingle();
  if (!data || data.revoked || new Date(data.expires_at) < new Date()) return json(404, { error: "Not found or expired" });
  const { revoked: _r, ...pub } = data;
  return json(200, pub);
});
