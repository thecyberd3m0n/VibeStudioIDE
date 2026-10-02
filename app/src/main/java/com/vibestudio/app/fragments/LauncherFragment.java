package com.vibestudio.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import java.util.UUID;

public class LauncherFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);

        view.findViewById(R.id.btn_launch_chat).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.CHAT, new ChatFragment());
        });

        view.findViewById(R.id.btn_launch_browser).setOnClickListener(v -> {
            String sessionId = "browser_" + UUID.randomUUID().toString().substring(0, 8);
            TabManager.getInstance().openTab(TabType.BROWSER, BrowserFragment.newInstance(sessionId));
        });

        view.findViewById(R.id.btn_launch_terminal).setOnClickListener(v -> {
            String sessionId = "term_" + UUID.randomUUID().toString().substring(0, 8);
            TabManager.getInstance().openTab(TabType.TERMINAL, TerminalFragment.newInstance(sessionId));
        });

        view.findViewById(R.id.btn_launch_settings).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.SETTINGS, new SettingsFragment());
        });

        return view;
    }
}
