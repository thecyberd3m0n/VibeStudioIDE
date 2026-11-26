package com.vibestudio.app.ux;

import android.graphics.Bitmap;

import java.util.List;

/** Immutable snapshot of everything the taskbar needs to draw itself. Built by the host (MainActivity). */
public final class TaskbarUiState {

    public static final class Item {
        public final String id;
        public final String title;
        public final String iconText;
        public final Integer iconResId;
        public final Bitmap favicon;
        public final boolean closeable;
        public final boolean active;

        public Item(String id, String title, String iconText, Integer iconResId, Bitmap favicon,
                    boolean closeable, boolean active) {
            this.id = id;
            this.title = title;
            this.iconText = iconText;
            this.iconResId = iconResId;
            this.favicon = favicon;
            this.closeable = closeable;
            this.active = active;
        }
    }

    public final List<Item> items;

    public TaskbarUiState(List<Item> items) {
        this.items = items;
    }
}
