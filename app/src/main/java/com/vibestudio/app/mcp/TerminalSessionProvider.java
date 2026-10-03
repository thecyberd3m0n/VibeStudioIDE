package com.vibestudio.app.mcp;

import android.content.Context;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.terminal.TerminalSessionManager;

public class TerminalSessionProvider implements ToolSessionProvider {

    @Override
    public ToolType getToolType() {
        return ToolType.TERMINAL;
    }

    @Override
    public Object createSessionHandle(Context context) {
        return TerminalSessionManager.getInstance().createSession(context);
    }

    @Override
    public boolean isSessionAlive(Object handle) {
        return handle instanceof TerminalSession && TerminalSessionManager.getInstance().isSessionAvailable((TerminalSession) handle);
    }

    @Override
    public void openUiTab(Context context, int sessionId, Object handle) {
        if (handle instanceof TerminalSession) {
            TabManager.getInstance().openTab(
                    TabType.TERMINAL,
                    "Terminal #" + sessionId,
                    "💻",
                    TerminalFragment.newInstance((TerminalSession) handle),
                    false
            );
        }
    }
}
