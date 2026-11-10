package com.vibestudio.app;

import android.app.Application;
import android.content.Intent;
import com.vibestudio.app.chat.provider.GithubCopilotAiProvider;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.service.McpService;

public class VibeApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(this);
        LogViewerService.getInstance().startLogcatCapture();
        GithubCopilotAiProvider.initEditorVersion(this);

        Intent mcpServiceIntent = new Intent(this, McpService.class);
        startService(mcpServiceIntent);
    }
}
