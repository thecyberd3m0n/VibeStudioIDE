package com.vibestudio.app.view;

import android.content.Context;

public class BrowserToolMessage extends BaseToolMessageView {

    public BrowserToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "🌐";
    }

    @Override
    protected String getHeaderTitle() {
        return "Browser Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 4; // Browser tab index in MainActivity navigation
    }
}
