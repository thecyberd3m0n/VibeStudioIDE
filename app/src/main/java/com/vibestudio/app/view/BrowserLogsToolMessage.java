package com.vibestudio.app.view;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.tab.TabType;

import org.json.JSONObject;

public class BrowserLogsToolMessage extends BaseToolMessageView {

    public BrowserLogsToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "📋";
    }

    @Override
    protected String getHeaderTitle() {
        return "Browser Logs Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 6; // Logs tab index in MainActivity navigation
    }

    @Override
    protected TabType getTargetTabType() {
        return TabType.SETTINGS;
    }

    @Override
    protected CharSequence formatToolContent(ChatMessage msg) {
        if (msg == null) return "";
        String text = msg.getText();
        if (text != null && text.contains("[MCP Tool Execution Output")) {
            return null;
        }
        String displayContent = text;

        if (text != null && text.contains("{") && text.contains("\"tool\"")) {
            try {
                int jsonStart = text.indexOf("{");
                int jsonEnd = text.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    JSONObject toolCallObj = new JSONObject(text.substring(jsonStart, jsonEnd + 1).trim());
                    String toolName = toolCallObj.optString("tool");
                    JSONObject args = toolCallObj.optJSONObject("args");
                    if (args == null) args = new JSONObject();

                    displayContent = "Reading browser logs (" + args.optInt("max_lines", 30) + " lines)";
                }
            } catch (Exception ignored) {}
        }

        return displayContent;
    }
}
