package com.vibestudio.app.files.model;

import android.content.Context;
import java.io.File;

public class UpDirEntry extends FileEntry {

    public UpDirEntry(File parentFile) {
        super("..", parentFile);
    }

    @Override
    public String getIconSymbol() {
        return "⬆️";
    }

    @Override
    public String getDetailsText(Context context) {
        return "Parent Directory";
    }

    @Override
    public boolean isUpDirectory() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }
}
