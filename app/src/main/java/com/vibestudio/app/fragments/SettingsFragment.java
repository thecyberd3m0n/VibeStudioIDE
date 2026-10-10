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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mBtnModels = view.findViewById(R.id.btn_settings_models);
        mBtnLogs = view.findViewById(R.id.btn_settings_logs);

        mBtnModels.setOnClickListener(v -> switchSubTab(0));
        mBtnLogs.setOnClickListener(v -> switchSubTab(1));

        if (savedInstanceState == null) {
            switchSubTab(0);
        }

        return view;
    }

    private void switchSubTab(int index) {
        Fragment selectedFragment;
        resetTabStyles();

        switch (index) {
            case 0:
                selectedFragment = new ModelsFragment();
                setTabActive(mBtnModels);
                break;
            case 1:
            default:
                selectedFragment = new LogViewerFragment();
                setTabActive(mBtnLogs);
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

        mBtnModels.setBackgroundColor(inactiveBg);
        mBtnModels.setTextColor(inactiveText);
        mBtnLogs.setBackgroundColor(inactiveBg);
        mBtnLogs.setTextColor(inactiveText);
    }

    private void setTabActive(TextView textView) {
        textView.setBackgroundColor(Color.parseColor("#2C2C3A"));
        textView.setTextColor(Color.parseColor("#03DAC6"));
    }
}
