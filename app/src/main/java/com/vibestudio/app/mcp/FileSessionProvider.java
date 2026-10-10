package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.fragments.FilesFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

public class FileSessionProvider implements ToolSessionProvider {

    @Override
    public ToolType getToolType() {
        return ToolType.FILE;
    }

    @Override
    public Object createSessionHandle(Context context) {
        return new FilesFragment();
    }

    @Override
    public boolean isSessionAlive(Object handle) {
        return handle instanceof FilesFragment;
    }

    @Override
    public void openUiTab(Context context, int sessionId, Object handle) {
        if (handle instanceof FilesFragment) {
            TabManager.getInstance().openTab(
                    TabType.FILES,
                    "File Manager #" + sessionId,
                    "📁",
                    (FilesFragment) handle,
                    false
            );
        }
    }
}
