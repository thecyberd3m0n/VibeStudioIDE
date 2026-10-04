package com.vibestudio.app.activity;
import com.vibestudio.app.R;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.fragments.*;
import com.vibestudio.app.util.FontUtils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CrashActivity extends Activity {

    public static final String EXTRA_CRASH_LOG = "extra_crash_log";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String crashLog = getIntent().getStringExtra(EXTRA_CRASH_LOG);
        if (crashLog == null || crashLog.isEmpty()) {
            crashLog = "No crash log details available.";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("⚠️ Application Crash");
        title.setTextColor(Color.parseColor("#EF4444"));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("VibeStudio encountered an unexpected error and had to stop. Below are the details:");
        subtitle.setTextColor(Color.parseColor("#CCCCCC"));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, 0, 0, 24);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        scrollView.setPadding(24, 24, 24, 24);

        final TextView logText = new TextView(this);
        logText.setText(crashLog);
        logText.setTextColor(Color.parseColor("#F3F4F6"));
        logText.setTextSize(13);
        logText.setTypeface(FontUtils.getMonospaceTypeface(this));
        logText.setTextIsSelectable(true);
        scrollView.addView(logText);

        root.addView(scrollView);

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Copy Log");
        btnCopy.setBackgroundColor(Color.parseColor("#2563EB"));
        btnCopy.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        copyParams.setMargins(0, 0, 12, 0);
        btnCopy.setLayoutParams(copyParams);

        final String finalCrashLog = crashLog;
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("VibeStudio Crash Log", finalCrashLog);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(CrashActivity.this, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button btnClose = new Button(this);
        btnClose.setText("Close App");
        btnClose.setBackgroundColor(Color.parseColor("#374151"));
        btnClose.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        closeParams.setMargins(12, 0, 0, 0);
        btnClose.setLayoutParams(closeParams);

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
                System.exit(0);
            }
        });

        btnContainer.addView(btnCopy);
        btnContainer.addView(btnClose);
        root.addView(btnContainer);

        setContentView(root);
    }
}
