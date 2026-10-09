package com.vibestudio.app.files.action;

import android.content.Context;
import com.vibestudio.app.files.model.FileEntry;

public interface FileAction {
    String getTitle();
    boolean isAvailableFor(FileEntry entry);
    void execute(Context context, FileEntry entry, FileActionListener listener);

    interface FileActionListener {
        void onDirectoryChanged(java.io.File newDir);
        void onReloadRequested();
    }
}
