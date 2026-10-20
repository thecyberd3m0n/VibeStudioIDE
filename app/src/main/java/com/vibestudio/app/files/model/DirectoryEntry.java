package com.vibestudio.app.files.model;

import android.content.Context;
import com.vibestudio.app.git.GitManager;
import com.vibestudio.app.R;
import java.io.File;

public class DirectoryEntry extends FileEntry {

    public DirectoryEntry(File file) {
        super(file.getName(), file);
    }

    @Override
    public String getIconSymbol() {
        if (GitManager.getInstance().isGitRoot(getFile())) {
            return null;
        }
        return "📁";
    }

    @Override
    public Integer getIconResId() {
        if (GitManager.getInstance().isGitRoot(getFile())) {
            return R.drawable.ic_git_repo;
        }
        return null;
    }

    @Override
    public String getDetailsText(Context context) {
        File[] children = getFile().listFiles();
        int childCount = children != null ? children.length : 0;
        if (GitManager.getInstance().isGitRoot(getFile())) {
            return childCount + " items • Git Repository";
        }
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
