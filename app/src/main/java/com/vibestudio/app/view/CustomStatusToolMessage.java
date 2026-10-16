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
        String displayText = extractFormattedStatus(rawText);

        mTvStatusText.setText(displayText);
        addView(mTvStatusText);
    }

    @Override
    public void appendToolMessage(ChatMessage msg) {
        if (mTvStatusText == null) {
            bind(msg);
            return;
        }
        CharSequence currentText = mTvStatusText.getText();
        CharSequence newText = extractFormattedStatus(msg != null ? msg.getText() : null);
        if (currentText == null || currentText.length() == 0) {
            mTvStatusText.setText(newText);
        } else {
            mTvStatusText.append("\n\n");
            mTvStatusText.append(newText);
        }
    }

    private String extractFormattedStatus(String text) {
        if (text == null || text.trim().isEmpty()) return "processing...";
        String trimmed = text.trim();

        // If message is raw MCP Tool Execution Output JSON block, strip raw JSON output
        String resultHeader = "[MCP Tool Execution Output for ";
        int headerStart = trimmed.indexOf(resultHeader);
        if (headerStart >= 0) {
            int nameStart = headerStart + resultHeader.length();
            int headerEnd = trimmed.indexOf(']', nameStart);
            if (headerEnd > nameStart) {
                String toolName = trimmed.substring(nameStart, headerEnd).trim();
                CharSequence formatted = getMcpClientManager().formatToolCall(toolName, new JSONObject());
                if (formatted != null) {
                    return formatted.toString();
                }
                return toolName + " completed";
            }
        }

        if (!trimmed.startsWith("{") && !trimmed.contains("\"tool\"")) {
            return trimmed;
        }

        int jsonStart = trimmed.indexOf("{");
        int jsonEnd = trimmed.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd > jsonStart) {
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

        return trimmed;
    }

    private McpClientManager getMcpClientManager() {
        return new McpClientManager();
    }
}
