package com.vibestudio.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.R;
import com.vibestudio.app.browser.BrowserManager;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.terminal.TerminalSessionManager;

public class  LauncherFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);

        view.findViewById(R.id.btn_launch_files).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.FILES, new FilesFragment());
        });

        view.findViewById(R.id.btn_launch_chat).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.CHAT, new ChatFragment());
        });

        view.findViewById(R.id.btn_launch_browser).setOnClickListener(v -> {
            WebView webView = BrowserManager.getInstance().createWebView(getContext());
            TabManager.getInstance().openTab(TabType.BROWSER, BrowserFragment.newInstance(webView));
        });

        view.findViewById(R.id.btn_launch_terminal).setOnClickListener(v -> {
            TerminalSession session = TerminalSessionManager.getInstance().createSession(getContext());
            TabManager.getInstance().openTab(TabType.TERMINAL, TerminalFragment.newInstance(session));
        });

        view.findViewById(R.id.btn_launch_editor).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.EDITOR, EditorFragment.newInstance());
        });

        view.findViewById(R.id.btn_launch_settings).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.SETTINGS, new SettingsFragment());
        });

        return view;
    }
}
