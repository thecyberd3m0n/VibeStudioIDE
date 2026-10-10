package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.fragments.EditorFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

public class EditorSessionProvider implements ToolSessionProvider {

    @Override
    public ToolType getToolType() {
        return ToolType.EDITOR;
    }

    @Override
    public Object createSessionHandle(Context context) {
        return EditorFragment.newInstance();
    }

    @Override
    public boolean isSessionAlive(Object handle) {
        return handle instanceof EditorFragment;
    }

    @Override
    public void openUiTab(Context context, int sessionId, Object handle) {
        if (handle instanceof EditorFragment) {
            TabManager.getInstance().openTab(
                    TabType.EDITOR,
                    "Editor #" + sessionId,
                    "📝",
                    (EditorFragment) handle,
                    false
            );
        }
    }
}
