package com.vibestudio.app.files.model;

import android.content.Context;
import java.io.File;

public abstract class FileEntry {
    private final String name;
    private final File file;

    public FileEntry(String name, File file) {
        this.name = name;
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public File getFile() {
        return file;
    }

    public abstract String getIconSymbol();

    public abstract String getDetailsText(Context context);

    public abstract boolean isUpDirectory();

    public abstract boolean isDirectory();
}
