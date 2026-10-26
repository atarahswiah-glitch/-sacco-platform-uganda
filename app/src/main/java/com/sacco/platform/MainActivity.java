package com.sacco.platform;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private final int blue = Color.rgb(21, 78, 150);
    private final int pale = Color.rgb(239, 245, 253);
    private EditText emailField, passwordField;
    private TextView status;
    private Button loginButton;
    private String currentScreen = "login";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showLogin();
    }

    private void showLogin() {
        currentScreen = "login";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(35), dp(24), dp(24));
        page.setBackgroundColor(Color.WHITE);

        TextView logo = new TextView(this);
        logo.setText("🏦");
        logo.setTextSize(48);
        logo.setGravity(Gravity.CENTER);
        page.addView(logo, params());

        TextView brand = new TextView(this);
        brand.setText("SACCO PLATFORM\nUGANDA");
        brand.setTextColor(blue);
        brand.setTextSize(23);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(8), 0, dp(12));
        page.addView(brand, params());

        TextView title = new TextView(this);
        title.setText("Secure sign in");
        title.setTextSize(20);
        title.setTextColor(Color.DKGRAY);
        title.setPadding(0, dp(18), 0, dp(8));
        page.addView(title, params());

        emailField = new EditText(this);
        emailField.setSingleLine(true);
        emailField.setHint("Email address");
        emailField.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        page.addView(emailField, params());

        passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setHint("Password");
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams pp = params();
        pp.topMargin = dp(10);
        page.addView(passwordField, pp);

        loginButton = new Button(this);
        loginButton.setText("SIGN IN");
        loginButton.setTextColor(Color.WHITE);
        loginButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(blue));
        LinearLayout.LayoutParams bp = params();
        bp.topMargin = dp(20);
        page.addView(loginButton, bp);
        loginButton.setOnClickListener(v -> signIn());

        status = new TextView(this);
        status.setText("Sign in with your registered account.");
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(14);
        status.setPadding(0, dp(16), 0, 0);
        page.addView(status, params());

        setContentView(page);
    }

    private void signIn() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            status.setText("Enter your email address and password.");
            return;
        }

        loginButton.setEnabled(false);
        status.setText("Signing in securely...");

        new Thread(() -> {
            String message;
            boolean authorized = false;
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                JSONObject auth = new JSONObject(request(
                        "/auth/v1/token?grant_type=password",
                        "POST", body.toString(), null));

                String token = auth.getString("access_token");

                JSONObject bootstrap = new JSONObject(request(
                        "/functions/v1/sacco-dashboard-bootstrap",
                        "GET", null, token));
                String role = bootstrap.optString("dashboard_role", "");
                authorized = "platform_owner".equals(role);

                if (!authorized) {
                    message = "Login succeeded, but this account is not yet authorized as a Platform Owner.";
                } else {
                    message = "Authorized.";
                }
            } catch (Exception e) {
                message = "Sign-in request failed: " +
    (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }

            final boolean canEnter = authorized;
            final String result = message;
            runOnUiThread(() -> {
                loginButton.setEnabled(true);
                if (canEnter) {
                    showDashboard();
                } else {
                    status.setText(result);
                }
            });
        }).start();
    }

    private void showDashboard() {
        currentScreen = "dashboard";
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(22));
        page.setBackgroundColor(Color.WHITE);
        scroll.addView(page);

        TextView heading = new TextView(this);
        heading.setText("🏦  SACCO PLATFORM");
        heading.setTextSize(21);
        heading.setTextColor(blue);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(6));
        page.addView(heading, params());

        TextView subtitle = new TextView(this);
        subtitle.setText("UGANDA  •  PLATFORM OWNER");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, 0, 0, dp(20));
        page.addView(subtitle, params());

        TextView section = new TextView(this);
        section.setText("Your platform");
        section.setTextColor(Color.BLACK);
        section.setTextSize(20);
        section.setTypeface(null, Typeface.BOLD);
        section.setPadding(0, 0, 0, dp(12));
        page.addView(section, params());

        addTileRow(page,
                "🏢", "SACCOs",
                "👥", "Member counts");
        addTileRow(page,
                "📡", "System status",
                "📍", "Location status");
        addTileRow(page,
                "🔔", "Notifications",
                "⚙️", "Settings");

        TextView privacy = new TextView(this);
        privacy.setText("Platform monitoring only. Private SACCO financial records are not shown here.");
        privacy.setTextSize(13);
        privacy.setTextColor(Color.DKGRAY);
        privacy.setPadding(dp(12), dp(16), dp(12), dp(16));
        privacy.setBackground(roundRect(pale, 12));
        LinearLayout.LayoutParams privp = params();
        privp.topMargin = dp(12);
        page.addView(privacy, privp);

        Button logout = new Button(this);
        logout.setText("LOG OUT");
        logout.setOnClickListener(v -> showLogin());
        LinearLayout.LayoutParams lp = params();
        lp.topMargin = dp(16);
        page.addView(logout, lp);

        setContentView(scroll);
    }

    private void addTileRow(LinearLayout parent,
                            String icon1, String label1,
                            String icon2, String label2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(makeTile(icon1, label1), new LinearLayout.LayoutParams(
                0, dp(132), 1));
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        row.addView(makeTile(icon2, label2), new LinearLayout.LayoutParams(
                0, dp(132), 1));

        LinearLayout.LayoutParams rp = params();
        rp.bottomMargin = dp(12);
        parent.addView(row, rp);
    }

    private View makeTile(String icon, String label) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(12), dp(8), dp(12));
        tile.setBackground(roundRect(pale, 16));
        tile.setClickable(true);
        tile.setFocusable(true);

        TextView symbol = new TextView(this);
        symbol.setText(icon);
        symbol.setTextSize(32);
        symbol.setGravity(Gravity.CENTER);
        tile.addView(symbol, params());

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(14);
        text.setTextColor(blue);
        text.setTypeface(null, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(8), 0, 0);
        tile.addView(text, params());

        tile.setOnClickListener(v -> openModule(label));
        return tile;
    }

    private void openModule(String name) {
        currentScreen = "module";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(24));
        page.setBackgroundColor(Color.WHITE);

        Button back = new Button(this);
        back.setText("←  BACK TO DASHBOARD");
        back.setOnClickListener(v -> showDashboard());
        page.addView(back, params());

        TextView icon = new TextView(this);
        icon.setTextSize(42);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(0, dp(28), 0, dp(10));
        page.addView(icon, params());

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(24);
        title.setTextColor(blue);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        page.addView(title, params());

        TextView info = new TextView(this);
        info.setText(
                "This section is not connected to its data actions yet.\n\n"
                + "No SACCO records have been changed from this screen. "
                + "The next implementation step is to connect this section "
                + "to the secure Supabase backend.");
        info.setTextSize(16);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(8), dp(24), dp(8), dp(24));
        page.addView(info, params());

        setContentView(page);
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private String request(String path, String method,
                          String body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection)
                new URL(SupabaseConfig.URL + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type",
                        "application/json; charset=UTF-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? c.getInputStream() : c.getErrorStream();
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            }
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + result.toString());
            }
            return result.toString();
        } finally {
            c.disconnect();
        }
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
