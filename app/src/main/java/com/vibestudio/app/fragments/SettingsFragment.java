package com.vibestudio.app.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;

public class SettingsFragment extends Fragment {

    private TextView mBtnModels;
    private TextView mBtnLogs;
    private TextView mBtnGit;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mBtnModels = view.findViewById(R.id.btn_settings_models);
        mBtnLogs = view.findViewById(R.id.btn_settings_logs);
        mBtnGit = view.findViewById(R.id.btn_settings_git);

        if (mBtnModels != null) mBtnModels.setOnClickListener(v -> switchSubTab(0));
        if (mBtnLogs != null) mBtnLogs.setOnClickListener(v -> switchSubTab(1));
        if (mBtnGit != null) mBtnGit.setOnClickListener(v -> switchSubTab(2));

        if (savedInstanceState == null) {
            switchSubTab(0);
        }

        return view;
    }

    public void switchSubTab(int index) {
        Fragment selectedFragment;
        resetTabStyles();

        switch (index) {
            case 0:
                selectedFragment = new ModelsFragment();
                setTabActive(mBtnModels);
                break;
            case 1:
                selectedFragment = new LogViewerFragment();
                setTabActive(mBtnLogs);
                break;
            case 2:
            default:
                selectedFragment = new GitSettingsFragment();
                setTabActive(mBtnGit);
                break;
        }

        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_content_frame, selectedFragment)
                .commit();
    }

    private void resetTabStyles() {
        int inactiveBg = Color.parseColor("#1E1E28");
        int inactiveText = Color.parseColor("#A0A0A0");

        if (mBtnModels != null) {
            mBtnModels.setBackgroundColor(inactiveBg);
            mBtnModels.setTextColor(inactiveText);
        }
        if (mBtnLogs != null) {
            mBtnLogs.setBackgroundColor(inactiveBg);
            mBtnLogs.setTextColor(inactiveText);
        }
        if (mBtnGit != null) {
            mBtnGit.setBackgroundColor(inactiveBg);
            mBtnGit.setTextColor(inactiveText);
        }
    }

    private void setTabActive(TextView textView) {
        if (textView != null) {
            textView.setBackgroundColor(Color.parseColor("#2C2C3A"));
            textView.setTextColor(Color.parseColor("#03DAC6"));
        }
    }
}
