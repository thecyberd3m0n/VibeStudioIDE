package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.McpClientManager;

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

        String rawText = message != null ? message.getText() : "";
        String displayText = extractFormattedStatus(rawText);

        if (displayText == null) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
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

        mTvStatusText.setText(displayText);
        addView(mTvStatusText);
    }

    @Override
    public void appendToolMessage(ChatMessage msg) {
        CharSequence newText = extractFormattedStatus(msg != null ? msg.getText() : null);
        if (newText == null) {
            return;
        }
        if (mTvStatusText == null) {
            bind(msg);
            return;
        }
        CharSequence currentText = mTvStatusText.getText();
        if (currentText == null || currentText.length() == 0) {
            mTvStatusText.setText(newText);
        } else {
            mTvStatusText.append("\n\n");
            mTvStatusText.append(newText);
        }
    }

    private String extractFormattedStatus(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String trimmed = text.trim();

        // Do NOT display raw MCP tool execution results or raw text outputs to the user
        if (trimmed.contains("[MCP Tool Execution Output")) {
            return null;
        }

        // Explicitly allowed plain text system status messages
        if ("Optimizing Tool Selection".equalsIgnoreCase(trimmed) || trimmed.toLowerCase().startsWith("loading skill")) {
            return trimmed;
        }

        // Try parsing JSON tool call
        int jsonStart = trimmed.indexOf("{");
        int jsonEnd = trimmed.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd > jsonStart && trimmed.contains("\"tool\"")) {
            try {
                JSONObject json = new JSONObject(trimmed.substring(jsonStart, jsonEnd + 1));
                String toolName = json.optString("tool", "");
                JSONObject args = json.optJSONObject("args");

                CharSequence formatted = getMcpClientManager().formatToolCall(toolName, args);
                if (formatted != null) {
                    return formatted.toString();
                }
            } catch (Exception ignored) {}
        }

        // Block any other arbitrary plain text from being rendered in CustomStatusToolMessage
        return null;
    }

    private McpClientManager getMcpClientManager() {
        return new McpClientManager();
    }
}
