package com.vibestudio.app.files.model;

import android.content.Context;
import android.text.format.Formatter;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RegularFileEntry extends FileEntry {

    public RegularFileEntry(File file) {
        super(file.getName(), file);
    }

    @Override
    public String getIconSymbol() {
        String lower = getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".h")) {
            return "💻";
        } else if (lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "⚙️";
        } else if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return "📝";
        } else if (lower.endsWith(".sh") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts")) {
            return "📜";
        } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return "🖼️";
        } else {
            return "📄";
        }
    }

    @Override
    public String getDetailsText(Context context) {
        String sizeStr = Formatter.formatShortFileSize(context, getFile().length());
        String dateStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date(getFile().lastModified()));
        return sizeStr + " • " + dateStr;
    }

    @Override
    public boolean isUpDirectory() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }
}
