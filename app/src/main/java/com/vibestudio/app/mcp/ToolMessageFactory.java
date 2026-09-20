package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.service.ChatService;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.TerminalToolMessage;

public class ToolMessageFactory {

    /**
     * Centralized logic to determine if a ChatMessage is a tool call message.
     */
    public static boolean isToolMessage(ChatService.ChatMessage msg) {
        if (msg == null) return false;
        if (msg.getType() == ChatService.ChatMessage.MessageType.TOOL_CALL) {
            return true;
        }
        String text = msg.getText();
        return text != null && text.contains("{") && text.contains("\"tool\"");
    }

    /**
     * Determines whether the tool message is associated with the Terminal tool.
     */
    public static boolean isTerminalTool(ChatService.ChatMessage msg) {
        if (!isToolMessage(msg)) return false;
        String text = msg.getText();
        if (text == null) return false;

        if (text.contains("execute_command") || text.contains("read_terminal_output") || text.contains("terminal")) {
            return true;
        }

        try {
            if (text.contains("{") && text.contains("\"tool\"")) {
                int jsonStart = text.indexOf("{");
                int jsonEnd = text.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    org.json.JSONObject obj = new org.json.JSONObject(text.substring(jsonStart, jsonEnd + 1).trim());
                    String toolName = obj.optString("tool", "");
                    org.json.JSONObject args = obj.optJSONObject("args");
                    String skillName = args != null ? args.optString("skill_name", "") : "";

                    return "execute_command".equalsIgnoreCase(toolName)
                            || "read_terminal_output".equalsIgnoreCase(toolName)
                            || "terminal".equalsIgnoreCase(skillName);
                }
            }
        } catch (Exception ignored) {}

        return true; // Default fallback for system tool invocations
    }

    /**
     * Factory method returning the appropriate BaseToolMessageView view implementation.
     */
    public static BaseToolMessageView createToolMessageView(Context context, ChatService.ChatMessage msg) {
        if (isTerminalTool(msg)) {
            TerminalToolMessage terminalView = new TerminalToolMessage(context);
            terminalView.bind(msg);
            return terminalView;
        }

        // Extensible for future tools (e.g. OtherToolMessage)
        TerminalToolMessage defaultView = new TerminalToolMessage(context);
        defaultView.bind(msg);
        return defaultView;
    }
}
