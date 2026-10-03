package com.vibestudio.app.mcp;

import android.content.Context;

public interface ToolSessionProvider {
    ToolType getToolType();
    Object createSessionHandle(Context context);
    boolean isSessionAlive(Object handle);
    void openUiTab(Context context, int sessionId, Object handle);
}
