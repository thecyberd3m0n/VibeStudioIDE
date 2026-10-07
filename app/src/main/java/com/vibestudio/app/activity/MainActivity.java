package com.vibestudio.app.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.R;
import com.vibestudio.app.browser.BrowserManager;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.fragments.ChatFragment;
import com.vibestudio.app.fragments.EditorFragment;
import com.vibestudio.app.fragments.LauncherFragment;
import com.vibestudio.app.fragments.SettingsFragment;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.terminal.TerminalSessionManager;
import com.vibestudio.app.util.IntentUtils;

import java.io.File;
import java.util.List;

public class MainActivity extends FragmentActivity implements TabManager.TabListener {

    public static final String ACTION_EDIT_FILE = "com.vibestudio.app.ACTION_EDIT_FILE";
    public static final String EXTRA_FILE_PATH = "file_path";

    private LinearLayout mTabStripContainer;
    private View mBtnLauncherContainer;
    private TabManager mTabManager;
    private LauncherFragment mLauncherFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LogViewerService.getInstance().i("MainActivity", "MainActivity created with Tabbed Layout");
        if (getIntent() != null) {
            String initialFilePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
            Log.i("EditCmd", "onCreate Intent received with file_path: " + initialFilePath);
            LogViewerService.getInstance().i("MainActivity", "[EDIT_INTENT_RECEIVED] onCreate intent file_path: " + initialFilePath);
        }

        mTabStripContainer = findViewById(R.id.tab_strip_container);
        mBtnLauncherContainer = findViewById(R.id.btn_launcher_container);

        mTabManager = TabManager.getInstance();
        mTabManager.addListener(this);

        mLauncherFragment = new LauncherFragment();

        TerminalSessionManager.getInstance().setSessionEventListener(filePath -> {
            runOnUiThread(() -> openFileInEditor(filePath));
        });

        // Handle intent if MainActivity was launched with ACTION_EDIT_FILE or extra
        handleIntent(getIntent());

        // Big circle launcher button switches view directly to launcher (without creating a tab)
        mBtnLauncherContainer.setOnClickListener(v -> {
            hideSoftKeyboard();
            showLauncherOverlay();
        });

        renderTabs();
        if (mTabManager.getActiveTab() != null) {
            displayTabFragment(mTabManager.getActiveTab());
        } else {
            showLauncherOverlay();
        }

        handleIncomingIntent(getIntent());
    }

    public void openFileInEditor(String filePath) {
        LogViewerService.getInstance().i("MainActivity", "openFileInEditor called for: " + filePath);
        File file = new File(filePath);
        String fileName = file.getName();

        // Check if a tab with this file path is already open
        for (TabItem tab : mTabManager.getTabs()) {
            if (tab.getType() == TabType.EDITOR && tab.getFragment() instanceof EditorFragment) {
                EditorFragment ef = (EditorFragment) tab.getFragment();
                if (filePath.equals(ef.getFilePath())) {
                    LogViewerService.getInstance().i("MainActivity", "Found existing Editor tab for: " + filePath + ", selecting tab");
                    mTabManager.selectTab(tab);
                    return;
                }
            }
        }

        // Open a new tab for this file
        LogViewerService.getInstance().i("MainActivity", "Opening new Editor tab for: " + fileName + " (" + filePath + ")");
        EditorFragment fragment = EditorFragment.newInstance(filePath);
        mTabManager.openTab(
                TabType.EDITOR,
                fileName,
                "📝",
                fragment,
                true
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TerminalSessionManager.getInstance().setSessionEventListener(null);
        mTabManager.removeListener(this);
    }

    public void hideSoftKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
            android.util.Log.i("EditCmd", "Intent received/handled in MainActivity with file_path: " + filePath);
            LogViewerService.getInstance().i("MainActivity", "[EDIT_INTENT_RECEIVED] Intent handled with file_path: " + filePath);
            if (filePath != null && !filePath.isEmpty()) {
                openFileInEditor(filePath);
            } else {
                LogViewerService.getInstance().w("MainActivity", "[EDIT_INTENT_RECEIVED] Intent extra 'file_path' was empty or null.");
            }
        }
    }


    private void showLauncherOverlay() {
        mTabManager.clearActiveSelection();

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Re-instantiate launcher overlay if added to another FragmentManager
        if (mLauncherFragment.isAdded() && mLauncherFragment.getFragmentManager() != fm) {
            mLauncherFragment = new LauncherFragment();
        }

        // Hide all tabs
        for (TabItem tab : mTabManager.getTabs()) {
            Fragment f = tab.getFragment();
            if (f != null && f.isAdded() && f.getFragmentManager() == fm) {
                ft.hide(f);
            }
        }

        // Also hide any fragments attached to fm that are not launcher overlay
        for (Fragment f : fm.getFragments()) {
            if (f != null && f.isAdded() && f != mLauncherFragment && f.getFragmentManager() == fm) {
                ft.hide(f);
            }
        }

        if (!mLauncherFragment.isAdded()) {
            ft.add(R.id.content_frame, mLauncherFragment, "launcher_overlay");
        } else if (mLauncherFragment.getFragmentManager() == fm) {
            ft.show(mLauncherFragment);
        }

        ft.commitAllowingStateLoss();
        renderTabs();
    }

    public void selectNavigationItem(int navIndex) {
        TabType type;
        Fragment fragment = null;

        switch (navIndex) {
            case 2: // Terminal
                type = TabType.TERMINAL;
                break;
            case 3: // Chat
                type = TabType.CHAT;
                fragment = new ChatFragment();
                break;
            case 4: // Browser
                type = TabType.BROWSER;
                break;
            case 7: // Editor
                type = TabType.EDITOR;
                fragment = EditorFragment.newInstance();
                break;
            case 0: // Models
            case 1: // MCP
            case 5: // Permissions
            case 6: // Logs
            default:
                type = TabType.SETTINGS;
                fragment = new SettingsFragment();
                break;
        }

        // Search if tab of this type already exists and select it, or open a new one
        for (TabItem tab : mTabManager.getTabs()) {
            if (tab.getType() == type) {
                mTabManager.selectTab(tab);
                return;
            }
        }

        // Create independent User-initiated session (unmapped in ToolSessionManager)
        if (type == TabType.TERMINAL) {
            TerminalSession session = TerminalSessionManager.getInstance().createSession(this);
            mTabManager.openTab(
                    TabType.TERMINAL,
                    "Terminal",
                    "💻",
                    TerminalFragment.newInstance(session),
                    true
            );
            return;
        } else if (type == TabType.BROWSER) {
            WebView webView = BrowserManager.getInstance().createWebView(this);
            mTabManager.openTab(
                    TabType.BROWSER,
                    "Browser",
                    "🌐",
                    BrowserFragment.newInstance(webView),
                    true
            );
            return;
        } else if (type == TabType.EDITOR) {
            mTabManager.openTab(
                    TabType.EDITOR,
                    null,
                    "📝",
                    EditorFragment.newInstance(),
                    true
            );
            return;
        }

        mTabManager.openTab(type, null, null, fragment, true);
    }

    @Override
    public void onTabAdded(TabItem tab) {
        renderTabs();
        if (tab.getFragment() != null) {
            // Background tabs (not currently active) are added as hidden. Active tabs are handled in displayTabFragment via onTabSelected.
            if (mTabManager.getActiveTab() != tab) {
                FragmentManager fm = getSupportFragmentManager();
                Fragment f = tab.getFragment();
                if (!f.isAdded()) {
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(R.id.content_frame, f, tab.getId());
                    ft.hide(f);
                    ft.commitAllowingStateLoss();
                } else if (f.getFragmentManager() == fm) {
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.hide(f);
                    ft.commitAllowingStateLoss();
                }
            }
        }
    }

    @Override
    public void onTabRemoved(TabItem tab, int removedIndex) {
        Fragment frag = tab.getFragment();

        if (frag instanceof TerminalFragment) {
            TerminalSessionManager.getInstance().closeSession(((TerminalFragment) frag).getTerminalSession());
        } else if (frag instanceof BrowserFragment) {
            BrowserManager.getInstance().closeWebView(((BrowserFragment) frag).getWebView());
        }

        if (frag != null) {
            FragmentManager fm = getSupportFragmentManager();
            if (frag.getFragmentManager() == fm) {
                fm.beginTransaction()
                        .remove(frag)
                        .commitAllowingStateLoss();
            }
        }
        renderTabs();
        if (mTabManager.getTabs().isEmpty()) {
            showLauncherOverlay();
        }
    }

    @Override
    public void onTabSelected(TabItem tab) {
        hideSoftKeyboard();
        renderTabs();
        displayTabFragment(tab);
    }

    @Override
    public void onTabUpdated(TabItem tab) {
        renderTabs();
    }

    private void displayTabFragment(TabItem activeTab) {
        if (activeTab == null || activeTab.getFragment() == null) return;

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Always hide launcher overlay when displaying active tab fragment
        if (mLauncherFragment != null && mLauncherFragment.isAdded() && mLauncherFragment.getFragmentManager() == fm) {
            ft.hide(mLauncherFragment);
        }

        Fragment target = activeTab.getFragment();

        for (TabItem tab : mTabManager.getTabs()) {
            Fragment f = tab.getFragment();
            if (f != null && f.isAdded() && f.getFragmentManager() == fm) {
                if (f == target) {
                    ft.show(f);
                } else {
                    ft.hide(f);
                }
            }
        }

        for (Fragment f : fm.getFragments()) {
            if (f != null && f.isAdded() && f != mLauncherFragment && f.getFragmentManager() == fm) {
                if (f == target) {
                    ft.show(f);
                } else {
                    ft.hide(f);
                }
            }
        }

        if (!target.isAdded()) {
            ft.add(R.id.content_frame, target, activeTab.getId());
        } else if (target.getFragmentManager() == fm) {
            ft.show(target);
        }

        ft.commitAllowingStateLoss();
    }

    private void renderTabs() {
        mTabStripContainer.removeAllViews();
        List<TabItem> tabs = mTabManager.getTabs();
        TabItem activeTab = mTabManager.getActiveTab();

        LayoutInflater inflater = LayoutInflater.from(this);

        for (final TabItem tab : tabs) {
            View tabView = inflater.inflate(R.layout.tab_item_view, mTabStripContainer, false);

            TextView tvIcon = tabView.findViewById(R.id.tab_icon);
            ImageView ivFavicon = tabView.findViewById(R.id.tab_favicon);
            TextView tvTitle = tabView.findViewById(R.id.tab_title);
            TextView tvClose = tabView.findViewById(R.id.tab_close_btn);

            if (tab.getFavicon() != null) {
                ivFavicon.setImageBitmap(tab.getFavicon());
                ivFavicon.setVisibility(View.VISIBLE);
                tvIcon.setVisibility(View.GONE);
            } else {
                tvIcon.setText(tab.getIcon());
                tvIcon.setVisibility(View.VISIBLE);
                ivFavicon.setVisibility(View.GONE);
            }
            tvTitle.setText(tab.getTitle());

            boolean isActive = (activeTab != null && activeTab.getId().equals(tab.getId()));

            if (isActive) {
                tabView.setBackgroundColor(Color.parseColor("#1E1E1E"));
                tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
                tvIcon.setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                tabView.setBackgroundColor(Color.parseColor("#2D2D2D"));
                tvTitle.setTextColor(Color.parseColor("#969696"));
                tvIcon.setTextColor(Color.parseColor("#969696"));
            }

            if (!tab.isCloseable()) {
                tvClose.setVisibility(View.GONE);
            } else {
                tvClose.setVisibility(View.VISIBLE);
                tvClose.setOnClickListener(v -> {
                    mTabManager.closeTab(tab);
                });
            }

            tabView.setOnClickListener(v -> {
                mTabManager.selectTab(tab);
            });

            mTabStripContainer.addView(tabView);
        }
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String url = IntentUtils.extractUrlFromIntent(intent);
        if (url != null && !url.isEmpty()) {
            intent.setAction(null);
            intent.setData(null);
            openBrowserTabWithUrl(url);
        }
    }

    public void openBrowserTabWithUrl(String url) {
        WebView webView = BrowserManager.getInstance().createWebView(this);
        if (webView != null) {
            if (url != null && !url.isEmpty()) {
                BrowserManager.getInstance().navigate(webView, url);
            }
            mTabManager.openTab(
                    TabType.BROWSER,
                    "Browser",
                    "🌐",
                    BrowserFragment.newInstance(webView),
                    true
            );
        }
    }
}
