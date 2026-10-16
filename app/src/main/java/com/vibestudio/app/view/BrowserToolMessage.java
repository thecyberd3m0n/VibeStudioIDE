package com.vibestudio.app.view;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.tab.TabType;

import org.json.JSONObject;

public class BrowserToolMessage extends BaseToolMessageView {

    public BrowserToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "🌐";
    }

    @Override
    protected String getHeaderTitle() {
        return "Browser Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 4; // Browser tab index in MainActivity navigation
    }

    @Override
    protected TabType getTargetTabType() {
        return TabType.BROWSER;
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

                    displayContent = "Browser Tool [" + toolName + "]: " + args.toString();
                }
            } catch (Exception ignored) {}
        }

        return displayContent;
    }
}
