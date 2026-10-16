package com.vibestudio.app.view;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.tab.TabType;

import org.json.JSONObject;

public class FileToolMessage extends BaseToolMessageView {

    public FileToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "📁";
    }

    @Override
    protected String getHeaderTitle() {
        return "File Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 8; // Files Tab
    }

    @Override
    protected TabType getTargetTabType() {
        return TabType.FILES;
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

                    if ("file_list_directory".equalsIgnoreCase(toolName)) {
                        displayContent = "Traversing directory: " + args.optString("path", "~");
                    } else if ("file_create_file".equalsIgnoreCase(toolName)) {
                        displayContent = "Created file: " + args.optString("path", "");
                    } else if ("file_create_directory".equalsIgnoreCase(toolName)) {
                        displayContent = "Created directory: " + args.optString("path", "");
                    } else if ("file_delete".equalsIgnoreCase(toolName)) {
                        displayContent = "Deleted path: " + args.optString("path", "");
                    } else if ("file_move".equalsIgnoreCase(toolName)) {
                        displayContent = "Moved " + args.optString("src", "") + " -> " + args.optString("dst", "");
                    } else if ("file_copy".equalsIgnoreCase(toolName)) {
                        displayContent = "Copied " + args.optString("src", "") + " -> " + args.optString("dst", "");
                    } else if ("file_get_info".equalsIgnoreCase(toolName)) {
                        displayContent = "Inspected info for: " + args.optString("path", "");
                    } else {
                        displayContent = toolName + "(" + args.toString() + ")";
                    }
                }
            } catch (Exception ignored) {}
        }

        return displayContent;
    }
}
