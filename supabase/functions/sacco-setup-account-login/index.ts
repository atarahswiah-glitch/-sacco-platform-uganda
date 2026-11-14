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
function accountId(value: unknown): string {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}
function validAccountId(value: string): boolean {
  return /^[A-Z0-9][A-Z0-9-]{5,23}$/.test(value);
}
function validPin(value: unknown): value is string {
  return typeof value === "string" && /^[0-9]{8,12}$/.test(value);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "authentication_required" });

  let body: Record<string, unknown>;
  try {
    const value = await req.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("bad_body");
    body = value as Record<string, unknown>;
  } catch {
    return json(400, { error: "invalid_request" });
  }
  const id = accountId(body.account_id);
  const pin = body.pin;
  if (!validAccountId(id)) return json(400, { error: "invalid_account_id_format" });
  if (!validPin(pin)) return json(400, { error: "pin_must_be_8_to_12_digits" });

  const url = Deno.env.get("SUPABASE_URL");
  const publishableKey = keyFromJsonEnv("SUPABASE_PUBLISHABLE_KEYS") ?? Deno.env.get("SUPABASE_ANON_KEY");
  const secretKey = keyFromJsonEnv("SUPABASE_SECRET_KEYS") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !publishableKey || !secretKey) return json(503, { error: "account_setup_unavailable" });

  const userClient = createClient(url, publishableKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const { data: authData, error: authError } = await userClient.auth.getUser();
  if (authError || !authData.user) return json(401, { error: "authentication_required" });
  const userId = authData.user.id;

  const { data: owner, error: ownerError } = await admin
    .from("platform_owners").select("active").eq("user_id", userId).maybeSingle();
  if (ownerError) return json(503, { error: "account_setup_unavailable" });

  const { data: memberships, error: membershipError } = await admin
    .from("sacco_memberships").select("id").eq("user_id", userId).eq("status", "active").limit(1);
  if (membershipError) return json(503, { error: "account_setup_unavailable" });
  if (owner?.active !== true && (!memberships || memberships.length === 0)) {
    return json(403, { error: "active_sacco_membership_required" });
  }

  const { error: assignError } = await admin.rpc("assign_account_login_id", {
    p_user_id: userId,
    p_account_id: id,
  });
  if (assignError) {
    if (assignError.code === "23505") return json(409, { error: "account_id_unavailable_or_already_assigned" });
    return json(400, { error: "account_id_setup_failed" });
  }

  const { error: passwordError } = await admin.auth.admin.updateUserById(userId, { password: pin });
  if (passwordError) {
    return json(400, { error: "pin_setup_failed_retry_same_account_id" });
  }

  return json(200, { ok: true, account_id: id, pin_length: pin.length });
});