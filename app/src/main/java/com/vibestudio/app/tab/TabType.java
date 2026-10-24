package com.vibestudio.app.tab;

import androidx.fragment.app.Fragment;

import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.fragments.ChatFragment;
import com.vibestudio.app.fragments.EditorFragment;
import com.vibestudio.app.fragments.FilesFragment;
import com.vibestudio.app.fragments.ImageViewerFragment;
import com.vibestudio.app.fragments.LauncherFragment;
import com.vibestudio.app.fragments.SettingsFragment;
import com.vibestudio.app.fragments.TerminalFragment;

public enum TabType {
    LAUNCHER("Launcher", "📱", LauncherFragment.class),
    FILES("Files", "📁", FilesFragment.class),
    CHAT("Assistant Chat", "💬", ChatFragment.class),
    BROWSER("Browser", "🌐", BrowserFragment.class),
    TERMINAL("Terminal", "💻", TerminalFragment.class),
    EDITOR("Editor", "📝", EditorFragment.class),
    IMAGE("Image Viewer", "🖼️", ImageViewerFragment.class),
    SETTINGS("Settings", "⚙️", SettingsFragment.class);

    private final String defaultTitle;
    private final String defaultIcon;
    private final Class<? extends Fragment> fragmentClass;

    TabType(String defaultTitle, String defaultIcon, Class<? extends Fragment> fragmentClass) {
        this.defaultTitle = defaultTitle;
        this.defaultIcon = defaultIcon;
        this.fragmentClass = fragmentClass;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public String getDefaultIcon() {
        return defaultIcon;
    }

    public Fragment createDefaultFragment() {
        try {
            return fragmentClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return new Fragment();
        }
    }
}
