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

        String displayText = extractFormattedStatus(message);

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
        CharSequence newText = extractFormattedStatus(msg);
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

    private String extractFormattedStatus(ChatMessage msg) {
        if (msg == null) return null;
        
        String toolName = msg.getToolName();
        if (toolName != null && !toolName.isEmpty()) {
            JSONObject args = msg.getToolArgs();
            if (args == null) args = new JSONObject();
            CharSequence formatted = getMcpClientManager().formatToolCall(toolName, args);
            if (formatted != null) {
                return formatted.toString();
            }
            return "Executing " + toolName + "...";
        }
        
        return null;
    }

    private McpClientManager getMcpClientManager() {
        return new McpClientManager();
    }
}
