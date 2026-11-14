import { createClient } from "npm:@supabase/supabase-js@2.118.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function keyFromJsonEnv(name: string): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (typeof parsed.default === "string") return parsed.default;
    const first = Object.values(parsed).find((value) => typeof value === "string");
    return typeof first === "string" ? first : null;
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const length = Number(req.headers.get("content-length") ?? "0");
  if (length > 2048) return json(413, { error: "request_too_large" });

  let body: Record<string, unknown>;
  try {
    const value = await req.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("bad_body");
    body = value as Record<string, unknown>;
  } catch {
    return json(400, { error: "invalid_request" });
  }

  const id = typeof body.account_id === "string" ? body.account_id.trim().toUpperCase() : "";
  if (!/^[A-Z0-9][A-Z0-9-]{5,23}$/.test(id)) {
    return json(200, { ok: true, message: "If this account is eligible, a verification code has been sent." });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const publishableKey = keyFromJsonEnv("SUPABASE_PUBLISHABLE_KEYS") ?? Deno.env.get("SUPABASE_ANON_KEY");
  const secretKey = keyFromJsonEnv("SUPABASE_SECRET_KEYS") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !publishableKey || !secretKey) return json(503, { error: "verification_unavailable" });

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const authClient = createClient(url, publishableKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const { data: locked, error: lockError } = await admin.rpc("account_login_is_locked", { p_account_id: id });
  if (lockError) return json(503, { error: "verification_unavailable" });
  if (locked === true) return json(200, { ok: true, message: "If this account is eligible, a verification code has been sent." });

  const { data: userId, error: lookupError } = await admin.rpc("resolve_account_login_id", { p_account_id: id });
  if (lookupError) return json(503, { error: "verification_unavailable" });
  if (typeof userId !== "string" || !userId) {
    return json(200, { ok: true, message: "If this account is eligible, a verification code has been sent." });
  }

  const [{ data: owner, error: ownerError }, { data: needsActivation, error: activationError }] = await Promise.all([
    admin.from("platform_owners").select("active").eq("user_id", userId).maybeSingle(),
    admin.rpc("account_login_requires_activation", { p_account_id: id }),
  ]);
  if (ownerError || activationError) return json(503, { error: "verification_unavailable" });
  if (owner?.active !== true || needsActivation !== true) {
    return json(200, { ok: true, message: "If this account is eligible, a verification code has been sent." });
  }

  const { data: userResult, error: userError } = await admin.auth.admin.getUserById(userId);
  const email = userResult?.user?.email;
  const confirmedAt = userResult?.user?.email_confirmed_at;
  if (userError || !email || !confirmedAt) {
    return json(503, { error: "registered_email_not_ready_for_verification" });
  }

  const { error: otpError } = await authClient.auth.signInWithOtp({
    email,
    options: { shouldCreateUser: false },
  });
  if (otpError) return json(503, { error: "verification_email_could_not_be_sent" });

  return json(200, { ok: true, message: "If this account is eligible, a verification code has been sent." });
});
