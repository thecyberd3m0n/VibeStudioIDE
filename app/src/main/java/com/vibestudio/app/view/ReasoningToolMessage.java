package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.util.FontUtils;

public class ReasoningToolMessage extends LinearLayout {

    private final LinearLayout mHeaderLayout;
    private final TextView mTvIcon;
    private final TextView mTvHeaderTitle;
    private final TextView mTvExpandArrow;

    private final LinearLayout mBodyLayout;
    private final TextView mTvBodyContent;

    private boolean mIsExpanded = true;

    public ReasoningToolMessage(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 8, 0, 8);
        setLayoutParams(containerParams);

        Typeface monoTypeface = FontUtils.getMonospaceTypeface(context);

        // --- HEADER LAYOUT ---
        mHeaderLayout = new LinearLayout(context);
        mHeaderLayout.setOrientation(LinearLayout.HORIZONTAL);
        mHeaderLayout.setGravity(Gravity.CENTER_VERTICAL);
        mHeaderLayout.setPadding(24, 14, 24, 14);
        mHeaderLayout.setBackgroundColor(Color.parseColor("#1F1A2E")); // Dark purple header style

        mTvIcon = new TextView(context);
        mTvIcon.setText("🧠");
        mTvIcon.setTextSize(14);
        mTvIcon.setPadding(0, 0, 16, 0);

        mTvHeaderTitle = new TextView(context);
        mTvHeaderTitle.setText("Reasoning");
        mTvHeaderTitle.setTextColor(Color.parseColor("#D4C5F9"));
        mTvHeaderTitle.setTextSize(13);
        mTvHeaderTitle.setTypeface(monoTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        mTvHeaderTitle.setLayoutParams(titleParams);

        mTvExpandArrow = new TextView(context);
        mTvExpandArrow.setText("▲"); // Expanded by default
        mTvExpandArrow.setTextColor(Color.parseColor("#A084E8"));
        mTvExpandArrow.setTextSize(11);
        mTvExpandArrow.setPadding(12, 0, 0, 0);

        mHeaderLayout.addView(mTvIcon);
        mHeaderLayout.addView(mTvHeaderTitle);
        mHeaderLayout.addView(mTvExpandArrow);

        // --- BODY LAYOUT ---
        mBodyLayout = new LinearLayout(context);
        mBodyLayout.setOrientation(LinearLayout.VERTICAL);
        mBodyLayout.setPadding(24, 16, 24, 16);
        mBodyLayout.setBackgroundColor(Color.parseColor("#151121")); // Dark purple/indigo body fill
        mBodyLayout.setVisibility(View.VISIBLE);

        mTvBodyContent = new TextView(context);
        mTvBodyContent.setTextColor(Color.parseColor("#C8C0E8"));
        mTvBodyContent.setTextSize(12);
        mTvBodyContent.setTypeface(monoTypeface, Typeface.ITALIC);
        mTvBodyContent.setTextIsSelectable(true);

        mBodyLayout.addView(mTvBodyContent);

        addView(mHeaderLayout);
        addView(mBodyLayout);

        // Toggle Expand/Collapse on Header click
        mHeaderLayout.setOnClickListener(v -> toggleExpand());
    }

    private void toggleExpand() {
        mIsExpanded = !mIsExpanded;
        mBodyLayout.setVisibility(mIsExpanded ? View.VISIBLE : View.GONE);
        mTvExpandArrow.setText(mIsExpanded ? "▲" : "▼");
    }

    public void bind(ChatMessage msg) {
        if (msg == null || msg.getText() == null || msg.getText().trim().isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mTvBodyContent.setText(msg.getText().trim());
    }

    public void appendToolMessage(ChatMessage msg) {
        if (msg == null || msg.getText() == null || msg.getText().trim().isEmpty()) {
            return;
        }
        CharSequence currentText = mTvBodyContent.getText();
        if (currentText == null || currentText.length() == 0) {
            mTvBodyContent.setText(msg.getText().trim());
        } else {
            mTvBodyContent.append("\n\n");
            mTvBodyContent.append(msg.getText().trim());
        }
    }
}
