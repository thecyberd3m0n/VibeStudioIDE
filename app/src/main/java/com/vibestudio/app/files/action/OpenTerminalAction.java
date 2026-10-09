package com.vibestudio.app.files.action;

import android.content.Context;
import android.widget.Toast;
import com.termux.terminal.TerminalSession;
import com.vibestudio.app.files.model.FileEntry;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.terminal.TerminalSessionManager;

public class OpenTerminalAction implements FileAction {

    @Override
    public String getTitle() {
        return "Open Terminal Here";
    }

    @Override
    public boolean isAvailableFor(FileEntry entry) {
        return entry != null && entry.isDirectory() && !entry.isUpDirectory();
    }

    @Override
    public void execute(Context context, FileEntry entry, FileActionListener listener) {
        if (entry == null || entry.getFile() == null) return;
        String path = entry.getFile().getAbsolutePath();

        TerminalSession session = TerminalSessionManager.getInstance().createSession(context, path);
        if (session != null) {
            TabManager.getInstance().openTab(
                    TabType.TERMINAL,
                    "Terminal",
                    "💻",
                    TerminalFragment.newInstance(session),
                    true
            );
        } else {
            Toast.makeText(context, "Failed to start terminal session", Toast.LENGTH_SHORT).show();
        }
    }
}
