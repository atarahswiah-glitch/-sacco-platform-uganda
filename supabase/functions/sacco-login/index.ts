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
  } catch { return null; }
}
function accountId(value: unknown): string {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}
function validAccountId(value: string): boolean {
  return value === "SP1" || /^[A-Z0-9][A-Z0-9-]{5,23}$/.test(value);
}
function validPin(value: unknown): value is string {
  return typeof value === "string" && /^[0-9]{8,12}$/.test(value);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const length = Number(req.headers.get("content-length") ?? "0");
  if (length > 4096) return json(413, { error: "request_too_large" });

  let body: Record<string, unknown>;
  try {
    const value = await req.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("bad_body");
    body = value as Record<string, unknown>;
  } catch { return json(400, { error: "invalid_request" }); }

  const id = accountId(body.account_id);
  const pin = body.pin;
  if (!validAccountId(id) || !validPin(pin)) return json(401, { error: "invalid_account_id_or_pin" });

  const url = Deno.env.get("SUPABASE_URL");
  const publishableKey = keyFromJsonEnv("SUPABASE_PUBLISHABLE_KEYS") ?? Deno.env.get("SUPABASE_ANON_KEY");
  const secretKey = keyFromJsonEnv("SUPABASE_SECRET_KEYS") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !publishableKey || !secretKey) return json(503, { error: "authentication_unavailable" });

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const authClient = createClient(url, publishableKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const { data: lockData, error: lockError } = await admin.rpc("account_login_is_locked", { p_account_id: id });
  if (lockError) return json(503, { error: "authentication_unavailable" });
  if (lockData === true) return json(401, { error: "invalid_account_id_or_pin" });

  const { data: userId, error: lookupError } = await admin.rpc("resolve_account_login_id", { p_account_id: id });
  if (lookupError) return json(503, { error: "authentication_unavailable" });
  if (typeof userId !== "string" || !userId) return json(401, { error: "invalid_account_id_or_pin" });

  const { data: userResult, error: userError } = await admin.auth.admin.getUserById(userId);
  const email = userResult?.user?.email;
  if (userError || !email) {
    await admin.rpc("record_account_login_failure", { p_account_id: id });
    return json(401, { error: "invalid_account_id_or_pin" });
  }

  const { data: authData, error: authError } = await authClient.auth.signInWithPassword({ email, password: pin });
  if (authError || !authData.session || !authData.user) {
    await admin.rpc("record_account_login_failure", { p_account_id: id });
    return json(401, { error: "invalid_account_id_or_pin" });
  }

  const { data: activationRequired, error: activationError } = await admin.rpc("account_login_requires_activation", { p_account_id: id });
  if (activationError) return json(503, { error: "authentication_unavailable" });
  if (activationRequired === true) return json(403, { error: "account_activation_required" });

  await admin.rpc("clear_account_login_failures", { p_account_id: id });
  return json(200, {
    access_token: authData.session.access_token,
    refresh_token: authData.session.refresh_token,
    token_type: authData.session.token_type,
    expires_in: authData.session.expires_in,
    expires_at: authData.session.expires_at,
    user_id: authData.user.id,
    account_id: id,
  });
});