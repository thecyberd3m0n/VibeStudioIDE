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
        if (msg == null) return "";

        String toolName = msg.getToolName();
        if (toolName != null && !toolName.isEmpty()) {
            JSONObject args = msg.getToolArgs();
            if (args == null) args = new JSONObject();
            CharSequence customFormat = getMcpClientManager().formatToolCall(toolName, args);
            if (customFormat != null) {
                return customFormat;
            }
            return "Executing " + toolName + "...";
        }

        String text = msg.getText();
        return text != null ? text : "";
    }

    private McpClientManager getMcpClientManager() {
        return new McpClientManager();
    }
}
