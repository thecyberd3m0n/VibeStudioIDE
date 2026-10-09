package com.vibestudio.app.tab;

public enum TabType {
    LAUNCHER("Launcher", "📱"),
    FILES("Files", "📁"),
    CHAT("Assistant Chat", "💬"),
    BROWSER("Browser", "🌐"),
    TERMINAL("Terminal", "💻"),
    SETTINGS("Settings", "⚙️");

    private final String defaultTitle;
    private final String defaultIcon;

    TabType(String defaultTitle, String defaultIcon) {
        this.defaultTitle = defaultTitle;
        this.defaultIcon = defaultIcon;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public String getDefaultIcon() {
        return defaultIcon;
    }
}
