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
function clean(value: unknown, max = 160): string {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}
function validUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
function temporaryPin(): string {
  const range = 9_000_000_000n;
  const max = (1n << 64n) - ((1n << 64n) % range);
  while (true) {
    const bytes = crypto.getRandomValues(new Uint8Array(8));
    let value = 0n;
    for (const byte of bytes) value = (value << 8n) | BigInt(byte);
    if (value < max) return (1_000_000_000n + (value % range)).toString();
  }
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
  } catch { return json(400, { error: "invalid_request" }); }

  const saccoId = clean(body.sacco_id, 64);
  const fullName = clean(body.chairperson_name, 160);
  const phone = clean(body.chairperson_phone, 50);
  const email = clean(body.chairperson_email, 254);
  const address = clean(body.chairperson_address, 300);
  if (!validUuid(saccoId)) return json(400, { error: "invalid_sacco_id" });
  if (fullName.length < 2) return json(400, { error: "chairperson_name_required" });
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json(400, { error: "invalid_chairperson_email" });

  const url = Deno.env.get("SUPABASE_URL");
  const publishableKey = keyFromJsonEnv("SUPABASE_PUBLISHABLE_KEYS") ?? Deno.env.get("SUPABASE_ANON_KEY");
  const secretKey = keyFromJsonEnv("SUPABASE_SECRET_KEYS") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !publishableKey || !secretKey) return json(503, { error: "tenant_activation_unavailable" });

  const caller = createClient(url, publishableKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: authData, error: authError } = await caller.auth.getUser();
  if (authError || !authData.user) return json(401, { error: "authentication_required" });
  const actorId = authData.user.id;

  const { data: owner, error: ownerError } = await admin
    .from("platform_owners").select("active").eq("user_id", actorId).maybeSingle();
  if (ownerError) return json(503, { error: "owner_authorization_check_failed" });
  if (owner?.active !== true) return json(403, { error: "platform_owner_required" });

  const { data: sacco, error: saccoError } = await admin
    .from("saccos").select("id,name,status").eq("id", saccoId).maybeSingle();
  if (saccoError) return json(503, { error: "tenant_lookup_failed" });
  if (!sacco) return json(404, { error: "sacco_not_found" });
  if (sacco.status !== "trial") return json(409, { error: "sacco_not_pending_owner_activation" });

  // The Platform Owner's activation authority is deliberately limited to the tenant
  // and its first chairperson. All later member accounts are provisioned by SACCO officers.
  const { data: alreadyHasChair, error: chairCheckError } = await admin
    .from("sacco_memberships").select("id").eq("sacco_id", saccoId)
    .eq("role", "chairperson").eq("status", "active").limit(1);
  if (chairCheckError) return json(503, { error: "chairperson_check_failed" });
  if (alreadyHasChair && alreadyHasChair.length > 0) return json(409, { error: "chairperson_already_activated" });

  const { data: memberResult, error: memberError } = await admin.rpc("register_sacco_member_record", {
    p_sacco_id: saccoId,
    p_actor_user_id: actorId,
    p_full_name: fullName,
    p_phone: phone || null,
    p_email: email || null,
    p_address: address || null,
    p_custom_fields: {},
  });
  if (memberError || !Array.isArray(memberResult) || !memberResult[0]?.member_row_id) {
    return json(400, { error: "chairperson_record_creation_failed" });
  }
  const memberRowId = memberResult[0].member_row_id;

  const pin = temporaryPin();
  const syntheticEmail = "login-" + crypto.randomUUID() + "@sacco-platform.invalid";
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email: syntheticEmail,
    password: pin,
    email_confirm: true,
    user_metadata: { display_name: fullName, account_role: "chairperson" },
  });
  if (createError || !created.user) {
    await admin.from("sacco_members").delete().eq("id", memberRowId).eq("sacco_id", saccoId);
    return json(503, { error: "chairperson_account_creation_failed" });
  }

  const { data: provisioned, error: provisionError } = await admin.rpc("provision_sacco_member_login", {
    p_sacco_id: saccoId,
    p_member_row_id: memberRowId,
    p_actor_user_id: actorId,
    p_new_user_id: created.user.id,
    p_role: "chairperson",
  });
  if (provisionError || !Array.isArray(provisioned) || !provisioned[0]?.generated_account_id) {
    await admin.auth.admin.deleteUser(created.user.id);
    await admin.from("sacco_members").delete().eq("id", memberRowId).eq("sacco_id", saccoId);
    return json(400, { error: "chairperson_account_provisioning_failed" });
  }

  const { data: activated, error: activateError } = await admin
    .from("saccos").update({ status: "active" }).eq("id", saccoId).eq("status", "trial")
    .select("id,name,mother_id,sacco_code,status").maybeSingle();
  if (activateError || !activated) {
    await admin.auth.admin.deleteUser(created.user.id);
    await admin.from("sacco_members").delete().eq("id", memberRowId).eq("sacco_id", saccoId);
    return json(503, { error: "tenant_activation_failed_retry" });
  }

  await admin.from("audit_logs").insert({
    sacco_id: saccoId,
    actor_user_id: actorId,
    event_type: "sacco.activated_with_chairperson",
    entity_type: "sacco",
    entity_id: saccoId,
    metadata: { chairperson_member_id: memberResult[0].generated_member_id ?? null },
  });

  return json(200, {
    ok: true,
    sacco: activated,
    chairperson_account: {
      account_id: provisioned[0].generated_account_id,
      temporary_pin: pin,
      member_id: provisioned[0].generated_member_id,
      role: "chairperson",
      must_activate: true,
    },
    note: "The SACCO is active. The chairperson must activate this account with the temporary PIN. Other member accounts are managed by the SACCO chairperson or authorised administrator.",
  });
});