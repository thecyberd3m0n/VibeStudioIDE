package com.vibestudio.app.test;

import com.vibestudio.app.db.DatabaseHelper;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(DatabaseHelper.class)
public class ShadowDatabaseHelper {

    @Implementation
    public boolean isEnvInitialized() {
        return false;
    }

    @Implementation
    public String getSetting(String key) {
        return null;
    }

    @Implementation
    public void setEnvInitialized(boolean value) {
    }
}
