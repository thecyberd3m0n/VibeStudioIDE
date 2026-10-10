package com.vibestudio.app.view;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.widget.TextView;

import com.vibestudio.app.chat.model.ChatMessage;

import org.json.JSONObject;

import java.lang.reflect.Field;

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
        super.bind(msg);

        String text = msg.getText();
        CharSequence formattedContent = text;

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
                        formattedContent = "Opened in editor: " + path;
                    } else if ("editor_read_file".equalsIgnoreCase(toolName)) {
                        int start = args.optInt("start_line", 1);
                        int end = args.optInt("end_line", 100);
                        formattedContent = "Reading " + path + " (lines " + start + "-" + end + ")";
                    } else if ("editor_replace_lines".equalsIgnoreCase(toolName)) {
                        int start = args.optInt("start_line", 1);
                        int end = args.optInt("end_line", start);
                        String newContent = args.optString("new_content", "");

                        int addedLines = newContent.isEmpty() ? 0 : newContent.split("\r?\n", -1).length;
                        int removedLines = Math.max(0, end - start + 1);

                        String lineRange = (start == end) ? " [line " + start + "]" : " [lines " + start + "-" + end + "]";
                        String diffHtml = "Updated " + path + lineRange + " ("
                                + "<font color=\"#4CAF50\">+" + addedLines + "</font>, "
                                + "<font color=\"#F44336\">-" + removedLines + "</font>)";

                        formattedContent = parseHtml(diffHtml);

                    } else if ("editor_insert_lines".equalsIgnoreCase(toolName)) {
                        int line = args.optInt("line", 1);
                        String content = args.optString("content", "");

                        int addedLines = content.isEmpty() ? 0 : content.split("\r?\n", -1).length;
                        int removedLines = 0;

                        String diffHtml = "Updated " + path + " [at line " + line + "] ("
                                + "<font color=\"#4CAF50\">+" + addedLines + "</font>, "
                                + "<font color=\"#F44336\">-" + removedLines + "</font>)";

                        formattedContent = parseHtml(diffHtml);

                    } else if ("editor_search_replace".equalsIgnoreCase(toolName)) {
                        formattedContent = "Replaced pattern in " + path + ": '" + args.optString("search", "") + "' -> '" + args.optString("replace", "") + "'";
                    } else if ("editor_save".equalsIgnoreCase(toolName)) {
                        formattedContent = "Saved changes to " + path;
                    } else {
                        formattedContent = toolName + "(" + args.toString() + ")";
                    }
                }
            } catch (Exception ignored) {}
        }

        setBodyText(formattedContent);
    }

    private Spanned parseHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(html);
        }
    }

    private void setBodyText(CharSequence text) {
        try {
            Field field = BaseToolMessageView.class.getDeclaredField("mTvBodyContent");
            field.setAccessible(true);
            TextView tv = (TextView) field.get(this);
            if (tv != null) {
                tv.setText(text);
            }
        } catch (Exception ignored) {}
    }
}
