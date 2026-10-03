package com.vibestudio.app.activity;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
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
import com.vibestudio.app.fragments.LauncherFragment;
import com.vibestudio.app.fragments.SettingsFragment;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.terminal.TerminalSessionManager;

import java.util.List;

public class MainActivity extends FragmentActivity implements TabManager.TabListener {

    private LinearLayout mTabStripContainer;
    private View mBtnLauncherContainer;
    private TabManager mTabManager;
    private LauncherFragment mLauncherFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LogViewerService.getInstance().i("MainActivity", "MainActivity created with Tabbed Layout");

        mTabStripContainer = findViewById(R.id.tab_strip_container);
        mBtnLauncherContainer = findViewById(R.id.btn_launcher_container);

        mTabManager = TabManager.getInstance();
        mTabManager.addListener(this);

        mLauncherFragment = new LauncherFragment();

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

    private void showLauncherOverlay() {
        mTabManager.clearActiveSelection();

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Hide all active tab fragments
        for (TabItem tab : mTabManager.getTabs()) {
            Fragment f = tab.getFragment();
            if (f != null && f.isAdded()) {
                ft.hide(f);
            }
        }

        if (!mLauncherFragment.isAdded()) {
            ft.add(R.id.content_frame, mLauncherFragment, "launcher_overlay");
        } else {
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
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(frag)
                    .commitAllowingStateLoss();
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
        if (mLauncherFragment != null && mLauncherFragment.isAdded()) {
            ft.hide(mLauncherFragment);
        }

        for (TabItem tab : mTabManager.getTabs()) {
            Fragment f = tab.getFragment();
            if (f != null && f.isAdded()) {
                if (tab == activeTab) {
                    ft.show(f);
                } else {
                    ft.hide(f);
                }
            }
        }

        Fragment target = activeTab.getFragment();
        if (!target.isAdded()) {
            ft.add(R.id.content_frame, target, activeTab.getId());
        } else {
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
}
