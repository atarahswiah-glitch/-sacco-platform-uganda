import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}
function clean(value: unknown, max = 500): string {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "missing_bearer_token" });

  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !key) return json(500, { error: "server_configuration_error" });

  const supabase = createClient(url, key, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: authData, error: authError } = await supabase.auth.getUser();
  if (authError || !authData.user) return json(401, { error: "invalid_or_expired_session" });
  const userId = authData.user.id;

  const { data: owner, error: ownerError } = await supabase
    .from("platform_owners").select("active").eq("user_id", userId).maybeSingle();
  if (ownerError) return json(500, { error: "owner_authorization_check_failed" });
  if (owner?.active !== true) return json(403, { error: "platform_owner_required" });

  let input: Record<string, unknown>;
  try {
    input = await req.json();
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_body");
  } catch {
    return json(400, { error: "invalid_json_body" });
  }

  const name = clean(input.name, 160);
  if (name.length < 2) return json(400, { error: "sacco_name_required" });

  const district = clean(input.district, 120);
  if (district.length < 2) return json(400, { error: "district_required" });
  const town = clean(input.town_village, 160);
  const location = clean(input.location, 300);
  const address = clean(input.physical_address, 300);
  const generalContact = clean(input.general_contact ?? input.contact, 50);
  const phone = clean(input.contact_phone, 50);
  const email = clean(input.contact_email, 254);

  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return json(400, { error: "invalid_contact_email" });
  }

  let expectedMemberCount = 50;
  if (input.expected_member_count !== undefined && input.expected_member_count !== null && input.expected_member_count !== "") {
    expectedMemberCount = Number(input.expected_member_count);
  }
  if (!Number.isSafeInteger(expectedMemberCount) || expectedMemberCount < 0 || expectedMemberCount > 1000000) {
    return json(400, { error: "expected_member_count_must_be_0_to_1000000" });
  }

  const currency = clean(input.currency || "UGX", 3).toUpperCase();
  if (currency !== "UGX") return json(400, { error: "unsupported_currency" });

  // Every new tenant starts pending. Only the authenticated owner activation
  // workflow may activate it after provisioning its first chairperson.
  const status = "trial";

  let memberLimit: number | null = null;
  if (input.member_limit !== undefined && input.member_limit !== null && input.member_limit !== "") {
    memberLimit = Number(input.member_limit);
    if (!Number.isSafeInteger(memberLimit) || memberLimit < 1 || memberLimit > 1000000) {
      return json(400, { error: "member_limit_must_be_1_to_1000000" });
    }
  }

  // sacco_code remains the existing technical identifier. mother_id is assigned
  // independently by a database trigger and is never supplied by the client.
  const saccoCode = "UG-" + crypto.randomUUID().replaceAll("-", "").slice(0, 8).toUpperCase();
  const { data: sacco, error: insertError } = await supabase
    .from("saccos")
    .insert({
      sacco_code: saccoCode,
      name,
      country_code: "UG",
      location_description: location || [town, district].filter(Boolean).join(", ") || null,
      district,
      town_village: town || null,
      physical_address: address || null,
      general_contact: generalContact || null,
      contact_phone: phone || null,
      contact_email: email || null,
      expected_member_count: expectedMemberCount,
      currency,
      member_limit: memberLimit,
      status,
      created_by: userId,
    })
    .select("id,name,sacco_code,mother_id,district,town_village,general_contact,expected_member_count,currency,status,created_at")
    .single();

  if (insertError || !sacco) {
    // Avoid exposing raw database errors to the mobile client.
    return json(500, { error: "sacco_creation_failed" });
  }
  return json(201, { ok: true, sacco });
});