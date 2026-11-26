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
    LAUNCHER("Launcher", "📱", LauncherFragment.class, false),
    FILES("Files", "📁", FilesFragment.class, true),
    CHAT("Assistant Chat", "💬", ChatFragment.class, false),
    BROWSER("Browser", "🌐", BrowserFragment.class, false),
    TERMINAL("Terminal", "💻", TerminalFragment.class, false),
    EDITOR("Editor", "📝", EditorFragment.class, false),
    IMAGE("Image Viewer", "🖼️", ImageViewerFragment.class, false),
    SETTINGS("Settings", "⚙️", SettingsFragment.class, true);

    private final String defaultTitle;
    private final String defaultIcon;
    private final Class<? extends Fragment> fragmentClass;
    private final boolean singleInstance;

    TabType(String defaultTitle, String defaultIcon, Class<? extends Fragment> fragmentClass, boolean singleInstance) {
        this.defaultTitle = defaultTitle;
        this.defaultIcon = defaultIcon;
        this.fragmentClass = fragmentClass;
        this.singleInstance = singleInstance;
    }

    /** Single-instance types reuse (and re-select) their already open tab instead of opening another. */
    public boolean isSingleInstance() {
        return singleInstance;
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