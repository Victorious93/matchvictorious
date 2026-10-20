// Deletes the CALLER's account. Auth is done here (verify_jwt=false only so browser CORS preflight works):
// the bearer token is validated with auth.getUser() before anything is touched.
// SUPABASE_SERVICE_ROLE_KEY is injected by the Supabase runtime; it is never in the app or repo.
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json(405, { error: "POST only" });
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return json(401, { error: "Missing bearer token" });

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error } = await admin.auth.getUser(token);
  if (error || !user) return json(401, { error: "Invalid or expired session" });
  const uid = user.id;

  // 1) Storage objects in <bucket>/<uid>/ (DB rows cascade, files do not).
  for (const name of ["photos", "voice", "selfies"]) {
    const bucket = admin.storage.from(name);
    for (;;) {
      const { data: files, error: le } = await bucket.list(uid, { limit: 100 });
      if (le) { if (/not.?found/i.test(le.message)) break; return json(500, { error: `Could not list ${name}` }); }
      if (!files || files.length === 0) break;
      const { error: re } = await bucket.remove(files.map((f) => `${uid}/${f.name}`));
      if (re) return json(500, { error: `Could not delete ${name}` });
    }
  }
  // 2) Auth user. Cascades: profile, preferences, photos rows, swipes, matches, messages, blocks, call_sessions.
  //    Reports are retained with reporter/reported set to NULL (safety/legal retention).
  await admin.auth.admin.signOut(token).catch(() => {});
  const { error: de } = await admin.auth.admin.deleteUser(uid);
  if (de) return json(500, { error: "Could not delete account" });
  return json(200, { deleted: true });
});
