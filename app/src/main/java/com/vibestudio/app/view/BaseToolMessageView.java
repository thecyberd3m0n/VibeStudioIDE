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

public abstract class BaseToolMessageView extends LinearLayout {

    private final LinearLayout mHeaderLayout;
    private final TextView mTvIcon;
    private final TextView mTvHeaderTitle;
    private final TextView mTvExpandArrow;

    private final LinearLayout mBodyLayout;
    private final TextView mTvBodyContent;

    private boolean mIsExpanded = false;

    public BaseToolMessageView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 8, 0, 8);
        setLayoutParams(containerParams);

        // --- HEADER LAYOUT ---
        mHeaderLayout = new LinearLayout(context);
        mHeaderLayout.setOrientation(LinearLayout.HORIZONTAL);
        mHeaderLayout.setGravity(Gravity.CENTER_VERTICAL);
        mHeaderLayout.setPadding(24, 14, 24, 14);
        mHeaderLayout.setBackgroundColor(Color.parseColor("#181822")); // VSCode dark header style

        mTvIcon = new TextView(context);
        mTvIcon.setText(getToolIcon());
        mTvIcon.setTextSize(14);
        mTvIcon.setPadding(0, 0, 16, 0);

        mTvHeaderTitle = new TextView(context);
        mTvHeaderTitle.setText(getHeaderTitle());
        mTvHeaderTitle.setTextColor(Color.parseColor("#CCCCCC"));
        mTvHeaderTitle.setTextSize(13);
        mTvHeaderTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        mTvHeaderTitle.setLayoutParams(titleParams);

        mTvExpandArrow = new TextView(context);
        mTvExpandArrow.setText("▼"); // Wrapped by default
        mTvExpandArrow.setTextColor(Color.parseColor("#007ACC"));
        mTvExpandArrow.setTextSize(11);
        mTvExpandArrow.setPadding(12, 0, 0, 0);

        mHeaderLayout.addView(mTvIcon);
        mHeaderLayout.addView(mTvHeaderTitle);
        mHeaderLayout.addView(mTvExpandArrow);

        // --- BODY LAYOUT ---
        mBodyLayout = new LinearLayout(context);
        mBodyLayout.setOrientation(LinearLayout.VERTICAL);
        mBodyLayout.setPadding(24, 16, 24, 16);
        mBodyLayout.setBackgroundColor(Color.parseColor("#111116")); // Darker body fill
        mBodyLayout.setVisibility(View.GONE); // Wrapped by default

        mTvBodyContent = new TextView(context);
        mTvBodyContent.setTextColor(Color.parseColor("#A0A0A0"));
        mTvBodyContent.setTextSize(12);
        mTvBodyContent.setTypeface(Typeface.MONOSPACE);
        mTvBodyContent.setTextIsSelectable(false); // Disables internal text-selection touch stealing so body click fires

        mBodyLayout.addView(mTvBodyContent);

        addView(mHeaderLayout);
        addView(mBodyLayout);

        // Toggle Expand/Collapse on Header click
        mHeaderLayout.setOnClickListener(v -> toggleExpand());

        // Navigation to target tool view on Body click or Body text click
        View.OnClickListener clickToNavigate = v -> navigateToTool();
        mBodyLayout.setOnClickListener(clickToNavigate);
        mTvBodyContent.setOnClickListener(clickToNavigate);
    }

    protected abstract String getToolIcon();
    protected abstract String getHeaderTitle();
    protected abstract int getTargetNavigationIndex();

    private void toggleExpand() {
        mIsExpanded = !mIsExpanded;
        mBodyLayout.setVisibility(mIsExpanded ? View.VISIBLE : View.GONE);
        mTvExpandArrow.setText(mIsExpanded ? "▲" : "▼");
    }

    private void navigateToTool() {
        int navIndex = getTargetNavigationIndex();
        Context ctx = getContext();
        if (ctx instanceof MainActivity) {
            ((MainActivity) ctx).selectNavigationItem(navIndex);
        } else if (ctx instanceof FragmentActivity) {
            FragmentActivity act = (FragmentActivity) ctx;
            if (act instanceof MainActivity) {
                ((MainActivity) act).selectNavigationItem(navIndex);
            }
        }
    }

    public void bind(ChatService.ChatMessage msg) {
        String text = msg.getText();
        String displayContent = text;

        if (text != null && text.contains("{") && text.contains("\"tool\"")) {
            try {
                int jsonStart = text.indexOf("{");
                int jsonEnd = text.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    org.json.JSONObject toolCallObj = new org.json.JSONObject(text.substring(jsonStart, jsonEnd + 1).trim());
                    String toolName = toolCallObj.optString("tool");
                    org.json.JSONObject args = toolCallObj.optJSONObject("args");
                    if (args == null) args = new org.json.JSONObject();

                    if ("execute_command".equalsIgnoreCase(toolName)) {
                        displayContent = "$ " + args.optString("command", "");
                    } else if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                        displayContent = "Schema request: " + args.optString("skill_name", "");
                    } else if ("read_terminal_output".equalsIgnoreCase(toolName)) {
                        displayContent = "Reading terminal buffer (" + args.optInt("max_lines", 30) + " lines)";
                    } else {
                        displayContent = toolName + "(" + args.toString() + ")";
                    }
                }
            } catch (Exception ignored) {}
        }

        mTvBodyContent.setText(displayContent);
    }
}
