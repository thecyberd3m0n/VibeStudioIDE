package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.CustomStatusToolMessage;

import org.json.JSONObject;

public class ToolMessageFactory {

    private static McpClientManager sMcpClientManager;

    public static void setMcpClientManager(McpClientManager clientManager) {
        sMcpClientManager = clientManager;
    }

    private static McpClientManager getClientManager() {
        if (sMcpClientManager == null) {
            sMcpClientManager = new McpClientManager();
        }
        return sMcpClientManager;
    }

    public static boolean isToolMessage(ChatMessage msg) {
        if (msg == null) return false;
        if (msg.getType() == ChatMessage.MessageType.TOOL_CALL || msg.getType() == ChatMessage.MessageType.TOOL_RESULT) {
            return true;
        }
        String text = msg.getText();
        if (text != null) {
            if (text.contains("{") && text.contains("\"tool\"")) return true;
            if (text.contains("[MCP Tool Execution Output")) return true;
            if ("Optimizing Tool Selection".equalsIgnoreCase(text) || text.toLowerCase().startsWith("loading skill:")) return true;
        }
        return false;
    }

    private static String extractToolName(ChatMessage msg) {
        if (msg == null) return "";
        String text = msg.getText();
        if (text == null) return "";

        String resultHeader = "[MCP Tool Execution Output for ";
        int headerStart = text.indexOf(resultHeader);
        if (headerStart >= 0) {
            int nameStart = headerStart + resultHeader.length();
            int headerEnd = text.indexOf(']', nameStart);
            if (headerEnd > nameStart) {
                return text.substring(nameStart, headerEnd).trim();
            }
        }

        int jsonStart = text.indexOf("{");
        int jsonEnd = text.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                JSONObject json = new JSONObject(text.substring(jsonStart, jsonEnd + 1));
                return json.optString("tool", "");
            } catch (Exception ignored) {}
        }
        return "";
    }

    public static BaseToolMessageView createToolMessageView(Context context, ChatMessage msg) {
        String toolName = extractToolName(msg);
        McpServer server = getClientManager().findServerForTool(toolName);
        BaseToolMessageView view = null;

        if (server != null) {
            view = server.createToolMessageView(context, toolName);
        }

        if (view == null) {
            view = new CustomStatusToolMessage(context);
        }

        view.bind(msg);
        return view;
    }
}
