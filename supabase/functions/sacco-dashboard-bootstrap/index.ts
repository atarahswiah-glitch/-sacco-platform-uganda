import { createClient } from "npm:@supabase/supabase-js@2.118.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json(405, { error: "method_not_allowed" });
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "missing_bearer_token" });

  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")
    ? (() => {
        try {
          const parsed = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")!);
          return typeof parsed.default === "string" ? parsed.default : Object.values(parsed).find((v) => typeof v === "string");
        } catch { return null; }
      })()
    : Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || typeof key !== "string") return json(503, { error: "dashboard_service_unavailable" });

  const supabase = createClient(url, key, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userResult, error: userError } = await supabase.auth.getUser();
  if (userError || !userResult.user) return json(401, { error: "invalid_or_expired_session" });
  const userId = userResult.user.id;

  const { data: owner, error: ownerError } = await supabase
    .from("platform_owners").select("active").eq("user_id", userId).maybeSingle();
  if (ownerError) return json(503, { error: "owner_authorization_check_failed" });
  if (owner?.active === true) {
    // Platform owner receives platform role only, never tenant financial/member records.
    return json(200, { dashboard_role: "platform_owner", user_id: userId });
  }

  const { data: memberships, error: membershipError } = await supabase
    .from("sacco_memberships").select("role,sacco_id")
    .eq("user_id", userId).eq("status", "active").limit(2);
  if (membershipError) return json(503, { error: "membership_authorization_check_failed" });
  if (!memberships?.length) return json(403, { error: "account_not_authorized" });
  if (memberships.length > 1) {
    return json(409, { error: "multiple_active_sacco_memberships", action: "resolve_account_membership" });
  }

  const membership = memberships[0];
  const { data: sacco, error: saccoError } = await supabase
    .from("saccos")
    .select("id,name,mother_id,currency,status,district,town_village,location_description,last_sync_at,location_sharing_enabled")
    .eq("id", membership.sacco_id).maybeSingle();
  if (saccoError || !sacco) return json(503, { error: "sacco_context_unavailable" });

  const { data: settings, error: settingsError } = await supabase
    .from("sacco_operating_settings")
    .select("operating_mode,ai_assistance_enabled,display_policy,show_savings_balances,show_contributions_deposits,show_loan_balances,show_repayments_totals")
    .eq("sacco_id", membership.sacco_id).maybeSingle();
  if (settingsError) return json(503, { error: "sacco_settings_unavailable" });

  const { data: member, error: memberError } = await supabase
    .from("sacco_members")
    .select("id,member_id,member_number,full_name,status")
    .eq("sacco_id", membership.sacco_id).eq("user_id", userId).maybeSingle();
  if (memberError) return json(503, { error: "member_context_unavailable" });

  return json(200, {
    dashboard_role: membership.role,
    user_id: userId,
    sacco,
    member: member ?? null,
    settings: settings ?? {
      operating_mode: "hybrid",
      ai_assistance_enabled: false,
      display_policy: "member_choice",
      show_savings_balances: true,
      show_contributions_deposits: true,
      show_loan_balances: true,
      show_repayments_totals: true,
    },
  });
});