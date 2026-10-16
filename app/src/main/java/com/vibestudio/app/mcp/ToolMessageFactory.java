package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.CustomStatusToolMessage;

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
        return msg != null && (msg.getType() == ChatMessage.MessageType.TOOL_CALL || msg.getType() == ChatMessage.MessageType.TOOL_RESULT);
    }

    public static BaseToolMessageView createToolMessageView(Context context, ChatMessage msg) {
        String toolName = msg != null ? msg.getToolName() : "";
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
