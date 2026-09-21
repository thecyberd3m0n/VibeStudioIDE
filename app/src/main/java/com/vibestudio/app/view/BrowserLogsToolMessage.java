package com.vibestudio.app.view;

import android.content.Context;

public class BrowserLogsToolMessage extends BaseToolMessageView {

    public BrowserLogsToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "📋";
    }

    @Override
    protected String getHeaderTitle() {
        return "Browser Logs Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 6; // Logs tab index in MainActivity navigation
    }
}
