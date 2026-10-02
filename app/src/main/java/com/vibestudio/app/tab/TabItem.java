package com.vibestudio.app.tab;

import androidx.fragment.app.Fragment;
import java.util.UUID;

public class TabItem {
    private final String id;
    private final TabType type;
    private String title;
    private String icon;
    private Fragment fragment;

    public TabItem(TabType type, String title, String icon, Fragment fragment) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.title = title != null ? title : type.getDefaultTitle();
        this.icon = icon != null ? icon : type.getDefaultIcon();
        this.fragment = fragment;
    }

    public String getId() {
        return id;
    }

    public TabType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Fragment getFragment() {
        return fragment;
    }

    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
    }

    public boolean isCloseable() {
        return type != TabType.LAUNCHER;
    }
}
