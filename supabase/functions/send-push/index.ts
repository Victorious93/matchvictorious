// Sends FCM HTTP v1 pushes for new matches / messages.
// Called by a DB trigger (pg_net) with {type, id}. It needs no caller secret because:
//  - it re-reads the row with the service role, and
//  - it atomically claims push_sent_at, so each row is notified at most once (replays are no-ops).
// FCM credentials: FCM_SERVICE_ACCOUNT function secret (service-account JSON). If missing, it skips gracefully.
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

const ok = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { "Content-Type": "application/json" } });
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

let cached: { token: string; exp: number } | null = null;
async function accessToken(sa: { client_email: string; private_key: string; token_uri?: string }) {
  if (cached && cached.exp > Date.now() / 1000 + 60) return cached.token;
  const key = await importPKCS8(sa.private_key, "RS256");
  const now = Math.floor(Date.now() / 1000);
  const jwt = await new SignJWT({ scope: "https://www.googleapis.com/auth/firebase.messaging" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" }).setIssuer(sa.client_email).setSubject(sa.client_email)
    .setAudience(sa.token_uri ?? "https://oauth2.googleapis.com/token").setIssuedAt(now).setExpirationTime(now + 3600).sign(key);
  const r = await fetch(sa.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  const j = await r.json();
  if (!r.ok) throw new Error("oauth " + r.status);
  cached = { token: j.access_token, exp: now + (j.expires_in ?? 3600) };
  return cached.token;
}

async function blocked(a: string, b: string) {
  const { count } = await admin.from("blocks").select("blocker_id", { count: "exact", head: true })
    .or(`and(blocker_id.eq.${a},blocked_id.eq.${b}),and(blocker_id.eq.${b},blocked_id.eq.${a})`);
  return (count ?? 0) > 0;
}
async function previews(uid: string) {
  const { data } = await admin.from("notification_prefs").select("show_previews").eq("user_id", uid).maybeSingle();
  return data?.show_previews === true;
}
async function nameOf(uid: string) {
  const { data } = await admin.from("profiles").select("display_name").eq("id", uid).maybeSingle();
  return data?.display_name ?? "Someone";
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return ok({ error: "POST only" }, 405);
  let body: { type?: string; id?: string };
  try { body = await req.json(); } catch { return ok({ error: "bad json" }, 400); }
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) return ok({ skipped: "FCM_SERVICE_ACCOUNT not configured" });
  const sa = JSON.parse(raw);

  // Build [{to, title, body, data}] after claiming the row (at most once).
  const out: { to: string; title: string; body: string; data: Record<string, string> }[] = [];
  if (body.type === "matches" && body.id) {
    const { data: m } = await admin.from("matches").update({ push_sent_at: new Date().toISOString() })
      .eq("id", body.id).is("push_sent_at", null).is("unmatched_at", null).select("id,user_a,user_b").maybeSingle();
    if (!m) return ok({ skipped: "already sent or gone" });
    if (await blocked(m.user_a, m.user_b)) return ok({ skipped: "blocked" });
    for (const [to, other] of [[m.user_a, m.user_b], [m.user_b, m.user_a]]) {
      const p = await previews(to);
      out.push({ to, title: "MatchVictorious", body: p ? `You matched with ${await nameOf(other)}` : "You have a new match", data: { type: "match", match_id: m.id } });
    }
  } else if (body.type === "messages" && body.id) {
    const { data: msg } = await admin.from("messages").update({ push_sent_at: new Date().toISOString() })
      .eq("id", Number(body.id)).is("push_sent_at", null).select("id,match_id,sender_id,body").maybeSingle();
    if (!msg) return ok({ skipped: "already sent or gone" });
    const { data: m } = await admin.from("matches").select("user_a,user_b,unmatched_at").eq("id", msg.match_id).maybeSingle();
    if (!m || m.unmatched_at) return ok({ skipped: "no active match" });
    const to = m.user_a === msg.sender_id ? m.user_b : m.user_a;
    if (await blocked(to, msg.sender_id)) return ok({ skipped: "blocked" });
    const p = await previews(to);
    const preview = msg.body.length > 80 ? msg.body.slice(0, 77) + "…" : msg.body;
    out.push({ to, title: p ? await nameOf(msg.sender_id) : "MatchVictorious", body: p ? preview : "New message", data: { type: "message", match_id: msg.match_id } });
  } else return ok({ error: "unknown type" }, 400);

  const token = await accessToken(sa);
  let sent = 0;
  for (const n of out) {
    const { data: toks } = await admin.from("device_tokens").select("token").eq("user_id", n.to);
    for (const { token: t } of toks ?? []) {
      const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ message: { token: t, notification: { title: n.title, body: n.body }, data: n.data,
          android: { priority: "HIGH", notification: { channel_id: "matches_messages", tag: n.data.match_id } } } }),
      });
      if (r.ok) sent++;
      else if (r.status === 404 || r.status === 400) {
        const e = await r.text();
        if (e.includes("UNREGISTERED") || e.includes("INVALID_ARGUMENT")) await admin.from("device_tokens").delete().eq("token", t);
      }
    }
  }
  return ok({ sent });
});
