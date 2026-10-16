package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

import com.vibestudio.app.chat.model.ChatMessage;

import org.json.JSONObject;

public class CustomStatusToolMessage extends BaseToolMessageView {

    private TextView mTvStatusText;

    public CustomStatusToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "";
    }

    @Override
    protected String getHeaderTitle() {
        return "";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return -1;
    }

    @Override
    public void bind(ChatMessage message) {
        removeAllViews();

        setOrientation(VERTICAL);
        LayoutParams params = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 8, 0, 8);
        setLayoutParams(params);

        mTvStatusText = new TextView(getContext());
        mTvStatusText.setTextColor(Color.parseColor("#888888"));
        mTvStatusText.setTextSize(12);
        mTvStatusText.setTypeface(null, Typeface.ITALIC);
        mTvStatusText.setGravity(Gravity.CENTER);

        String rawText = message != null ? message.getText() : "";
        String skillName = extractSkillNameFromToolCall(rawText);
        String displayText;

        if (!skillName.isEmpty()) {
            displayText = "loading skill: " + skillName;
        } else if (rawText != null && !rawText.trim().isEmpty()) {
            displayText = rawText.trim();
        } else {
            displayText = "processing...";
        }

        mTvStatusText.setText(displayText);
        addView(mTvStatusText);
    }

    private String extractSkillNameFromToolCall(String text) {
        if (text == null || text.isEmpty()) return "";
        int jsonStart = text.indexOf("{");
        int jsonEnd = text.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                JSONObject json = new JSONObject(text.substring(jsonStart, jsonEnd + 1));
                JSONObject args = json.optJSONObject("args");
                if (args != null) {
                    return args.optString("skill_name", args.optString("skill", ""));
                }
            } catch (Exception ignored) {}
        }
        return "";
    }
}
