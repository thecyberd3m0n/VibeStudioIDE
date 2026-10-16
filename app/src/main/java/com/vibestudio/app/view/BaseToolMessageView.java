package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.util.FontUtils;

public abstract class BaseToolMessageView extends LinearLayout {

    private final LinearLayout mHeaderLayout;
    private final TextView mTvIcon;
    private final TextView mTvHeaderTitle;
    private final TextView mTvExpandArrow;

    private final LinearLayout mBodyLayout;
    protected final TextView mTvBodyContent;

    private boolean mIsExpanded = true;
    private boolean mIsErrorState = false;

    public BaseToolMessageView(Context context) {
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
        mHeaderLayout.setBackgroundColor(Color.parseColor("#181822")); // VSCode dark header style

        mTvIcon = new TextView(context);
        mTvIcon.setText(getToolIcon());
        mTvIcon.setTextSize(14);
        mTvIcon.setPadding(0, 0, 16, 0);

        mTvHeaderTitle = new TextView(context);
        mTvHeaderTitle.setText(getHeaderTitle());
        mTvHeaderTitle.setTextColor(Color.parseColor("#CCCCCC"));
        mTvHeaderTitle.setTextSize(13);
        mTvHeaderTitle.setTypeface(monoTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        mTvHeaderTitle.setLayoutParams(titleParams);

        mTvExpandArrow = new TextView(context);
        mTvExpandArrow.setText("▲"); // Expanded by default
        mTvExpandArrow.setTextColor(Color.parseColor("#007ACC"));
        mTvExpandArrow.setTextSize(11);
        mTvExpandArrow.setPadding(12, 0, 0, 0);

        mHeaderLayout.addView(mTvIcon);
        mHeaderLayout.addView(mTvHeaderTitle);
        mHeaderLayout.addView(mTvExpandArrow);

        // --- BODY LAYOUT ---
        mBodyLayout = new LinearLayout(context);
        mBodyLayout.setOrientation(LinearLayout.VERTICAL);
        mBodyLayout.setPadding(24, 16, 24, 16);
        mBodyLayout.setBackgroundColor(Color.parseColor("#111116")); // Darker body fill
        mBodyLayout.setVisibility(View.VISIBLE); // Expanded by default

        mTvBodyContent = new TextView(context);
        mTvBodyContent.setTextColor(Color.parseColor("#A0A0A0"));
        mTvBodyContent.setTextSize(12);
        mTvBodyContent.setTypeface(monoTypeface);
        mTvBodyContent.setTextIsSelectable(false); // Disables internal text-selection touch stealing so body click fires

        mBodyLayout.addView(mTvBodyContent);

        addView(mHeaderLayout);
        addView(mBodyLayout);

        // Toggle Expand/Collapse on Header click
        mHeaderLayout.setOnClickListener(v -> toggleExpand());

        // Navigation to target tool view on Body click or Body text click
        View.OnClickListener clickToNavigate = v -> navigateToTool();
        mBodyLayout.setOnClickListener(clickToNavigate);
        mTvBodyContent.setOnClickListener(clickToNavigate);
    }

    protected abstract String getToolIcon();
    protected abstract String getHeaderTitle();
    protected abstract int getTargetNavigationIndex();

    protected TabType getTargetTabType() {
        return null;
    }

    private void toggleExpand() {
        mIsExpanded = !mIsExpanded;
        mBodyLayout.setVisibility(mIsExpanded ? View.VISIBLE : View.GONE);
        mTvExpandArrow.setText(mIsExpanded ? "▲" : "▼");
    }

    private void navigateToTool() {
        TabType tabType = getTargetTabType();
        if (tabType != null) {
            TabManager tabManager = TabManager.getInstance();
            TabItem targetTab = tabManager.findTabByType(tabType);
            if (targetTab != null) {
                tabManager.selectTab(targetTab);
                return;
            }
        }

        int navIndex = getTargetNavigationIndex();
        Context ctx = getContext();
        if (ctx instanceof MainActivity) {
            ((MainActivity) ctx).selectNavigationItem(navIndex);
        } else if (ctx instanceof FragmentActivity) {
            FragmentActivity act = (FragmentActivity) ctx;
            if (act instanceof MainActivity) {
                ((MainActivity) act).selectNavigationItem(navIndex);
            }
        }
    }

    public void setErrorState(boolean isError) {
        mIsErrorState = isError;
        if (isError) {
            mHeaderLayout.setBackgroundColor(Color.parseColor("#4A1520")); // Header more red
            mBodyLayout.setBackgroundColor(Color.parseColor("#241014"));   // Content background slightly red
            mTvHeaderTitle.setTextColor(Color.parseColor("#FF9999"));
            mTvExpandArrow.setTextColor(Color.parseColor("#FF5555"));
            mTvBodyContent.setTextColor(Color.parseColor("#FFB0B0"));
        } else {
            mHeaderLayout.setBackgroundColor(Color.parseColor("#181822"));
            mBodyLayout.setBackgroundColor(Color.parseColor("#111116"));
            mTvHeaderTitle.setTextColor(Color.parseColor("#CCCCCC"));
            mTvExpandArrow.setTextColor(Color.parseColor("#007ACC"));
            mTvBodyContent.setTextColor(Color.parseColor("#A0A0A0"));
        }
    }

    public boolean isErrorState() {
        return mIsErrorState;
    }

    protected boolean isErrorContent(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        if (lower.contains("\"status\":\"error\"") || lower.contains("\"status\": \"error\"")) return true;
        if (lower.contains("tool execution threw exception")) return true;
        if (lower.contains("\"error\":") || lower.contains("\"exception\":")) return true;
        if (lower.startsWith("error:") || lower.startsWith("exception:")) return true;
        return false;
    }

    protected CharSequence formatToolContent(ChatMessage msg) {
        if (msg == null) return "";
        String text = msg.getText();
        if (text != null && text.contains("[MCP Tool Execution Output")) {
            return null;
        }
        return text != null ? text : "";
    }

    public void bind(ChatMessage msg) {
        CharSequence formatted = formatToolContent(msg);
        if (formatted == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mTvBodyContent.setText(formatted);
        if (msg != null && (msg.isError() || isErrorContent(msg.getText()))) {
            setErrorState(true);
        }
    }

    public void appendToolMessage(ChatMessage msg) {
        CharSequence newText = formatToolContent(msg);
        if (newText == null) {
            return;
        }
        CharSequence currentText = mTvBodyContent.getText();
        if (currentText == null || currentText.length() == 0) {
            mTvBodyContent.setText(newText);
        } else {
            if (currentText instanceof android.text.SpannableStringBuilder) {
                android.text.SpannableStringBuilder builder = (android.text.SpannableStringBuilder) currentText;
                builder.append("\n\n").append(newText);
                mTvBodyContent.setText(builder);
            } else {
                android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder(currentText);
                builder.append("\n\n").append(newText);
                mTvBodyContent.setText(builder);
            }
        }

        if (msg != null && (msg.isError() || isErrorContent(msg.getText()))) {
            setErrorState(true);
        }
    }
}
