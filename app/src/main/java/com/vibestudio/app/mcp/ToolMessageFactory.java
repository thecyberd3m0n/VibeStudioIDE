package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.BrowserLogsToolMessage;
import com.vibestudio.app.view.BrowserToolMessage;
import com.vibestudio.app.view.CustomStatusToolMessage;
import com.vibestudio.app.view.EditorToolMessage;
import com.vibestudio.app.view.FileToolMessage;
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
     * Returns the target BaseToolMessageView Class implementation for a given message.
     */
    public static Class<? extends BaseToolMessageView> getToolMessageClass(ChatMessage msg) {
        if (!isToolMessage(msg)) {
            return TerminalToolMessage.class;
        }

        String text = msg.getText();
        if (text != null) {
            if ("Optimizing Tool Selection".equalsIgnoreCase(text) || text.contains("get_skill_schema")) {
                return CustomStatusToolMessage.class;
            } else if (text.contains("file_") || text.contains("\"file\"") || text.contains("\"file_")) {
                return FileToolMessage.class;
            } else if (text.contains("editor_") || text.contains("\"editor\"") || text.contains("\"editor_")) {
                return EditorToolMessage.class;
            } else if (text.contains("browser_") || text.contains("\"browser\"")) {
                if (text.contains("read_browser_logs") || text.contains("browser_logs")) {
                    return BrowserLogsToolMessage.class;
                } else {
                    return BrowserToolMessage.class;
                }
            } else if (text.contains("execute_command") || text.contains("read_terminal_output") || text.contains("send_keystroke") || text.contains("terminal")) {
                return TerminalToolMessage.class;
            }
        }

        return TerminalToolMessage.class;
    }

    /**
     * Factory method returning the appropriate BaseToolMessageView implementation.
     */
    public static BaseToolMessageView createToolMessageView(Context context, ChatMessage msg) {
        Class<? extends BaseToolMessageView> clazz = getToolMessageClass(msg);
        BaseToolMessageView view;
        if (clazz == CustomStatusToolMessage.class) {
            view = new CustomStatusToolMessage(context);
        } else if (clazz == FileToolMessage.class) {
            view = new FileToolMessage(context);
        } else if (clazz == EditorToolMessage.class) {
            view = new EditorToolMessage(context);
        } else if (clazz == BrowserLogsToolMessage.class) {
            view = new BrowserLogsToolMessage(context);
        } else if (clazz == BrowserToolMessage.class) {
            view = new BrowserToolMessage(context);
        } else {
            view = new TerminalToolMessage(context);
        }

        view.bind(msg);
        return view;
    }
}
