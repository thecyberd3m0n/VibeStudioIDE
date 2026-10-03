package com.vibestudio.app.tab;

import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TabManager {
    private static TabManager instance;

    public interface TabListener {
        void onTabAdded(TabItem tab);
        void onTabRemoved(TabItem tab, int removedIndex);
        void onTabSelected(TabItem tab);
        void onTabUpdated(TabItem tab);
    }

    private final List<TabItem> tabs = new ArrayList<>();
    private final List<TabListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TabItem activeTab;
    private int sessionCounterTerminal = 0;
    private int sessionCounterChat = 0;

    private TabManager() {}

    public static synchronized TabManager getInstance() {
        if (instance == null) {
            instance = new TabManager();
        }
        return instance;
    }

    public void addListener(TabListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(TabListener listener) {
        listeners.remove(listener);
    }

    public List<TabItem> getTabs() {
        return Collections.unmodifiableList(tabs);
    }

    public TabItem getActiveTab() {
        return activeTab;
    }

    public TabItem findTabByType(TabType type) {
        for (TabItem tab : tabs) {
            if (tab.getType() == type) {
                return tab;
            }
        }
        return null;
    }

    public TabItem findTabByFragment(Fragment fragment) {
        for (TabItem tab : tabs) {
            if (tab.getFragment() == fragment) {
                return tab;
            }
        }
        return null;
    }

    public TabItem findTabByTitle(String title) {
        if (title == null) return null;
        for (TabItem tab : tabs) {
            if (title.equals(tab.getTitle())) {
                return tab;
            }
        }
        return null;
    }

    public void clearActiveSelection() {
        this.activeTab = null;
    }

    public void selectTab(final TabItem tab) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    selectTab(tab);
                }
            });
            return;
        }

        if (tab == null || !tabs.contains(tab)) return;
        this.activeTab = tab;
        for (TabListener listener : new ArrayList<>(listeners)) {
            listener.onTabSelected(tab);
        }
    }

    public void selectTabById(String id) {
        for (TabItem tab : tabs) {
            if (tab.getId().equals(id)) {
                selectTab(tab);
                break;
            }
        }
    }

    public TabItem openTab(TabType type, Fragment fragment) {
        return openTab(type, null, null, fragment);
    }

    public TabItem openTab(final TabType type, final String customTitle, final String icon, final Fragment fragment) {
        return openTab(type, customTitle, icon, fragment, true);
    }

    public TabItem openTab(final TabType type, final String customTitle, final String icon, final Fragment fragment, final boolean selectNewTab) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final AtomicTabItemHolder holder = new AtomicTabItemHolder();
            final CountDownLatch latch = new CountDownLatch(1);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    holder.tab = openTab(type, customTitle, icon, fragment, selectNewTab);
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException ignored) {}
            return holder.tab;
        }

        String title = customTitle;
        if (title == null) {
            switch (type) {
                case CHAT:
                    sessionCounterChat++;
                    title = "Chat #" + sessionCounterChat;
                    break;
                case TERMINAL:
                    title = "Terminal";
                    break;
                case BROWSER:
                    title = "Browser";
                    break;
                case SETTINGS:
                    title = "Settings";
                    break;
                default:
                    title = type.getDefaultTitle();
            }
        }

        TabItem newTab = new TabItem(type, title, icon, fragment);
        tabs.add(newTab);

        if (selectNewTab || activeTab == null) {
            activeTab = newTab;
        }

        for (TabListener listener : new ArrayList<>(listeners)) {
            listener.onTabAdded(newTab);
            if (selectNewTab) {
                listener.onTabSelected(newTab);
            }
        }

        return newTab;
    }

    private static class AtomicTabItemHolder {
        TabItem tab;
    }

    public boolean closeTab(final TabItem tab) {
        if (tab == null || !tab.isCloseable() || !tabs.contains(tab)) {
            return false;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    closeTab(tab);
                }
            });
            return true;
        }

        int index = tabs.indexOf(tab);
        tabs.remove(tab);

        for (TabListener listener : new ArrayList<>(listeners)) {
            listener.onTabRemoved(tab, index);
        }

        if (activeTab == tab) {
            if (!tabs.isEmpty()) {
                int nextIndex = Math.min(index, tabs.size() - 1);
                selectTab(tabs.get(nextIndex));
            } else {
                activeTab = null;
            }
        }

        return true;
    }

    public boolean closeTabById(String id) {
        for (TabItem tab : tabs) {
            if (tab.getId().equals(id)) {
                return closeTab(tab);
            }
        }
        return false;
    }

    public void updateTabTitle(final TabItem tab, final String newTitle) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateTabTitle(tab, newTitle);
                }
            });
            return;
        }

        if (tab != null && tabs.contains(tab)) {
            tab.setTitle(newTitle);
            for (TabListener listener : new ArrayList<>(listeners)) {
                listener.onTabUpdated(tab);
            }
        }
    }

    public void notifyTabUpdated(final TabItem tab) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyTabUpdated(tab);
                }
            });
            return;
        }

        if (tab != null && tabs.contains(tab)) {
            for (TabListener listener : new ArrayList<>(listeners)) {
                listener.onTabUpdated(tab);
            }
        }
    }

    public void clearAll() {
        tabs.clear();
        activeTab = null;
        sessionCounterTerminal = 0;
        sessionCounterChat = 0;
    }
}
