package com.vibestudio.app.editor;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

public class ThemeManager {
    private static ThemeManager instance;

    // Centralized IDE Dark Theme Palette
    public static final int COLOR_BACKGROUND_PRIMARY   = 0xFF1E1E2E; // #1E1E2E Main Editor, Terminal, Files dark background
    public static final int COLOR_BACKGROUND_SECONDARY = 0xFF181825; // #181825 Sidebar / Gutter background
    public static final int COLOR_BACKGROUND_HEADER    = 0xFF252536; // #252536 Top header, Tab bar, Inactive tab
    public static final int COLOR_TEXT_PRIMARY        = 0xFFCDD6F4; // #CDD6F4 High contrast primary text
    public static final int COLOR_TEXT_SECONDARY      = 0xFFA6ADC8; // #A6ADC8 Subtitle / Muted text
    public static final int COLOR_ACCENT              = 0xFF89B4FA; // #89B4FA Primary accent / active tab / buttons
    public static final int COLOR_SELECTION           = 0xFF313244; // #313244 Text selection / highlight

    private ThemeManager() {}

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public int getPrimaryBackgroundColor() {
        return COLOR_BACKGROUND_PRIMARY;
    }

    public int getSecondaryBackgroundColor() {
        return COLOR_BACKGROUND_SECONDARY;
    }

    public int getHeaderBackgroundColor() {
        return COLOR_BACKGROUND_HEADER;
    }

    public int getPrimaryTextColor() {
        return COLOR_TEXT_PRIMARY;
    }

    public int getSecondaryTextColor() {
        return COLOR_TEXT_SECONDARY;
    }

    public int getAccentColor() {
        return COLOR_ACCENT;
    }

    public int getSelectionColor() {
        return COLOR_SELECTION;
    }

    public EditorColorScheme createEditorColorScheme() {
        SchemeDarcula scheme = new SchemeDarcula();
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, COLOR_BACKGROUND_PRIMARY);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, COLOR_BACKGROUND_SECONDARY);
        scheme.setColor(EditorColorScheme.LINE_NUMBER, COLOR_TEXT_SECONDARY);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, COLOR_ACCENT);
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, COLOR_SELECTION);
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, COLOR_ACCENT);
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, COLOR_ACCENT);
        return scheme;
    }
}
