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
    val memberId: String = ""
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
                } else {
                    LoginScreen(
                        busy = busy,
                        message = statusMessage,
                        onLogin = { accountId, pin ->
                            busy = true
                            statusMessage = ""
                            scope.launch {
                                try {
                                    session = withContext(Dispatchers.IO) { loginAndLoadContext(accountId, pin) }
                                    statusMessage = ""
                                } catch (e: Exception) {
                                    statusMessage = e.message ?: "Sign-in failed. Check your Account ID and PIN."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
            } else {
                Scaffold(
                    containerColor = Canvas,
                    topBar = {
                        TopAppBarSacco(
                            title = feature ?: destination.label,
                            subtitle = if (active.role == "platform_owner") "Platform Owner" else active.saccoName,
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
    if (accountId.isBlank()) throw IOException("Enter your account ID.")
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
        memberId = member.optString("member_id", member.optString("member_number"))
    )
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
        Text("Platform Owner Registration", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Enter the Platform Owner ID generated for this app. You can generate and retrieve the ID from the Termux terminal.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = ownerId,
            onValueChange = { ownerId = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(32) },
            label = { Text("Platform Owner ID") },
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
private fun LoginScreen(busy: Boolean, message: String, onLogin: (String, String) -> Unit) {
    var accountId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Canvas).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(68.dp).background(Blue, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("SACCO Platform", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Uganda", fontSize = 17.sp, color = Blue, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Secure access to your SACCO", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(value = accountId, onValueChange = { accountId = it.uppercase().take(24) }, label = { Text("Account ID") }, leadingIcon = { Icon(Icons.Filled.Badge, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN (8–12 digits)") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, trailingIcon = { IconButton(onClick = { showPin = !showPin }) { Icon(if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } }, visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Spacer(Modifier.height(22.dp))
        Button(onClick = { onLogin(accountId, pin) }, enabled = !busy && accountId.isNotBlank() && pin.length in 8..12, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Sign in securely", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Forgot your PIN? Contact your SACCO administrator for the verified recovery process.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
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
private fun FeatureScreen(title: String, session: SaccoSession, onBack: () -> Unit) {
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
