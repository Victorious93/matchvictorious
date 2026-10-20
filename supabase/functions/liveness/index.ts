// Vendor liveness: Amazon Rekognition Face Liveness. Optional — without secrets returns {vendor:false} / 503 and the app
// uses the on-device ML Kit gesture challenge + moderator review instead.
// Secrets (Supabase function secrets only): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION (e.g. us-east-1),
//   AWS_LIVENESS_ROLE_ARN (role the app assumes briefly, allowed only rekognition:StartFaceLivenessSession),
//   LIVENESS_MIN_CONFIDENCE (optional, default 90).
// Privacy: no S3 output is configured, AuditImagesLimit=0, and the reference image returned by AWS is discarded.
// We store only {confidence, status}. No face templates or images are kept.
import { createClient } from "npm:@supabase/supabase-js@2";
import { AwsClient } from "npm:aws4fetch@1";

const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info" };
const json = (s: number, b: unknown) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json(405, { error: "POST only" });
  const key = Deno.env.get("AWS_ACCESS_KEY_ID"), secret = Deno.env.get("AWS_SECRET_ACCESS_KEY");
  const region = Deno.env.get("AWS_REGION") ?? "us-east-1", roleArn = Deno.env.get("AWS_LIVENESS_ROLE_ARN");
  const configured = !!(key && secret && roleArn);
  let body: { action?: string; check_id?: string } = {};
  try { body = await req.json(); } catch { /* empty */ }
  if (body.action === "config") return json(200, { vendor: configured, region });
  if (!configured) return json(503, { disabled: true, error: "Vendor liveness is not configured" });

  const bearer = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  const { data: { user } } = await admin.auth.getUser(bearer);
  if (!user) return json(401, { error: "Invalid session" });
  const { data: check } = await admin.from("liveness_checks").select("id,user_id,method,status,vendor_session_id,created_at")
    .eq("id", body.check_id ?? "").eq("user_id", user.id).maybeSingle();
  if (!check || check.method !== "vendor") return json(404, { error: "Unknown check" });
  if (Date.parse(check.created_at) < Date.now() - 30 * 60_000) return json(409, { error: "Check expired" });

  const aws = new AwsClient({ accessKeyId: key!, secretAccessKey: secret!, region, service: "rekognition" });
  const rek = (target: string, payload: unknown) => aws.fetch(`https://rekognition.${region}.amazonaws.com/`, {
    method: "POST", headers: { "Content-Type": "application/x-amz-json-1.1", "X-Amz-Target": `RekognitionService.${target}` },
    body: JSON.stringify(payload) });

  if (body.action === "start") {
    if (check.status !== "started" || check.vendor_session_id) return json(409, { error: "Check already used" });
    const r = await rek("CreateFaceLivenessSession", { ClientRequestToken: check.id, Settings: { AuditImagesLimit: 0 } });
    if (!r.ok) return json(502, { error: "Rekognition " + r.status });
    const { SessionId } = await r.json();
    await admin.from("liveness_checks").update({ vendor_session_id: SessionId }).eq("id", check.id);
    // Short-lived credentials that can ONLY stream this kind of session (session policy further narrows the role).
    const sts = new AwsClient({ accessKeyId: key!, secretAccessKey: secret!, region, service: "sts" });
    const policy = JSON.stringify({ Version: "2012-10-17", Statement: [{ Effect: "Allow", Action: "rekognition:StartFaceLivenessSession", Resource: "*" }] });
    const q = new URLSearchParams({ Action: "AssumeRole", Version: "2011-06-15", RoleArn: roleArn!, RoleSessionName: `liveness-${user.id.slice(0, 8)}`,
      DurationSeconds: "900", Policy: policy });
    const s = await sts.fetch(`https://sts.${region}.amazonaws.com/?${q}`, { headers: { Accept: "application/json" } });
    if (!s.ok) return json(502, { error: "STS " + s.status });
    const c = (await s.json()).AssumeRoleResponse.AssumeRoleResult.Credentials;
    return json(200, { session_id: SessionId, region, access_key_id: c.AccessKeyId, secret_access_key: c.SecretAccessKey,
      session_token: c.SessionToken, expiration: typeof c.Expiration === "number" ? c.Expiration : Math.floor(Date.parse(c.Expiration) / 1000) });
  }

  if (body.action === "result") {
    if (!check.vendor_session_id || check.status !== "started") return json(409, { error: "Nothing to check" });
    const r = await rek("GetFaceLivenessSessionResults", { SessionId: check.vendor_session_id });
    if (!r.ok) return json(502, { error: "Rekognition " + r.status });
    const res = await r.json();       // contains ReferenceImage bytes — intentionally not stored
    if (res.Status === "IN_PROGRESS" || res.Status === "CREATED") return json(202, { status: "in_progress" });
    const min = Number(Deno.env.get("LIVENESS_MIN_CONFIDENCE") ?? "90");
    const confidence = Math.round(Number(res.Confidence ?? 0) * 10) / 10;
    const passed = res.Status === "SUCCEEDED" && confidence >= min;
    await admin.from("liveness_checks").update({ status: passed ? "passed" : "failed", result: { confidence, vendor_status: res.Status },
      reviewed_at: new Date().toISOString() }).eq("id", check.id);
    if (passed) await admin.from("profiles").update({ verification_status: "verified", moderation_note: "You're verified." })
      .eq("id", user.id).eq("is_banned", false).neq("verification_status", "verified");
    return json(200, { passed, confidence });
  }
  return json(400, { error: "Unknown action" });
});
