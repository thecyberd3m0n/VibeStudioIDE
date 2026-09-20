package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.service.ChatService;

public class TerminalToolMessage extends LinearLayout {

    private final TextView mTvIcon;
    private final TextView mTvText;
    private final TextView mTvArrow;

    public TerminalToolMessage(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(24, 12, 24, 12);
        setBackgroundColor(Color.parseColor("#181822")); // VSCode-style sleek dark line

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 8, 0, 8);
        setLayoutParams(layoutParams);

        // Terminal Icon
        mTvIcon = new TextView(context);
        mTvIcon.setText("💻");
        mTvIcon.setTextSize(14);
        mTvIcon.setPadding(0, 0, 16, 0);

        // Single line concise message
        mTvText = new TextView(context);
        mTvText.setText("Terminal Tool used");
        mTvText.setTextColor(Color.parseColor("#858585")); // Subtle VSCode status grey
        mTvText.setTextSize(13);
        mTvText.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        mTvText.setLayoutParams(textParams);

        // Arrow indicator
        mTvArrow = new TextView(context);
        mTvArrow.setText("➔");
        mTvArrow.setTextColor(Color.parseColor("#007ACC")); // VSCode Accent Blue
        mTvArrow.setTextSize(12);
        mTvArrow.setPadding(12, 0, 0, 0);

        addView(mTvIcon);
        addView(mTvText);
        addView(mTvArrow);

        // Clicking navigates directly to Terminal
        setOnClickListener(v -> {
            if (getContext() instanceof MainActivity) {
                ((MainActivity) getContext()).selectNavigationItem(2); // Terminal index
            } else if (getContext() instanceof FragmentActivity) {
                FragmentActivity act = (FragmentActivity) getContext();
                if (act instanceof MainActivity) {
                    ((MainActivity) act).selectNavigationItem(2);
                }
            }
        });
    }

    public void bind(ChatService.ChatMessage msg) {
        // Simple single line confirmation without exposing raw content
        mTvText.setText("Terminal Tool used");
    }
}
