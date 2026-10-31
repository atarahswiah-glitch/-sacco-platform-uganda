package com.sacco.platform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val SUPABASE_URL = "https://evsilqrluaudxiqrlpbs.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable__6_YClzfyTFxS3DUbuXlVQ_rXCbgJaa"
private val http = OkHttpClient()
private val Navy = Color(0xFF102A56)
private val Blue = Color(0xFF155EEF)
private val PaleBlue = Color(0xFFEAF1FF)
private val Canvas = Color(0xFFF6F8FC)
private val Muted = Color(0xFF667085)
private val Teal = Color(0xFF087E8B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(246, 248, 252)
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { SaccoApp() }
    }
}

private data class SaccoSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val accountId: String,
    val role: String,
    val saccoId: String? = null,
    val saccoName: String = "SACCO Platform Uganda",
    val motherId: String = "",
    val currency: String = "UGX",
    val operatingMode: String = "hybrid",
    val aiEnabled: Boolean = false,
    val displayPolicy: String = "member_choice",
    val showSavings: Boolean = true,
    val showContributions: Boolean = true,
    val showLoans: Boolean = true,
    val showRepayments: Boolean = true,
    val memberName: String = "",
    val memberId: String = "",
    val ownerProfileComplete: Boolean = true,
    val ownerDisplayName: String = "",
    val ownerPhone: String = "",
    val preferredLanguage: String = "en"
)

private data class Notice(val id: String, val title: String, val body: String, val category: String, val createdAt: String, val readAt: String?)
private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home), ACCOUNTS("Accounts", Icons.Filled.AccountBalanceWallet),
    POLICIES("Policies", Icons.Filled.Policy), ALERTS("Alerts", Icons.Filled.Notifications),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
private fun SaccoApp() {
    var session by remember { mutableStateOf<SaccoSession?>(null) }
    var welcomeStep by remember { mutableStateOf(false) }
    var loginStep by remember { mutableStateOf(false) }
    var pendingAccountId by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var feature by remember { mutableStateOf<String?>(null) }
    var notices by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var noticeError by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, background = Canvas, surface = Color.White, secondary = Teal)) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            val active = session
            if (active == null) {
                if (!welcomeStep) {
                    WelcomeScreen(onGetStarted = { welcomeStep = true })
                } else if (!loginStep) {
                    PlatformOwnerIdScreen(busy, statusMessage, onBack = { welcomeStep = false; statusMessage = "" }, onContinue = { id -> pendingAccountId = id.trim().uppercase(); loginStep = true; statusMessage = "" })
                } else {
                    LoginScreen(
                        initialAccountId = pendingAccountId,
                        onChangeAccountId = { loginStep = false; statusMessage = "" },
                        busy = busy,
                        message = statusMessage,
                        onLogin = { accountId, pin ->
                            scope.launch {
                                busy = true
                                statusMessage = ""
                                try {
                                    session = withContext(Dispatchers.IO) {
                                        loginAndLoadContext(accountId, pin)
                                    }
                                    destination = Destination.HOME
                                    feature = null
                                } catch (e: Exception) {
                                    statusMessage = e.message ?: "Sign-in failed. Please try again."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onActivate = { accountId, temporaryPin, newPin ->
                            scope.launch {
                                busy = true
                                statusMessage = ""
                                try {
                                    withContext(Dispatchers.IO) {
                                        activateAccount(accountId, temporaryPin, newPin)
                                    }
                                    statusMessage = "Account activated. Sign in with your new PIN."
                                } catch (e: Exception) {
                                    statusMessage = e.message ?: "Account activation failed. Please try again."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
            } else if (active.role == "platform_owner" && !active.ownerProfileComplete) {
                OwnerProfileScreen(active.ownerDisplayName, active.ownerPhone, active.preferredLanguage, busy, statusMessage) { name, phone, language ->
                    scope.launch {
                        busy = true; statusMessage = ""
                        try {
                            withContext(Dispatchers.IO) { saveOwnerProfile(active, name, phone, language) }
                            session = active.copy(ownerProfileComplete = true, ownerDisplayName = name.trim(), ownerPhone = phone.trim(), preferredLanguage = language)
                            statusMessage = "Your profile has been saved."
                        } catch (e: Exception) { statusMessage = e.message ?: "Could not save your profile." }
                        finally { busy = false }
                    }
                }
            } else {
                Scaffold(
                    containerColor = Canvas,
                    topBar = {
                        TopAppBarSacco(
                            title = feature ?: destination.label,
                            subtitle = if (active.role == "platform_owner") "SACCO Platform Uganda" else active.saccoName,
                            onBack = if (feature != null) ({ feature = null }) else null,
                            onLogout = { session = null; feature = null; statusMessage = "" }
                        )
                    },
                    bottomBar = {
                        if (feature == null) NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                            Destination.values().forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item,
                                    onClick = {
                                        destination = item
                                        feature = null
                                        if (item == Destination.ALERTS) {
                                            scope.launch {
                                                try { notices = withContext(Dispatchers.IO) { fetchNotifications(active) }; noticeError = "" }
                                                catch (e: Exception) { noticeError = e.message ?: "Could not load notifications." }
                                            }
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, fontSize = 11.sp) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when {
                            feature != null -> FeatureScreen(feature!!, active, onBack = { feature = null })
                            destination == Destination.HOME -> DashboardScreen(active, onOpen = { feature = it })
                            destination == Destination.ACCOUNTS -> AccountsScreen(active)
                            destination == Destination.POLICIES -> PoliciesScreen(active)
                            destination == Destination.ALERTS -> AlertsScreen(active, notices, noticeError, onRefresh = {
                                scope.launch {
                                    try { notices = withContext(Dispatchers.IO) { fetchNotifications(active) }; noticeError = "" }
                                    catch (e: Exception) { noticeError = e.message ?: "Could not load notifications." }
                                }
                            }, onMarkRead = { id ->
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { markNoticeRead(active, id) }
                                        notices = notices.map { if (it.id == id) it.copy(readAt = "read") else it }
                                    } catch (e: Exception) { noticeError = e.message ?: "Could not update notification." }
                                }
                            })
                            destination == Destination.SETTINGS -> SettingsScreen(active, onUpdate = { updated -> session = updated }, onMessage = { statusMessage = it })
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loginAndLoadContext(accountId: String, pin: String): SaccoSession {
    if (accountId.isBlank()) throw IOException("Enter your personal account ID.")
    if (!pin.matches(Regex("^[0-9]{8,12}$"))) throw IOException("Your PIN must contain 8–12 digits.")
    val payload = JSONObject().put("account_id", accountId.trim()).put("pin", pin)
    val loginRequest = Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-login")
        .header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    val loginJson = requestJson(loginRequest)
    val accessToken = loginJson.optString("access_token")
    if (accessToken.isBlank()) throw IOException("The authentication service did not return a session.")
    val refreshToken = loginJson.optString("refresh_token")
    val userId = loginJson.optString("user_id")
    val bootstrap = Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-dashboard-bootstrap")
        .header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Authorization", "Bearer $accessToken").get().build()
    val ctx = requestJson(bootstrap)
    val sacco = ctx.optJSONObject("sacco") ?: JSONObject()
    val member = ctx.optJSONObject("member") ?: JSONObject()
    val settings = ctx.optJSONObject("settings") ?: JSONObject()
    var ownerProfileComplete = true
    var ownerDisplayName = ""
    var ownerPhone = ""
    var preferredLanguage = "en"
    if (ctx.optString("dashboard_role") == "platform_owner") {
        val profileReq = Request.Builder().url("$SUPABASE_URL/rest/v1/user_profiles?select=display_name,phone,preferred_language&user_id=eq.$userId&limit=1").header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Authorization", "Bearer $accessToken").get().build()
        val profiles = JSONArray(requestJsonArray(profileReq))
        if (profiles.length() > 0) {
            val p = profiles.getJSONObject(0)
            ownerDisplayName = p.optString("display_name")
            ownerPhone = p.optString("phone")
            preferredLanguage = p.optString("preferred_language", "en")
        }
        ownerProfileComplete = ownerDisplayName.isNotBlank() && ownerPhone.isNotBlank()
    }
    return SaccoSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = userId,
        accountId = loginJson.optString("account_id", accountId.trim().uppercase()),
        role = ctx.optString("dashboard_role", "member"),
        saccoId = sacco.optString("id").takeIf { it.isNotBlank() },
        saccoName = sacco.optString("name", "SACCO Platform Uganda"),
        motherId = sacco.optString("mother_id"),
        currency = sacco.optString("currency", "UGX"),
        operatingMode = settings.optString("operating_mode", "hybrid"),
        aiEnabled = settings.optBoolean("ai_assistance_enabled", false),
        displayPolicy = settings.optString("display_policy", "member_choice"),
        showSavings = settings.optBoolean("show_savings_balances", true),
        showContributions = settings.optBoolean("show_contributions_deposits", true),
        showLoans = settings.optBoolean("show_loan_balances", true),
        showRepayments = settings.optBoolean("show_repayments_totals", true),
        memberName = member.optString("full_name"),
        memberId = member.optString("member_id", member.optString("member_number")),
        ownerProfileComplete = ownerProfileComplete,
        ownerDisplayName = ownerDisplayName,
        ownerPhone = ownerPhone,
        preferredLanguage = preferredLanguage
    )
}

private fun activateAccount(accountId: String, temporaryPin: String, newPin: String) {
    if (accountId.isBlank()) throw IOException("Enter your account ID.")
    if (!temporaryPin.matches(Regex("^[0-9]{8,12}$"))) throw IOException("Enter the temporary PIN issued to you.")
    if (!newPin.matches(Regex("^[0-9]{8,12}$"))) throw IOException("Your new PIN must contain 8–12 digits.")
    if (temporaryPin == newPin) throw IOException("Choose a new PIN different from the temporary PIN.")
    val payload = JSONObject()
        .put("account_id", accountId.trim())
        .put("temporary_pin", temporaryPin)
        .put("new_pin", newPin)
    val request = Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-activate-account")
        .header("apikey", SUPABASE_PUBLISHABLE_KEY)
        .header("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
    requestJson(request)
}

private fun requestJsonArray(request: Request): String {
    http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("Could not load your profile (" + response.code + ").")
        return body.ifBlank { "[]" }
    }
}

private suspend fun saveOwnerProfile(session: SaccoSession, name: String, phone: String, language: String) = withContext(Dispatchers.IO) {
    if (session.role != "platform_owner") throw IOException("This account is not permitted to complete this profile.")
    if (name.trim().length < 2) throw IOException("Enter your full name.")
    if (phone.trim().length < 7) throw IOException("Enter a valid contact phone number.")
    if (language !in listOf("en", "lg")) throw IOException("Choose a supported language.")
    val payload = JSONObject().put("user_id", session.userId).put("display_name", name.trim()).put("phone", phone.trim()).put("preferred_language", language)
    apiRequest(session, "/rest/v1/user_profiles?on_conflict=user_id", "POST", payload, "resolution=merge-duplicates,return=minimal")
}

private fun requestJson(request: Request): JSONObject {
    http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val error = try { JSONObject(body).optString("error", "request_failed") } catch (_: Exception) { "request_failed" }
            throw IOException(friendlyError(error, response.code))
        }
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }
}
private fun friendlyError(code: String, httpCode: Int): String = when (code) {
    "invalid_account_id_or_pin" -> "The account ID or PIN is incorrect, or sign-in is temporarily locked."
    "account_activation_required" -> "This is a new account. Select “I have a temporary PIN — activate account” and choose your private PIN."
    "invalid_activation_details" -> "The Account ID or temporary PIN is incorrect, or activation is temporarily locked."
    "account_already_activated" -> "This account is already activated. Sign in with your current PIN."
    "activation_completion_failed_retry" -> "Your PIN activation could not be finalised. The temporary PIN has been restored; retry activation."
    "activation_recovery_required_contact_administrator" -> "Activation needs administrator recovery. Do not keep retrying; contact your SACCO administrator."
    "account_not_authorized" -> "This account has no active SACCO role. Contact your SACCO administrator."
    "multiple_active_sacco_memberships" -> "This account is linked to more than one active SACCO. Contact support to resolve it."
    "sacco_context_unavailable" -> "Your SACCO profile could not be loaded. Please try again."
    "authentication_unavailable", "dashboard_service_unavailable" -> "The sign-in service is temporarily unavailable. Try again later."
    else -> "Request failed ($httpCode). Please try again or contact support."
}

private fun apiRequest(session: SaccoSession, path: String, method: String = "GET", json: JSONObject? = null, prefer: String? = null): String {
    val builder = Request.Builder().url("$SUPABASE_URL$path")
        .header("apikey", SUPABASE_PUBLISHABLE_KEY)
        .header("Authorization", "Bearer ${session.accessToken}")
        .header("Content-Type", "application/json")
    if (prefer != null) builder.header("Prefer", prefer)
    val body = json?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
    builder.method(method, when (method) { "GET", "HEAD" -> null; "POST" -> body ?: "{}".toRequestBody("application/json".toMediaType()); "PATCH" -> body ?: "{}".toRequestBody("application/json".toMediaType()); else -> body })
    http.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val code = try { JSONObject(text).optString("message", JSONObject(text).optString("error", "request_failed")) } catch (_: Exception) { "request_failed" }
            throw IOException(if (response.code == 401) "Your session has expired. Sign in again." else "Update failed (${response.code}): $code")
        }
        return text
    }
}


private data class SaccoMemberRow(val id:String,val memberId:String,val memberNumber:String,val fullName:String,val phone:String,val hasLogin:Boolean)
private data class OneTimeAccount(val accountId:String,val temporaryPin:String,val memberName:String,val role:String)
private suspend fun fetchSaccoMembers(session:SaccoSession):List<SaccoMemberRow> = withContext(Dispatchers.IO) {
 val sid=session.saccoId ?: throw IOException("No SACCO context.")
 val raw=apiRequest(session,"/rest/v1/sacco_members?select=id,member_id,member_number,full_name,phone,user_id&status=eq.active&sacco_id=eq."+sid+"&order=created_at.desc&limit=250")
 val a=JSONArray(raw)
 (0 until a.length()).map { i -> val r=a.getJSONObject(i); val uid=r.optString("user_id"); SaccoMemberRow(r.optString("id"),r.optString("member_id"),r.optString("member_number"),r.optString("full_name"),r.optString("phone"),uid.isNotBlank()&&uid!="null") }
}
private suspend fun provisionMemberAccount(session:SaccoSession,m:SaccoMemberRow,role:String):OneTimeAccount = withContext(Dispatchers.IO) {
 val sid=session.saccoId ?: throw IOException("No SACCO context.")
 val body=JSONObject().put("sacco_id",sid).put("member_row_id",m.id).put("role",role)
 val req=Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-provision-member-account").header("apikey",SUPABASE_PUBLISHABLE_KEY).header("Authorization","Bearer "+session.accessToken).header("Content-Type","application/json").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
 val obj=requestJson(req).optJSONObject("account") ?: throw IOException("Incomplete account response.")
 val result=OneTimeAccount(obj.optString("account_id"),obj.optString("temporary_pin"),m.fullName,obj.optString("role",role))
 if(result.accountId.isBlank()||result.temporaryPin.isBlank()) throw IOException("Missing one-time activation details. Contact support before retrying.")
 result
}


private suspend fun registerSaccoMember(session:SaccoSession,fullName:String,phone:String,email:String,address:String):SaccoMemberRow = withContext(Dispatchers.IO) {
 val sid=session.saccoId ?: throw IOException("No SACCO context.")
 val body=JSONObject().put("sacco_id",sid).put("full_name",fullName.trim()).put("phone",phone.trim()).put("email",email.trim()).put("address",address.trim()).put("custom_fields",JSONObject())
 val req=Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-register-member").header("apikey",SUPABASE_PUBLISHABLE_KEY).header("Authorization","Bearer "+session.accessToken).header("Content-Type","application/json").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
 val member=requestJson(req).optJSONObject("member") ?: throw IOException("Member registration returned an incomplete response.")
 SaccoMemberRow(member.optString("id"),member.optString("member_id"),member.optString("member_number"),member.optString("full_name",fullName),phone,false)
}

private suspend fun fetchNotifications(session: SaccoSession): List<Notice> = withContext(Dispatchers.IO) {
    val text = apiRequest(session, "/rest/v1/sacco_notifications?select=id,title,body,category,created_at,read_at&order=created_at.desc&limit=50")
    val array = JSONArray(text)
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Notice(o.optString("id"), o.optString("title"), o.optString("body"), o.optString("category"), o.optString("created_at"), o.optString("read_at").takeIf { it.isNotBlank() && it != "null" })
    }
}
private suspend fun markNoticeRead(session: SaccoSession, id: String) = withContext(Dispatchers.IO) {
    apiRequest(session, "/rest/v1/sacco_notifications?id=eq.$id", "PATCH", JSONObject().put("read_at", java.time.Instant.now().toString()), "return=minimal")
}
private suspend fun updateSettings(session: SaccoSession, changes: JSONObject) = withContext(Dispatchers.IO) {
    val saccoId = session.saccoId ?: throw IOException("This account has no SACCO settings context.")
    changes.put("updated_by", session.userId)
    apiRequest(session, "/rest/v1/sacco_operating_settings?sacco_id=eq.$saccoId", "PATCH", changes, "return=minimal")
}
private suspend fun savePersonalVisibility(session: SaccoSession, hidden: Boolean) = withContext(Dispatchers.IO) {
    val saccoId = session.saccoId ?: throw IOException("This account has no SACCO settings context.")
    val payload = JSONObject().put("sacco_id", saccoId).put("user_id", session.userId).put("hide_money_amounts", hidden)
    apiRequest(session, "/rest/v1/sacco_member_display_preferences?on_conflict=sacco_id,user_id", "POST", payload, "resolution=merge-duplicates,return=minimal")
}

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Canvas).padding(horizontal = 26.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(94.dp).background(Blue, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(30.dp))
        Text("Welcome to", color = Muted, fontSize = 17.sp)
        Text("SACCO Platform", color = Navy, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Uganda", color = Blue, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text("One secure platform for your SACCOs.", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(38.dp))
        Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Get Started", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun PlatformOwnerIdScreen(
    busy: Boolean,
    message: String,
    onBack: () -> Unit,
    onContinue: (String) -> Unit
) {
    var ownerId by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Canvas).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Back") }
        Spacer(Modifier.height(18.dp))
        Text("Enter your Account ID", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Enter your personal account ID to continue. Your role and workspace will open automatically after secure sign-in.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = ownerId,
            onValueChange = { ownerId = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(32) },
            label = { Text("Account ID") },
            leadingIcon = { Icon(Icons.Filled.Badge, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onContinue(ownerId) },
            enabled = !busy && ownerId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(15.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LoginScreen(
    initialAccountId: String,
    onChangeAccountId: () -> Unit,
    busy: Boolean,
    message: String,
    onLogin: (String, String) -> Unit,
    onActivate: (String, String, String) -> Unit
) {
    var accountId by remember(initialAccountId) { mutableStateOf(initialAccountId) }
    var pin by remember { mutableStateOf("") }
    var temporaryPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var activationMode by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Canvas).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(68.dp).background(Blue, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("SACCO Platform", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Uganda", fontSize = 17.sp, color = Blue, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(if (activationMode) "Activate your new account" else "Secure access to your SACCO", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(value = accountId, onValueChange = { accountId = it.uppercase().take(24) }, label = { Text("Account ID") }, leadingIcon = { Icon(Icons.Filled.Badge, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        TextButton(onClick = onChangeAccountId, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("Change Account ID") }
        Spacer(Modifier.height(14.dp))
        if (!activationMode) {
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN (8–12 digits)") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, trailingIcon = { IconButton(onClick = { showPin = !showPin }) { Icon(if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } }, visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        } else {
            OutlinedTextField(value = temporaryPin, onValueChange = { temporaryPin = it.filter(Char::isDigit).take(12) }, label = { Text("Temporary PIN issued to you") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(12) }, label = { Text("Choose your new PIN (8–12 digits)") }, leadingIcon = { Icon(Icons.Filled.VpnKey, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) }, label = { Text("Confirm new PIN") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, color = if (message.startsWith("Account activated")) Teal else MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                if (activationMode) onActivate(accountId, temporaryPin, newPin)
                else onLogin(accountId, pin)
            },
            enabled = !busy && accountId.isNotBlank() && if (activationMode) {
                temporaryPin.length in 8..12 && newPin.length in 8..12 && newPin == confirmPin && newPin != temporaryPin
            } else pin.length in 8..12,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(15.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (activationMode) "Activate account" else "Sign in securely", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = {
            activationMode = !activationMode
            pin = ""
            temporaryPin = ""
            newPin = ""
            confirmPin = ""
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (activationMode) "Back to sign in" else "I have a temporary PIN — activate account")
        }
        Spacer(Modifier.height(8.dp))
        Text("Forgot your PIN? Contact your SACCO administrator for the verified recovery process.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun OwnerProfileScreen(initialName: String, initialPhone: String, initialLanguage: String, busy: Boolean, message: String, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var language by remember { mutableStateOf(initialLanguage) }
    Column(Modifier.fillMaxSize().background(Canvas).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Complete your profile", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Your account has been securely verified. Complete your name, contact number, and language to continue to your workspace.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(name, { name = it.take(160) }, label = { Text("Full name") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(phone, { phone = it.take(30) }, label = { Text("Contact phone number") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text("Preferred language", color = Navy, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = language == "en", onClick = { language = "en" }, label = { Text("English") }, enabled = !busy)
            FilterChip(selected = language == "lg", onClick = { language = "lg" }, label = { Text("Luganda") }, enabled = !busy)
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(message, color = if (message.contains("saved", true)) Teal else MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onSave(name, phone, language) }, enabled = !busy && name.trim().length >= 2 && phone.trim().length >= 7, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Save profile and continue")
        }
    }
}

@Composable
private fun TopAppBarSacco(title: String, subtitle: String, onBack: (() -> Unit)?, onLogout: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Navy) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, contentDescription = "Sign out", tint = Navy) }
        }
    }
}

private data class DashboardCard(val title: String, val subtitle: String, val icon: ImageVector, val color: Color)
private fun cardsFor(role: String): List<DashboardCard> = if (role == "platform_owner") listOf(
    DashboardCard("SACCO Directory", "Register and manage tenants", Icons.Filled.Domain, Blue),
    DashboardCard("Platform Health", "Connectivity and sync status", Icons.Filled.MonitorHeart, Teal),
    DashboardCard("Activation", "Tenant status and limits", Icons.Filled.VerifiedUser, Color(0xFF8B5CF6)),
    DashboardCard("Location Sharing", "Authorized last-reported locations", Icons.Filled.LocationOn, Color(0xFFB54708)),
    DashboardCard("Platform Activity", "Technical audit overview", Icons.Filled.FactCheck, Color(0xFF0E9384))
) else when (role) {
    "chairperson", "sacco_admin" -> listOf(DashboardCard("Members", "Register and manage member access", Icons.Filled.Groups, Blue), DashboardCard("Accounts", "Authorised account records", Icons.Filled.AccountBalanceWallet, Teal), DashboardCard("Contributions", "Savings and deposits", Icons.Filled.Savings, Color(0xFF8B5CF6)), DashboardCard("Loans", "Loan applications and approvals", Icons.Filled.AccountBalance, Color(0xFFB54708)), DashboardCard("Meetings", "Calendar and attendance", Icons.Filled.Event, Color(0xFF0E9384)), DashboardCard("Policies", "Bylaws and SACCO rules", Icons.Filled.Policy, Color(0xFF475467)))
    "treasurer" -> listOf(DashboardCard("Accounts", "Authorized account records", Icons.Filled.AccountBalanceWallet, Blue), DashboardCard("Contributions", "Deposits and contributions", Icons.Filled.Savings, Teal), DashboardCard("Receipts", "Issue and review receipts", Icons.Filled.ReceiptLong, Color(0xFF8B5CF6)), DashboardCard("Reports", "Financial reports", Icons.Filled.Assessment, Color(0xFFB54708)), DashboardCard("Members", "Member directory", Icons.Filled.Groups, Color(0xFF0E9384)))
    "credit_officer" -> listOf(DashboardCard("Loan Applications", "Review submitted requests", Icons.Filled.RequestQuote, Blue), DashboardCard("Loans", "Authorized loan records", Icons.Filled.AccountBalance, Teal), DashboardCard("Repayments", "Track loan repayments", Icons.Filled.Payments, Color(0xFF8B5CF6)), DashboardCard("Members", "Member directory", Icons.Filled.Groups, Color(0xFF0E9384)), DashboardCard("Reports", "Credit and repayment reports", Icons.Filled.Assessment, Color(0xFFB54708)))
    "secretary" -> listOf(DashboardCard("Members", "Member directory", Icons.Filled.Groups, Blue), DashboardCard("Meetings", "Calendar and attendance", Icons.Filled.Event, Teal), DashboardCard("Policies", "Approved bylaws and rules", Icons.Filled.Policy, Color(0xFF8B5CF6)), DashboardCard("Proposals & Votes", "Member decisions", Icons.Filled.HowToVote, Color(0xFFB54708)), DashboardCard("Documents", "SACCO files and minutes", Icons.Filled.Folder, Color(0xFF0E9384)))
    else -> listOf(DashboardCard("My Accounts", "Your account information", Icons.Filled.AccountBalanceWallet, Blue), DashboardCard("Savings & Contributions", "Your own records", Icons.Filled.Savings, Teal), DashboardCard("My Loans", "Your applications and repayments", Icons.Filled.AccountBalance, Color(0xFF8B5CF6)), DashboardCard("Receipts & History", "Your own transaction history", Icons.Filled.ReceiptLong, Color(0xFFB54708)), DashboardCard("Meetings", "Meetings and attendance", Icons.Filled.Event, Color(0xFF0E9384)), DashboardCard("Documents", "Approved SACCO documents", Icons.Filled.Folder, Color(0xFF475467)))
}

@Composable
private fun DashboardScreen(session: SaccoSession, onOpen: (String) -> Unit) {
    val cards = cardsFor(session.role)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Surface(color = Navy, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text(if (session.role == "platform_owner") "Platform overview" else "Welcome${if (session.memberName.isNotBlank()) ", ${session.memberName.substringBefore(' ')}" else " back"}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(if (session.role == "platform_owner") "Monitor tenant health without accessing SACCO financial records." else session.saccoName, color = Color(0xFFD6E4FF), fontSize = 14.sp)
                    if (session.motherId.isNotBlank()) { Spacer(Modifier.height(14.dp)); Text("SACCO ID  •  ${session.motherId}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item { Text("Quick access", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(cards.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { index, card ->
                    DashboardTile(card, Modifier.weight(1f).height(if ((cards.indexOf(card) + index) % 3 == 0) 164.dp else 142.dp)) { onOpen(card.title) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, null, tint = Teal, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Privacy is built in", color = Navy, fontWeight = FontWeight.SemiBold); Text("Your access depends on your assigned role and SACCO permissions.", color = Muted, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun DashboardTile(card: DashboardCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(44.dp).background(card.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(card.icon, null, tint = card.color, modifier = Modifier.size(25.dp)) }
            Column {
                Text(card.title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(card.subtitle, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun AccountsScreen(session: SaccoSession) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading("Account & access", "Your role is assigned by your SACCO. There is no role-selection screen after sign-in.") }
        item { InfoCard("Account ID", session.accountId, Icons.Filled.Badge) }
        item { InfoCard("Assigned role", session.role.replace('_', ' ').replaceFirstChar { it.uppercase() }, Icons.Filled.VerifiedUser) }
        if (session.saccoId != null) item { InfoCard("SACCO", session.saccoName, Icons.Filled.Domain, session.motherId) }
        if (session.memberId.isNotBlank()) item { InfoCard("Member ID", session.memberId, Icons.Filled.Person, session.memberName) }
        item { Text("PIN recovery", color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        item { Text("PIN recovery must be verified and approved. An administrator must never see or choose your new PIN. The recovery screen will be connected after the secure account lifecycle is completed.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
    }
}

@Composable
private fun PoliciesScreen(session: SaccoSession) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Policies & bylaws", "Approved rules are shared with members. Editing is reserved for authorized officers and must be audited.")
        InfoCard("Operating mode", session.operatingMode.replaceFirstChar { it.uppercase() }, Icons.Filled.Tune)
        InfoCard("AI assistance", if (session.aiEnabled) "Enabled by this SACCO" else "Not enabled", Icons.Filled.AutoAwesome)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Policy document library", color = Navy, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("The approved constitution, bylaws, and rule documents will appear here once the document-library endpoint is connected. This screen does not invent or enforce unconfigured rules.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun AlertsScreen(session: SaccoSession, notices: List<Notice>, error: String, onRefresh: () -> Unit, onMarkRead: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Notifications", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Private to your account", color = Muted, fontSize = 12.sp) }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh notifications", tint = Blue) }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (notices.isEmpty()) {
            EmptyState(Icons.Filled.NotificationsNone, "No notifications yet", "Meeting reminders, policy updates, and account alerts will appear here when sent to your account.")
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(notices, key = { it.id }) { n ->
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(38.dp).background(if (n.readAt == null) PaleBlue else Color(0xFFF2F4F7), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Notifications, null, tint = if (n.readAt == null) Blue else Muted) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, color = Navy, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(n.body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${n.category.replaceFirstChar { it.uppercase() }} • ${n.createdAt.take(10)}", color = Muted, fontSize = 10.sp)
                            if (n.readAt == null) TextButton(onClick = { onMarkRead(n.id) }, contentPadding = PaddingValues(0.dp)) { Text("Mark as read") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(session: SaccoSession, onUpdate: (SaccoSession) -> Unit, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var hiddenAmounts by remember { mutableStateOf(false) }
    var personalLoaded by remember { mutableStateOf(false) }
    val isAdmin = session.role in listOf("chairperson", "sacco_admin")
    LaunchedEffect(session.saccoId, session.userId) {
        if (session.saccoId != null) {
            try {
                val text = withContext(Dispatchers.IO) { apiRequest(session, "/rest/v1/sacco_member_display_preferences?select=hide_money_amounts&sacco_id=eq.${session.saccoId}&user_id=eq.${session.userId}&limit=1") }
                val arr = JSONArray(text)
                if (arr.length() > 0) hiddenAmounts = arr.getJSONObject(0).optBoolean("hide_money_amounts", false)
            } catch (_: Exception) { }
            personalLoaded = true
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionHeading("Settings", "Workflow, financial display, notifications, and account preferences are separate controls.") }
        item {
            SettingsCard("Workflow & Operations", "Applies to routine tasks. Required human approvals are never bypassed.") {
                Text("Operating mode", color = Navy, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("automatic", "manual", "hybrid").forEach { mode ->
                        FilterChip(selected = session.operatingMode == mode, onClick = {
                            if (!isAdmin) onMessage("Only the chairperson or SACCO administrator can change workflow mode.")
                            else scope.launch {
                                busy = true
                                try { updateSettings(session, JSONObject().put("operating_mode", mode)); onUpdate(session.copy(operatingMode = mode)); onMessage("Workflow mode updated and saved.") }
                                catch (e: Exception) { onMessage(e.message ?: "Could not save setting.") }
                                finally { busy = false }
                            }
                        }, label = { Text(mode.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }, enabled = !busy)
                    }
                }
                SettingSwitch("Optional AI assistance", "Separate from the operating mode.", session.aiEnabled, enabled = isAdmin && !busy) { enabled ->
                    scope.launch { busy = true; try { updateSettings(session, JSONObject().put("ai_assistance_enabled", enabled)); onUpdate(session.copy(aiEnabled = enabled)); onMessage("AI setting saved. An external AI service is not automatically connected by this toggle.") } catch (e: Exception) { onMessage(e.message ?: "Could not save setting.") } finally { busy = false } }
                }
            }
        }
        item {
            SettingsCard("Financial Display & Privacy", "Display masking does not change financial records or grant access to another member's data.") {
                Text("SACCO-wide display policy", color = Navy, fontWeight = FontWeight.SemiBold)
                listOf("member_choice" to "Member chooses", "masked_default" to "Masked by default", "masked_locked" to "Masking locked").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = isAdmin && !busy) {
                        scope.launch { busy = true; try { updateSettings(session, JSONObject().put("display_policy", value)); onUpdate(session.copy(displayPolicy = value)); onMessage("Financial display policy saved.") } catch (e: Exception) { onMessage(e.message ?: "Could not save setting.") } finally { busy = false } }
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = session.displayPolicy == value, onClick = null, enabled = isAdmin && !busy)
                        Spacer(Modifier.width(6.dp)); Text(label, color = Navy, fontSize = 14.sp)
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE4E7EC))
                Text("Amounts available on member dashboards", color = Navy, fontWeight = FontWeight.SemiBold)
                SettingSwitch("Savings balances", "Show amounts on the dashboard.", session.showSavings, enabled = isAdmin && !busy) { v -> saveSetting(session, "show_savings_balances", v, { onUpdate(session.copy(showSavings = v)) }, onMessage, scope) { busy = it } }
                SettingSwitch("Contributions & deposits", "Show amounts on the dashboard.", session.showContributions, enabled = isAdmin && !busy) { v -> saveSetting(session, "show_contributions_deposits", v, { onUpdate(session.copy(showContributions = v)) }, onMessage, scope) { busy = it } }
                SettingSwitch("Loan balances", "Show amounts on the dashboard.", session.showLoans, enabled = isAdmin && !busy) { v -> saveSetting(session, "show_loan_balances", v, { onUpdate(session.copy(showLoans = v)) }, onMessage, scope) { busy = it } }
                SettingSwitch("Repayment totals", "Show amounts on the dashboard.", session.showRepayments, enabled = isAdmin && !busy) { v -> saveSetting(session, "show_repayments_totals", v, { onUpdate(session.copy(showRepayments = v)) }, onMessage, scope) { busy = it } }
                if (!isAdmin) Text("Only the chairperson or SACCO administrator can change SACCO-wide display policy.", color = Muted, fontSize = 12.sp)
                Divider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE4E7EC))
                SettingSwitch("Hide my own amounts", "Personal preference, where SACCO policy permits.", hiddenAmounts, enabled = personalLoaded && !busy && session.role != "platform_owner") { v ->
                    scope.launch { busy = true; try { savePersonalVisibility(session, v); hiddenAmounts = v; onMessage("Your personal display preference was saved.") } catch (e: Exception) { onMessage(e.message ?: "Could not save preference.") } finally { busy = false } }
                }
            }
        }
        item {
            SettingsCard("Account & Security", "Your role is assigned by your SACCO.") {
                InfoLine("Account ID", session.accountId)
                InfoLine("Role", session.role.replace('_', ' ').replaceFirstChar { it.uppercase() })
                Text("Phone changes require a request and approval. PIN recovery must be verified, and an administrator must never see your new PIN.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        item {
            SettingsCard("App preferences", "Language and appearance options will be connected after the core account flow is verified.") {
                InfoLine("Interface", "English (initial)")
                InfoLine("Appearance", "Blue & white")
            }
        }
        item { if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}

private fun saveSetting(session: SaccoSession, key: String, value: Boolean, success: () -> Unit, onMessage: (String) -> Unit, scope: kotlinx.coroutines.CoroutineScope, setBusy: (Boolean) -> Unit) {
    scope.launch { setBusy(true); try { updateSettings(session, JSONObject().put(key, value)); success(); onMessage("Setting saved.") } catch (e: Exception) { onMessage(e.message ?: "Could not save setting.") } finally { setBusy(false) } }
}


@Composable
private fun MemberManagementScreen(session:SaccoSession,onBack:()->Unit) {
 var members by remember { mutableStateOf<List<SaccoMemberRow>>(emptyList()) }
 var loading by remember { mutableStateOf(true) }
 var error by remember { mutableStateOf("") }
 var busyId by remember { mutableStateOf("") }
 var role by remember { mutableStateOf("member") }
 var menu by remember { mutableStateOf(false) }
 var account by remember { mutableStateOf<OneTimeAccount?>(null) }
 var adding by remember { mutableStateOf(false) }
 var registering by remember { mutableStateOf(false) }
 var newName by remember { mutableStateOf("") }
 var newPhone by remember { mutableStateOf("") }
 var newEmail by remember { mutableStateOf("") }
 var newAddress by remember { mutableStateOf("") }
 var reload by remember { mutableIntStateOf(0) }
 val scope=rememberCoroutineScope()
 val canManage=session.role=="chairperson"||session.role=="sacco_admin"
 val roles=listOf("member" to "Ordinary member","chairperson" to "Chairperson","treasurer" to "Treasurer","secretary" to "Secretary","sacco_admin" to "SACCO administrator","credit_officer" to "Credit / loans officer","assistant" to "Assistant")
 LaunchedEffect(session.saccoId,reload) { loading=true; error=""; try { members=fetchSaccoMembers(session) } catch(e:Exception){error=e.message ?: "Could not load members."} finally{loading=false} }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)){Text("SACCO members",color=Navy,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Active members • "+session.saccoName,color=Muted,fontSize=12.sp)}
   IconButton(onClick={reload++},enabled=!loading){Icon(Icons.Filled.Refresh,"Refresh")}
  }
  if(!canManage) Text("Only the chairperson or an authorised SACCO administrator can create member login accounts.",color=Muted,fontSize=13.sp)
  else Surface(color=Color.White,shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(12.dp)){Text("Role for new account",color=Navy,fontWeight=FontWeight.SemiBold)
    Box { OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()){Text(roles.first{it.first==role}.second,modifier=Modifier.weight(1f));Icon(Icons.Filled.ArrowDropDown,null)}
     DropdownMenu(expanded=menu,onDismissRequest={menu=false}){roles.forEach{r->DropdownMenuItem(text={Text(r.second)},onClick={role=r.first;menu=false})}}
    }
    Text("The platform generates the Account ID and one-time temporary PIN.",color=Muted,fontSize=12.sp)
   }
  }
  if(canManage) {
   OutlinedButton(onClick={adding=!adding},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Filled.PersonAdd,null); Spacer(Modifier.width(8.dp)); Text(if(adding) "Close registration form" else "Register a new member") }
   if(adding) Surface(color=Color.White,shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("New member details",color=Navy,fontWeight=FontWeight.Bold)
     OutlinedTextField(value=newName,onValueChange={newName=it},label={Text("Full name *")},singleLine=true,modifier=Modifier.fillMaxWidth())
     OutlinedTextField(value=newPhone,onValueChange={newPhone=it},label={Text("Phone number")},singleLine=true,modifier=Modifier.fillMaxWidth())
     OutlinedTextField(value=newEmail,onValueChange={newEmail=it},label={Text("Email (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
     OutlinedTextField(value=newAddress,onValueChange={newAddress=it},label={Text("Address (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
     Text("Member ID is generated automatically by the SACCO platform.",color=Muted,fontSize=11.sp)
     Button(onClick={
      if(newName.trim().length<2) error="Enter the member's full name."
      else { registering=true;error="";scope.launch { try { val created=registerSaccoMember(session,newName,newPhone,newEmail,newAddress);members=listOf(created)+members;adding=false;newName="";newPhone="";newEmail="";newAddress="" } catch(e:Exception){error=e.message ?: "Member registration failed."} finally{registering=false} } }
     },enabled=!registering,modifier=Modifier.fillMaxWidth()) { if(registering) CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Text("Save member") }
    }
   }
  }
  if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp))
  when {
   loading -> Box(Modifier.fillMaxWidth().padding(35.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}
   members.isEmpty() -> EmptyState(Icons.Filled.People,"No active members","Active registered members will appear here.")
   else -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(vertical=10.dp)){
    items(members,key={it.id}){m->Surface(color=Color.White,shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth()){
     Column(Modifier.padding(13.dp)){
      Text(m.fullName,color=Navy,fontWeight=FontWeight.SemiBold)
      Text(m.memberId.ifBlank{m.memberNumber}+(if(m.phone.isNotBlank()) " • "+m.phone else ""),color=Muted,fontSize=11.sp)
      if(m.hasLogin) Text("Login account already created",color=Teal,fontSize=12.sp)
      else if(canManage) Button(onClick={busyId=m.id;error="";scope.launch{try{account=provisionMemberAccount(session,m,role);members=members.map{if(it.id==m.id)it.copy(hasLogin=true)else it}}catch(e:Exception){error=e.message ?: "Could not create account."}finally{busyId=""}}},enabled=busyId.isBlank()&&account==null,modifier=Modifier.fillMaxWidth()){
       if(busyId==m.id) CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Text("Create login account")
      }
     }
    }}
   }
  }
  OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Back to dashboard")}
 }
 account?.let{a->androidx.compose.ui.window.Dialog(onDismissRequest={account=null}){
  Surface(color=Color.White,shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
   Icon(Icons.Filled.VerifiedUser,null,tint=Teal,modifier=Modifier.size(30.dp));Text("Account created",color=Navy,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Text("For "+a.memberName+" • "+a.role.replace('_',' '),color=Muted,fontSize=12.sp)
   Text("Account ID",color=Muted,fontSize=12.sp);Text(a.accountId,color=Navy,fontSize=18.sp,fontWeight=FontWeight.Bold);Divider()
   Text("One-time temporary PIN",color=Muted,fontSize=12.sp);Text(a.temporaryPin,color=Navy,fontSize=24.sp,fontWeight=FontWeight.Bold)
   Text("Give this PIN privately to the member. They must activate the account and set their own private PIN. This PIN is shown once.",color=Color(0xFFB54708),fontSize=12.sp,lineHeight=17.sp)
   Button(onClick={account=null;reload++},modifier=Modifier.fillMaxWidth()){Text("I have handed it over securely")}
  }}
 }}
}

private data class OwnerSaccoRow(
    val id: String, val name: String, val motherId: String, val code: String,
    val status: String, val district: String, val town: String,
    val expectedMembers: Int, val actualMembers: Int, val lastSync: String
)

private suspend fun fetchOwnerDirectory(session: SaccoSession): List<OwnerSaccoRow> = withContext(Dispatchers.IO) {
    if (session.role != "platform_owner") throw IOException("This account does not have permission to perform this action.")
    val req = Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-owner-directory")
        .header("apikey", SUPABASE_PUBLISHABLE_KEY)
        .header("Authorization", "Bearer ${session.accessToken}").get().build()
    val root = requestJson(req)
    val arr = root.optJSONArray("saccos") ?: JSONArray()
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(OwnerSaccoRow(o.optString("id"),o.optString("name"),o.optString("mother_id"),o.optString("sacco_code"),o.optString("status"),o.optString("district"),o.optString("town_village"),o.optInt("expected_member_count"),o.optInt("actual_member_count"),o.optString("last_sync_at")))
        }
    }
}

private suspend fun createOwnerSacco(session: SaccoSession, name: String, district: String, town: String, address: String, phone: String, email: String, expected: Int) = withContext(Dispatchers.IO) {
    if (session.role != "platform_owner") throw IOException("This account does not have permission to register a SACCO.")
    if (name.trim().length < 2) throw IOException("Enter the SACCO name.")
    if (district.trim().length < 2) throw IOException("Enter the district.")
    if (expected < 0) throw IOException("Expected member count cannot be negative.")
    val payload = JSONObject().put("name",name.trim()).put("district",district.trim())
        .put("town_village",town.trim()).put("physical_address",address.trim())
        .put("contact_phone",phone.trim()).put("contact_email",email.trim())
        .put("expected_member_count",expected).put("status","trial").put("currency","UGX")
    val req = Request.Builder().url("$SUPABASE_URL/functions/v1/sacco-create")
        .header("apikey",SUPABASE_PUBLISHABLE_KEY).header("Authorization","Bearer ${session.accessToken}")
        .header("Content-Type","application/json").post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    requestJson(req)
}

@Composable
private fun OwnerDirectoryScreen(session: SaccoSession, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<OwnerSaccoRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var town by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("50") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        try { rows = fetchOwnerDirectory(session); error = "" }
        catch (e: Exception) { error = e.message ?: "Could not load SACCO directory." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading("SACCO Directory", "Register SACCO tenants and monitor their status and member totals. Private financial records are never shown here.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showForm = !showForm; error = "" }, modifier = Modifier.weight(1f)) { Text(if (showForm) "Close registration" else "Register a SACCO") }
                OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) { Icon(Icons.Filled.Refresh,null); Text("Refresh") }
            }
        }
        if (showForm) item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("New SACCO registration", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    OutlinedTextField(name,{name=it.take(160)},label={Text("SACCO name *")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(district,{district=it.take(120)},label={Text("District *")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(town,{town=it.take(160)},label={Text("Town / village")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(address,{address=it.take(300)},label={Text("Physical address")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(phone,{phone=it.take(50)},label={Text("General contact phone")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(email,{email=it.take(254)},label={Text("Contact email (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(expected,{expected=it.filter(Char::isDigit).take(7)},label={Text("Expected member count")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                    Text("The system generates the permanent SACCO code and Mother ID automatically. New registrations start in Trial status.",color=Muted,fontSize=12.sp,lineHeight=17.sp)
                    Button(onClick={
                        val count=expected.toIntOrNull() ?: 50
                        scope.launch { busy=true; error=""
                            try { val created=createOwnerSacco(session,name,district,town,address,phone,email,count); val sc=created.optJSONObject("sacco") ?: JSONObject(); error="Created ${sc.optString("name")} • Mother ID ${sc.optString("mother_id")}"; name="";district="";town="";address="";phone="";email="";expected="50";showForm=false;refresh++ }
                            catch(e:Exception){error=e.message ?: "SACCO registration failed."}
                            finally{busy=false}
                        }
                    },enabled=!busy && name.trim().length>=2 && district.trim().length>=2,modifier=Modifier.fillMaxWidth()) {
                        if(busy) CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Text("Create SACCO")
                    }
                }
            }
        }
        if(error.isNotBlank()) item { Text(error,color=if(error.startsWith("Created")) Teal else MaterialTheme.colorScheme.error,fontSize=13.sp) }
        if(loading) item { Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} }
        else if(rows.isEmpty()) item { EmptyState(Icons.Filled.Domain,"No SACCOs registered","New SACCO tenants will appear here after registration.") }
        else items(rows,key={it.id}) { row ->
            Surface(color=Color.White,shape=RoundedCornerShape(17.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text(row.name,color=Navy,fontWeight=FontWeight.Bold,fontSize=16.sp,modifier=Modifier.weight(1f)); Text(row.status.replaceFirstChar{it.uppercase()},color=if(row.status=="active")Teal else Muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold) }
                    Text("Mother ID: ${row.motherId}",color=Blue,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                    Text("SACCO code: ${row.code}",color=Muted,fontSize=11.sp)
                    Text(listOf(row.town,row.district).filter{it.isNotBlank()}.joinToString(", "),color=Muted,fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) { Text("Members: ${row.actualMembers}",color=Navy,fontSize=12.sp); Text("Expected: ${row.expectedMembers}",color=Muted,fontSize=12.sp) }
                    Text(if(row.lastSync.isBlank()) "Last sync: Not yet reported" else "Last sync: ${row.lastSync}",color=Muted,fontSize=11.sp)
                }
            }
        }
        item { OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Back to dashboard")} }
    }
}

@Composable
private fun FeatureScreen(title: String, session: SaccoSession, onBack: () -> Unit) {
    if (title == "Members") { MemberManagementScreen(session, onBack); return }
    if (title == "SACCO Directory" && session.role == "platform_owner") { OwnerDirectoryScreen(session, onBack); return }
    val description = when (title) {
        "SACCO Directory" -> "Tenant registration, activation status, member totals, and technical health. Private financial records are not available to the Platform Owner."
        "Platform Health" -> "Connection and synchronization health indicators for registered SACCO tenants."
        "Location Sharing" -> "Only the last location explicitly shared by a SACCO is shown, with its timestamp. Continuous GPS is not enabled by default."
        "Members" -> "Member registration and directory tools are restricted to authorized officers in this SACCO."
        "Savings & Contributions", "Contributions" -> "View and record contributions according to this SACCO's configured rules and your assigned permissions."
        "My Accounts", "Accounts" -> "Account records and balances are available only when authorized for your role and by the SACCO display policy."
        "My Loans", "Loans", "Loan Applications" -> "Loan applications and records follow the configured eligibility rules and required approval process."
        "Meetings" -> "Meeting calendar, attendance, notices, and minutes."
        "Policies" -> "Read the approved constitution, bylaws, and rules. Only authorized officers can change policies."
        "Receipts", "Receipts & History" -> "Receipts and transaction history must be loaded from the authenticated ledger service."
        "Reports" -> "Reports are limited to the data your assigned role is authorized to view."
        "Proposals & Votes" -> "Proposals and voting follow the approved SACCO rules and audit trail."
        "Documents" -> "Files and documents are shown only when your role is authorized to access them."
        "Activation" -> "Tenant status and membership limits are managed by the Platform Owner."
        "Platform Activity" -> "Technical audit events only. Private SACCO financial statements and member transactions are excluded."
        else -> "This feature screen is reserved for the authenticated SACCO workflow. Live records will be shown only after its service endpoint and authorization checks are connected."
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Box(Modifier.size(54.dp).background(PaleBlue, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.DashboardCustomize, null, tint = Blue, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(16.dp)); Text(title, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp)); Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Security, null, tint = Teal)
                Spacer(Modifier.width(10.dp))
                Text("No sample financial data is displayed. This screen will show live records only after the corresponding backend workflow is implemented and verified.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to dashboard") }
    }
}

@Composable
private fun SectionHeading(title: String, description: String) {
    Column(Modifier.padding(bottom = 2.dp)) { Text(title, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(description, color = Muted, fontSize = 13.sp, lineHeight = 18.sp) }
}
@Composable
private fun InfoCard(title: String, value: String, icon: ImageVector, detail: String? = null) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(PaleBlue, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Blue) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = Muted, fontSize = 12.sp); Text(value, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); if (!detail.isNullOrBlank()) Text(detail, color = Muted, fontSize = 11.sp) }
        }
    }
}
@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            content()
        }
    }
}
@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Navy, fontWeight = FontWeight.Medium, fontSize = 14.sp); Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 15.sp) }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Muted, fontSize = 13.sp); Text(value, color = Navy, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
}
@Composable
private fun EmptyState(icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(PaleBlue, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Blue, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp)); Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp)); Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
