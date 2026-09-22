package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.BrowserLogsToolMessage;
import com.vibestudio.app.view.BrowserToolMessage;
import com.vibestudio.app.view.CustomStatusToolMessage;
import com.vibestudio.app.view.TerminalToolMessage;

public class ToolMessageFactory {

    /**
     * Centralized logic to determine if a ChatMessage is a tool call message.
     */
    public static boolean isToolMessage(ChatMessage msg) {
        if (msg == null) return false;
        if (msg.getType() == ChatMessage.MessageType.TOOL_CALL) {
            return true;
        }
        String text = msg.getText();
        return text != null && text.contains("{") && text.contains("\"tool\"");
    }

    /**
     * Factory method returning the appropriate BaseToolMessageView implementation.
     */
    public static BaseToolMessageView createToolMessageView(Context context, ChatMessage msg) {
        if (!isToolMessage(msg)) {
            TerminalToolMessage defaultView = new TerminalToolMessage(context);
            defaultView.bind(msg);
            return defaultView;
        }

        String text = msg.getText();
        if (text != null) {
            if ("Optimizing Tool Selection".equalsIgnoreCase(text) || text.contains("get_skill_schema")) {
                CustomStatusToolMessage statusView = new CustomStatusToolMessage(context);
                statusView.bind(msg);
                return statusView;
            } else if (text.contains("browser_") || text.contains("\"browser\"")) {
                if (text.contains("read_browser_logs") || text.contains("browser_logs")) {
                    BrowserLogsToolMessage logsView = new BrowserLogsToolMessage(context);
                    logsView.bind(msg);
                    return logsView;
                } else {
                    BrowserToolMessage browserView = new BrowserToolMessage(context);
                    browserView.bind(msg);
                    return browserView;
                }
            } else if (text.contains("execute_command") || text.contains("read_terminal_output") || text.contains("terminal")) {
                TerminalToolMessage terminalView = new TerminalToolMessage(context);
                terminalView.bind(msg);
                return terminalView;
            }
        }

        // Fallback default
        TerminalToolMessage defaultView = new TerminalToolMessage(context);
        defaultView.bind(msg);
        return defaultView;
    }
}
