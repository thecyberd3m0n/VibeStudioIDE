package com.vibestudio.app.test;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ViewTreePrinter {

    public static String dump(View view) {
        StringBuilder sb = new StringBuilder();
        dumpView(view, sb, 0);
        return sb.toString();
    }

    private static void dumpView(View view, StringBuilder sb, int indent) {
        if (view == null) return;

        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }

        String className = view.getClass().getSimpleName();
        if (className.isEmpty()) {
            className = view.getClass().getName();
        }

        sb.append("[").append(className).append("]");

        try {
            if (view.getId() != View.NO_ID && view.getResources() != null) {
                try {
                    String idName = view.getResources().getResourceEntryName(view.getId());
                    sb.append(" id=").append(idName);
                } catch (Exception ignored) {
                    sb.append(" id=").append(view.getId());
                }
            }
        } catch (Exception ignored) {}

        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) {
                sb.append(" text=\"").append(text.toString().replace("\n", "\\n")).append("\"");
            }
        }

        sb.append(" visible=").append(view.getVisibility() == View.VISIBLE);
        sb.append(" enabled=").append(view.isEnabled());

        sb.append("\n");

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpView(group.getChildAt(i), sb, indent + 1);
            }
        }
    }
}
