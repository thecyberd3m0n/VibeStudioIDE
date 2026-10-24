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

public class LauncherFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);

        view.findViewById(R.id.btn_launch_files).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.FILES);
        });

        view.findViewById(R.id.btn_launch_chat).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.CHAT);
        });

        view.findViewById(R.id.btn_launch_browser).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.BROWSER);
        });

        view.findViewById(R.id.btn_launch_terminal).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.TERMINAL);
        });

        view.findViewById(R.id.btn_launch_settings).setOnClickListener(v -> {
            TabManager.getInstance().openTab(TabType.SETTINGS);
        });

        return view;
    }
}
