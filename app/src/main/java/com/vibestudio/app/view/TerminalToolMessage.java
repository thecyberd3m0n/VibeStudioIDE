package com.vibestudio.app.view;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.tab.TabType;

import org.json.JSONObject;

public class TerminalToolMessage extends BaseToolMessageView {

    public TerminalToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "💻";
    }

    @Override
    protected String getHeaderTitle() {
        return "Terminal Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 2;
    }

    @Override
    protected TabType getTargetTabType() {
        return TabType.TERMINAL;
    }

    @Override
    protected CharSequence formatToolContent(ChatMessage msg) {
        String text = msg.getText();

        if (text != null && text.contains("{") && text.contains("\"tool\"")) {
            try {
                int jsonStart = text.indexOf("{");
                int jsonEnd = text.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    JSONObject toolCallObj = new JSONObject(text.substring(jsonStart, jsonEnd + 1).trim());
                    String toolName = toolCallObj.optString("tool");
                    JSONObject args = toolCallObj.optJSONObject("args");
                    if (args == null) args = new JSONObject();

                    CharSequence customFormat = getMcpClientManager().formatToolCall(toolName, args);
                    if (customFormat != null) {
                        return customFormat;
                    }
                }
            } catch (Exception ignored) {}
        }

        return text;
    }

    private McpClientManager getMcpClientManager() {
        return new McpClientManager();
    }
}
