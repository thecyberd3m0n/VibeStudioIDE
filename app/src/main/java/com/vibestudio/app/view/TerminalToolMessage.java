package com.vibestudio.app.view;

import android.content.Context;

public class TerminalToolMessage extends BaseToolMessageView {

    public TerminalToolMessage(Context context) {
        super(context);
    }

    @Override
    protected String getToolIcon() {
        return "💻";
    }

    @Override
    protected String getHeaderTitle() {
        return "Terminal Tool Used";
    }

    @Override
    protected int getTargetNavigationIndex() {
        return 2; // Terminal tab index in MainActivity navigation
    }
}
