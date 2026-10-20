// Server-side verification of Google Play subscription purchases for Victorious+.
// Secrets (Supabase function secrets, never in repo/app):
//   PLAY_SERVICE_ACCOUNT  - JSON key of a service account with access to the Play Developer API
//   PLAY_PACKAGE_NAME     - e.g. com.victorious.match
//   PLAY_PRODUCT_IDS      - optional comma list, default "victorious_plus"
// If unset -> 503 {disabled:true} and the app shows "not available yet".
// POST {purchase_token, product_id}  -> verifies with Google, binds token to caller, acknowledges, upserts entitlements.
// POST {} (no token)                 -> re-checks the caller's stored token (refresh status/expiry).
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info" };
const json = (s: number, b: unknown) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

async function accessToken(sa: { client_email: string; private_key: string; token_uri?: string }) {
  const now = Math.floor(Date.now() / 1000);
  const key = await importPKCS8(sa.private_key, "RS256");
  const uri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const jwt = await new SignJWT({ scope: "https://www.googleapis.com/auth/androidpublisher" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" }).setIssuer(sa.client_email).setSubject(sa.client_email)
    .setAudience(uri).setIssuedAt(now).setExpirationTime(now + 3600).sign(key);
  const r = await fetch(uri, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }) });
  if (!r.ok) throw new Error("oauth " + r.status);
  return (await r.json()).access_token as string;
}

const STATE: Record<string, string> = {
  SUBSCRIPTION_STATE_ACTIVE: "active", SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "grace", SUBSCRIPTION_STATE_ON_HOLD: "on_hold",
  SUBSCRIPTION_STATE_PAUSED: "paused", SUBSCRIPTION_STATE_CANCELED: "canceled", SUBSCRIPTION_STATE_EXPIRED: "expired",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json(405, { error: "POST only" });
  const saRaw = Deno.env.get("PLAY_SERVICE_ACCOUNT"), pkg = Deno.env.get("PLAY_PACKAGE_NAME");
  if (!saRaw || !pkg) return json(503, { disabled: true, error: "Purchases are not configured yet" });
  const products = (Deno.env.get("PLAY_PRODUCT_IDS") ?? "victorious_plus").split(",").map((s) => s.trim()).filter(Boolean);

  const bearer = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!bearer) return json(401, { error: "Missing bearer token" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  const { data: { user }, error } = await admin.auth.getUser(bearer);
  if (error || !user) return json(401, { error: "Invalid session" });

  let body: { purchase_token?: string; product_id?: string } = {};
  try { body = await req.json(); } catch { /* empty body = refresh */ }
  let token = body.purchase_token, productId = body.product_id;
  if (!token) {
    const { data } = await admin.from("entitlements").select("purchase_token,product_id").eq("user_id", user.id).maybeSingle();
    if (!data?.purchase_token) return json(200, { plus: false });
    token = data.purchase_token; productId = data.product_id;
  }
  if (!productId || !products.includes(productId) || token.length > 4096) return json(400, { error: "Unknown product" });

  // Token must not already belong to someone else
  const { data: owner } = await admin.from("entitlements").select("user_id").eq("purchase_token", token).maybeSingle();
  if (owner && owner.user_id !== user.id) return json(409, { error: "Purchase belongs to another account" });

  let gtoken: string;
  try { gtoken = await accessToken(JSON.parse(saRaw)); } catch (e) { return json(502, { error: "Play auth failed: " + (e as Error).message }); }
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(pkg)}/purchases`;
  const r = await fetch(`${base}/subscriptionsv2/tokens/${encodeURIComponent(token)}`, { headers: { Authorization: `Bearer ${gtoken}` } });
  if (r.status === 404 || r.status === 410) return json(400, { error: "Invalid purchase token" });
  if (!r.ok) return json(502, { error: "Play API " + r.status });
  // deno-lint-ignore no-explicit-any
  const sub: any = await r.json();
  // deno-lint-ignore no-explicit-any
  const line = (sub.lineItems ?? []).find((l: any) => l.productId === productId);
  if (!line) return json(400, { error: "Product mismatch" });
  // The app sets obfuscatedAccountId = Supabase user id at purchase; reject tokens bought for another account.
  const acct = sub.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  if (acct && acct !== user.id) return json(409, { error: "Purchase belongs to another account" });

  const status = STATE[sub.subscriptionState] ?? "expired";
  const expires = line.expiryTime ?? null;
  if (sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING" && (status === "active" || status === "grace")) {
    await fetch(`${base}/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}:acknowledge`,
      { method: "POST", headers: { Authorization: `Bearer ${gtoken}`, "Content-Type": "application/json" }, body: "{}" });
  }
  const { error: upErr } = await admin.from("entitlements").upsert({
    user_id: user.id, product_id: productId, status, expires_at: expires, auto_renewing: !!line.autoRenewingPlan?.autoRenewEnabled,
    purchase_token: token, order_id: sub.latestOrderId ?? null, updated_at: new Date().toISOString(),
  }, { onConflict: "user_id" });
  if (upErr) return json(500, { error: "Could not save entitlement" });
  const plus = ["active", "grace", "canceled"].includes(status) && (!expires || Date.parse(expires) > Date.now());
  return json(200, { plus, status, expires_at: expires });
});
