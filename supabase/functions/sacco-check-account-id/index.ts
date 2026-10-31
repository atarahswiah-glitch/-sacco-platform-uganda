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
  } catch { return json(400, { error: "invalid_request" }); }

  const id = typeof body.account_id === "string" ? body.account_id.trim().toUpperCase() : "";
  if (!/^[A-Z0-9][A-Z0-9-]{5,23}$/.test(id)) return json(404, { error: "account_not_found" });

  const url = Deno.env.get("SUPABASE_URL");
  const secretKey = keyFromJsonEnv("SUPABASE_SECRET_KEYS") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !secretKey) return json(503, { error: "account_lookup_unavailable" });
  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const { data: locked, error: lockError } = await admin.rpc("account_login_is_locked", { p_account_id: id });
  if (lockError) return json(503, { error: "account_lookup_unavailable" });
  if (locked === true) return json(429, { error: "account_lookup_locked" });

  const { data: userId, error: lookupError } = await admin.rpc("resolve_account_login_id", { p_account_id: id });
  if (lookupError) return json(503, { error: "account_lookup_unavailable" });
  if (typeof userId !== "string" || !userId) return json(404, { error: "account_not_found" });

  const { data: owner, error: ownerError } = await admin
    .from("platform_owners").select("active").eq("user_id", userId).maybeSingle();
  if (ownerError) return json(503, { error: "account_lookup_unavailable" });

  let accountType = "sacco_member";
  if (owner?.active === true) {
    accountType = "platform_owner";
  } else {
    const { data: memberships, error: membershipError } = await admin
      .from("sacco_memberships").select("id").eq("user_id", userId).eq("status", "active").limit(2);
    if (membershipError) return json(503, { error: "account_lookup_unavailable" });
    if (!memberships || memberships.length === 0) return json(403, { error: "account_not_authorized" });
    if (memberships.length > 1) return json(409, { error: "multiple_active_sacco_memberships" });
  }

  const { data: requiresActivation, error: activationError } = await admin.rpc("account_login_requires_activation", { p_account_id: id });
  if (activationError) return json(503, { error: "account_lookup_unavailable" });
  return json(200, {
    ok: true,
    account_type: accountType,
    requires_activation: requiresActivation === true,
  });
});