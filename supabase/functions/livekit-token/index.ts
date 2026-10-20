// Mints a short-lived LiveKit access token for ONE call the caller belongs to.
// Secrets (Supabase function secrets): LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET. If unset -> 503 {disabled:true}.
// No recording: tokens never include roomRecord/roomAdmin, and we never start egress.
import { createClient } from "npm:@supabase/supabase-js@2";
import { SignJWT } from "npm:jose@5";

const json = (s: number, b: unknown) => new Response(JSON.stringify(b), { status: s, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "POST only" });
  const url = Deno.env.get("LIVEKIT_URL"), key = Deno.env.get("LIVEKIT_API_KEY"), secret = Deno.env.get("LIVEKIT_API_SECRET");
  if (!url || !key || !secret) return json(503, { disabled: true, error: "Calls are not configured yet" });
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return json(401, { error: "Missing bearer token" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  const { data: { user }, error } = await admin.auth.getUser(token);
  if (error || !user) return json(401, { error: "Invalid session" });

  let body: { call_id?: string };
  try { body = await req.json(); } catch { return json(400, { error: "bad json" }); }
  const { data: call } = await admin.from("call_sessions").select("id,room_name,status,initiator_id,kind,match_id,matches!inner(user_a,user_b,unmatched_at)")
    .eq("id", body.call_id ?? "").maybeSingle();
  // deno-lint-ignore no-explicit-any
  const m = (call as any)?.matches;
  if (!call || !m || m.unmatched_at || ![m.user_a, m.user_b].includes(user.id)) return json(403, { error: "Not your call" });
  const isInitiator = call.initiator_id === user.id;
  if (!(call.status === "active" || (call.status === "ringing" && isInitiator))) return json(409, { error: "Call is not active" });
  const other = m.user_a === user.id ? m.user_b : m.user_a;
  const { count } = await admin.from("blocks").select("blocker_id", { count: "exact", head: true })
    .or(`and(blocker_id.eq.${user.id},blocked_id.eq.${other}),and(blocker_id.eq.${other},blocked_id.eq.${user.id})`);
  if ((count ?? 0) > 0) return json(403, { error: "Not available" });

  const now = Math.floor(Date.now() / 1000);
  const jwt = await new SignJWT({
    video: { room: call.room_name, roomJoin: true, canPublish: true, canSubscribe: true, canPublishData: true,
             canPublishSources: call.kind === "video" ? ["microphone", "camera"] : ["microphone"] },
    name: user.id.slice(0, 8),
  }).setProtectedHeader({ alg: "HS256", typ: "JWT" }).setIssuer(key).setSubject(user.id)
    .setNotBefore(now - 5).setIssuedAt(now).setExpirationTime(now + 600).setJti(crypto.randomUUID())
    .sign(new TextEncoder().encode(secret));
  return json(200, { url, token: jwt, room: call.room_name, kind: call.kind });
});
