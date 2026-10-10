package com.vibestudio.app.view;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;

import org.json.JSONObject;

public class EditorToolMessage extends BaseToolMessageView {

    public EditorToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "📝";
    }

    @Override
    protected String getHeaderTitle() {
        return "Editor Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 7; // Editor Tab
    }

    @Override
    public void bind(ChatMessage msg) {
        String text = msg.getText();
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

                    String path = args.optString("path", "");

                    if ("editor_open".equalsIgnoreCase(toolName)) {
                        displayContent = "Opened in editor: " + path;
                    } else if ("editor_read_file".equalsIgnoreCase(toolName)) {
                        int start = args.optInt("start_line", 1);
                        int end = args.optInt("end_line", 100);
                        displayContent = "Reading " + path + " (lines " + start + "-" + end + ")";
                    } else if ("editor_replace_lines".equalsIgnoreCase(toolName)) {
                        int start = args.optInt("start_line", 1);
                        int end = args.optInt("end_line", 1);
                        String newContent = args.optString("new_content", "");
                        int newLines = newContent.isEmpty() ? 0 : newContent.split("\r?\n", -1).length;
                        int removedLines = Math.max(0, end - start + 1);
                        displayContent = "Updated " + path + " [lines " + start + "-" + end + "] (+" + newLines + " / -" + removedLines + " lines)";
                    } else if ("editor_insert_lines".equalsIgnoreCase(toolName)) {
                        int line = args.optInt("line", 1);
                        String content = args.optString("content", "");
                        int addedLines = content.isEmpty() ? 0 : content.split("\r?\n", -1).length;
                        displayContent = "Updated " + path + " [at line " + line + "] (+" + addedLines + " / -0 lines)";
                    } else if ("editor_search_replace".equalsIgnoreCase(toolName)) {
                        displayContent = "Replaced pattern in " + path + ": '" + args.optString("search", "") + "' -> '" + args.optString("replace", "") + "'";
                    } else if ("editor_save".equalsIgnoreCase(toolName)) {
                        displayContent = "Saved changes to " + path;
                    } else {
                        displayContent = toolName + "(" + args.toString() + ")";
                    }
                }
            } catch (Exception ignored) {}
        }

        super.bind(msg);
        setBodyText(displayContent);
    }

    private void setBodyText(String text) {
        try {
            java.lang.reflect.Field field = BaseToolMessageView.class.getDeclaredField("mTvBodyContent");
            field.setAccessible(true);
            android.widget.TextView tv = (android.widget.TextView) field.get(this);
            if (tv != null) {
                tv.setText(text);
            }
        } catch (Exception ignored) {}
    }
}
