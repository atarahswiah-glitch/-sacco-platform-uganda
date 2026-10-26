package com.sacco.platform;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private final int blue = Color.rgb(21, 78, 150);
    private EditText emailField;
    private EditText passwordField;
    private TextView status;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(36), dp(24), dp(24));
        page.setBackgroundColor(Color.WHITE);

        TextView brand = new TextView(this);
        brand.setText("SACCO PLATFORM UGANDA");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(16);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(dp(12), dp(20), dp(12), dp(20));
        brand.setBackgroundColor(blue);
        page.addView(brand, params());

        TextView title = new TextView(this);
        title.setText("Sign in");
        title.setTextSize(29);
        title.setTextColor(blue);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(32), 0, dp(8));
        page.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Use the email and password registered to your account.");
        hint.setTextSize(15);
        hint.setTextColor(Color.DKGRAY);
        page.addView(hint);

        emailField = new EditText(this);
        emailField.setSingleLine(true);
        emailField.setHint("Email address");
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams fieldParams = params();
        fieldParams.topMargin = dp(24);
        page.addView(emailField, fieldParams);

        passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setHint("Password");
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passParams = params();
        passParams.topMargin = dp(12);
        page.addView(passwordField, passParams);

        loginButton = new Button(this);
        loginButton.setText("Sign in securely");
        loginButton.setTextColor(Color.WHITE);
        loginButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue));
        LinearLayout.LayoutParams buttonParams = params();
        buttonParams.topMargin = dp(20);
        page.addView(loginButton, buttonParams);
        loginButton.setOnClickListener(v -> signIn());

        status = new TextView(this);
        status.setText("Your account permissions will be checked after sign-in.");
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(14);
        status.setPadding(0, dp(18), 0, 0);
        page.addView(status, params());

        setContentView(page);
    }

    private void signIn() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            status.setText("Enter both your email address and password.");
            return;
        }
        loginButton.setEnabled(false);
        status.setText("Connecting securely to Supabase...");
        new Thread(() -> {
            String message;
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                JSONObject auth = new JSONObject(request(
                    "/auth/v1/token?grant_type=password", "POST",
                    body.toString(), null));
                String userId = auth.getJSONObject("user").getString("id");
                String token = auth.getString("access_token");
                String ownerJson = request(
                    "/rest/v1/platform_owners?user_id=eq." + userId + "&select=active",
                    "GET", null, token);
                JSONArray owners = new JSONArray(ownerJson);
                if (owners.length() > 0 && owners.getJSONObject(0).optBoolean("active", false)) {
                    message = "Sign-in successful. Platform Creator access is verified. The full Creator dashboard is the next implementation step.";
                } else {
                    message = "Sign-in successful, but Platform Creator access is not active for this account. An authorized platform owner record is required.";
                }
            } catch (Exception e) {
                message = "Sign-in failed. Check your internet connection and account details, then try again.";
            }
            final String result = message;
            runOnUiThread(() -> {
                status.setText(result);
                loginButton.setEnabled(true);
            });
        }).start();
    }

    private String request(String path, String method, String body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SupabaseConfig.URL + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            return result.toString();
        } finally {
            c.disconnect();
        }
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
