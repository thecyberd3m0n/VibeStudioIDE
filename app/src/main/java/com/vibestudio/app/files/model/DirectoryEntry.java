package com.vibestudio.app.files.model;

import android.content.Context;
import java.io.File;

public class DirectoryEntry extends FileEntry {

    public DirectoryEntry(File file) {
        super(file.getName(), file);
    }

    @Override
    public String getIconSymbol() {
        return "📁";
    }

    @Override
    public String getDetailsText(Context context) {
        File[] children = getFile().listFiles();
        int childCount = children != null ? children.length : 0;
        return childCount + " items";
    }

    @Override
    public boolean isUpDirectory() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }
}
