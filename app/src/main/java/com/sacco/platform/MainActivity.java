package com.sacco.platform;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final int blue = Color.rgb(21, 78, 150);
    private final int paleBlue = Color.rgb(235, 243, 253);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(24));
        page.setBackgroundColor(Color.WHITE);
        scroll.addView(page);

        TextView brand = text("CIRCLE PLATFORM UGANDA", 13, Color.WHITE, true);
        brand.setLetterSpacing(0.06f);
        brand.setPadding(dp(16), dp(16), dp(16), dp(16));
        brand.setBackgroundColor(blue);
        page.addView(brand, matchWrap());

        TextView title = text("Welcome", 30, blue, true);
        title.setPadding(0, dp(24), 0, dp(4));
        page.addView(title);
        TextView subtitle = text("One platform. Separate, secure SACCO rooms.", 16, Color.DKGRAY, false);
        page.addView(subtitle);

        TextView notice = text("Starter version", 18, blue, true);
        notice.setPadding(dp(16), dp(16), dp(16), dp(6));
        notice.setBackgroundColor(paleBlue);
        LinearLayout.LayoutParams np = matchWrap();
        np.topMargin = dp(24);
        page.addView(notice, np);

        TextView note = text("This screen confirms the native Android project can launch. Login, role permissions, Supabase connection, offline storage, and financial features are not implemented yet.", 15, Color.DKGRAY, false);
        note.setPadding(dp(16), dp(6), dp(16), dp(16));
        note.setBackgroundColor(paleBlue);
        page.addView(note, matchWrap());

        addCard(page, "Master Creator", "Register SACCOs and monitor platform health. Private member and financial records must remain protected.");
        addCard(page, "SACCO rooms", "Each SACCO will have its own members, officials, rules, meetings, savings, loans, and transaction history.");
        addCard(page, "Secure accounts", "Each person will sign in individually, with permissions enforced by the backend.");
        addCard(page, "Next milestone", "Connect Supabase securely and implement tenant isolation and authentication before adding real records.");

        setContentView(scroll);
    }

    private void addCard(LinearLayout page, String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(Color.rgb(247, 249, 252));
        LinearLayout.LayoutParams cp = matchWrap();
        cp.topMargin = dp(14);
        page.addView(card, cp);
        TextView h = text(heading, 18, blue, true);
        card.addView(h);
        TextView b = text(body, 14, Color.DKGRAY, false);
        b.setPadding(0, dp(6), 0, 0);
        card.addView(b);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
