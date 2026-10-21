package com.vibestudio.app.files.action;

import android.content.Context;
import android.widget.Toast;

import com.vibestudio.app.R;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.chat.session.ChatSessionManager;
import com.vibestudio.app.files.model.FileEntry;
import com.vibestudio.app.fragments.ChatFragment;
import com.vibestudio.app.git.GitManager;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import java.io.File;

public class OpenScopedChatAction implements FileAction {

    @Override
    public String getTitle() {
        return "Open Scoped Chat";
    }

    @Override
    public boolean isAvailableFor(FileEntry entry) {
        return entry != null && entry.isDirectory() && !entry.isUpDirectory();
    }

    @Override
    public void execute(Context context, FileEntry entry, FileActionListener listener) {
        if (entry == null || entry.getFile() == null) return;
        File dir = entry.getFile();
        String path = dir.getAbsolutePath();

        boolean isRepo = GitManager.getInstance().isGitRoot(dir) || GitManager.getInstance().isGitRepository(dir);
        Integer iconResId = isRepo ? R.drawable.ic_git_repo : null;
        String emojiIcon = isRepo ? null : "📁";

        ChatSession session = ChatSessionManager.getInstance().createSession(path);
        if (session != null) {
            String tabTitle = "Chat: " + dir.getName();
            TabManager.getInstance().openTab(
                    TabType.CHAT,
                    tabTitle,
                    emojiIcon,
                    iconResId,
                    ChatFragment.newInstance(session),
                    true
            );
        } else {
            Toast.makeText(context, "Failed to start scoped chat session", Toast.LENGTH_SHORT).show();
        }
    }
}
