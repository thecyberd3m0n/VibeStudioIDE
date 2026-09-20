package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.service.ChatService;

public class SystemMessageView extends LinearLayout {

    private final TextView mTvIcon;
    private final TextView mTvText;
    private final TextView mTvArrow;

    public SystemMessageView(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(24, 12, 24, 12);
        setBackgroundColor(Color.parseColor("#181822")); // Sleek VSCode inline system call style

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 8, 0, 8);
        setLayoutParams(layoutParams);

        // Terminal / Tool Icon
        mTvIcon = new TextView(context);
        mTvIcon.setText("💻");
        mTvIcon.setTextSize(14);
        mTvIcon.setPadding(0, 0, 16, 0);

        // Tool text / message
        mTvText = new TextView(context);
        mTvText.setTextColor(Color.parseColor("#858585")); // Subtle VSCode status grey
        mTvText.setTextSize(13);
        mTvText.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        mTvText.setLayoutParams(textParams);

        // Click arrow / indicator
        mTvArrow = new TextView(context);
        mTvArrow.setText("➔");
        mTvArrow.setTextColor(Color.parseColor("#007ACC")); // VSCode Accent Blue
        mTvArrow.setTextSize(12);
        mTvArrow.setPadding(12, 0, 0, 0);

        addView(mTvIcon);
        addView(mTvText);
        addView(mTvArrow);

        // Clicking on the system tool view navigates to Terminal tab
        setOnClickListener(v -> {
            if (getContext() instanceof MainActivity) {
                ((MainActivity) getContext()).selectNavigationItem(2); // Terminal is item 2
            } else if (getContext() instanceof FragmentActivity) {
                FragmentActivity act = (FragmentActivity) getContext();
                if (act instanceof MainActivity) {
                    ((MainActivity) act).selectNavigationItem(2);
                }
            }
        });
    }

    public void bind(ChatService.ChatMessage msg) {
        String text = msg.getText();
        String displaySummary = text;

        if (text != null && text.contains("{") && text.contains("\"tool\"")) {
            try {
                int jsonStart = text.indexOf("{");
                int jsonEnd = text.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    org.json.JSONObject toolCallObj = new org.json.JSONObject(text.substring(jsonStart, jsonEnd + 1).trim());
                    String toolName = toolCallObj.optString("tool");
                    org.json.JSONObject args = toolCallObj.optJSONObject("args");
                    if (args == null) args = new org.json.JSONObject();

                    if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                        String skillName = args.optString("skill_name", "unknown");
                        displaySummary = "Requesting schema for skill: " + skillName;
                        mTvIcon.setText("🔧");
                    } else if ("execute_command".equalsIgnoreCase(toolName)) {
                        String cmd = args.optString("command", "");
                        displaySummary = "Terminal > " + cmd;
                        mTvIcon.setText("💻");
                    } else if ("read_terminal_output".equalsIgnoreCase(toolName)) {
                        displaySummary = "Terminal > Inspecting output buffer";
                        mTvIcon.setText("🔍");
                    } else {
                        displaySummary = "Tool > " + toolName;
                        mTvIcon.setText("⚙️");
                    }
                }
            } catch (Exception e) {
                displaySummary = text;
            }
        } else if (text != null) {
            if (text.contains("Terminal >") || text.contains("terminal")) {
                mTvIcon.setText("💻");
            } else if (text.contains("schema") || text.contains("skill")) {
                mTvIcon.setText("🔧");
            } else {
                mTvIcon.setText("⚡");
            }
        }

        mTvText.setText(displaySummary);
    }
}
