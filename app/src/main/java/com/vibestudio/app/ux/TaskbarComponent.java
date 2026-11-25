package com.vibestudio.app.ux;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.vibestudio.app.R;

/**
 * Presentational taskbar: draws a {@link TaskbarUiState} and reports user intents via {@link Listener}.
 * Holds no business state and never talks to TabManager; the host owns and syncs the state.
 */
public class TaskbarComponent {

    public interface Listener {
        void onTabSelected(String tabId);
        void onTabCloseRequested(String tabId);
    }

    private final Context mContext;
    private final LinearLayout mStrip;
    private final HorizontalScrollView mScroll;
    private final TextView mOverflowBtn;
    private final Listener mListener;
    private TaskbarUiState mState = new TaskbarUiState(java.util.Collections.emptyList());

    public TaskbarComponent(View root, Listener listener) {
        mContext = root.getContext();
        mListener = listener;
        mStrip = root.findViewById(R.id.tab_strip_container);
        mScroll = root.findViewById(R.id.tab_scroll_view);
        mOverflowBtn = root.findViewById(R.id.btn_tab_overflow);
        ((View) mScroll.getParent()).addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (r - l != or - ol) fitTabs();
        });
        mOverflowBtn.setOnClickListener(v -> showOverflowPopup());
    }

    public void render(TaskbarUiState state) {
        mState = state;
        mStrip.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        for (TaskbarUiState.Item item : state.items) {
            View tabView = inflater.inflate(R.layout.tab_item_view, mStrip, false);
            bindItem(tabView, item, null);
            mStrip.addView(tabView);
        }
        mStrip.post(this::fitTabs);
    }

    private void showOverflowPopup() {
        LinearLayout list = new LinearLayout(mContext);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.parseColor("#2D2D2D"));
        PopupWindow popup = new PopupWindow(list,
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2D2D2D")));
        for (int i = 0; i < mStrip.getChildCount() && i < mState.items.size(); i++) {
            if (mStrip.getChildAt(i).getVisibility() != View.GONE) continue;
            View row = LayoutInflater.from(mContext).inflate(R.layout.tab_item_view, list, false);
            bindItem(row, mState.items.get(i), popup::dismiss);
            row.setMinimumHeight((int) (44 * mContext.getResources().getDisplayMetrics().density));
            list.addView(row);
        }
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.showAsDropDown(mOverflowBtn, mOverflowBtn.getWidth() - list.getMeasuredWidth(),
                -mOverflowBtn.getHeight() - list.getMeasuredHeight());
    }

    private void bindItem(View tabView, TaskbarUiState.Item item, Runnable afterAction) {
        TextView tvIcon = tabView.findViewById(R.id.tab_icon);
        ImageView ivFavicon = tabView.findViewById(R.id.tab_favicon);
        TextView tvTitle = tabView.findViewById(R.id.tab_title);
        TextView tvClose = tabView.findViewById(R.id.tab_close_btn);

        if (item.favicon != null) {
            ivFavicon.setImageBitmap(item.favicon);
            ivFavicon.setVisibility(View.VISIBLE);
            tvIcon.setVisibility(View.GONE);
        } else if (item.iconResId != null) {
            ivFavicon.setImageResource(item.iconResId);
            ivFavicon.setVisibility(View.VISIBLE);
            tvIcon.setVisibility(View.GONE);
        } else {
            tvIcon.setText(item.iconText);
            tvIcon.setVisibility(View.VISIBLE);
            ivFavicon.setVisibility(View.GONE);
        }
        String title = item.title;
        tvTitle.setText(title != null && title.length() > 15 ? title.substring(0, 15) + "…" : title);

        int text = Color.parseColor(item.active ? "#FFFFFF" : "#969696");
        tabView.setBackgroundColor(Color.parseColor(item.active ? "#1E1E1E" : "#2D2D2D"));
        tvTitle.setTextColor(text);
        tvIcon.setTextColor(text);

        if (!item.closeable) {
            tvClose.setVisibility(View.GONE);
        } else {
            tvClose.setVisibility(View.VISIBLE);
            tvClose.setOnClickListener(v -> {
                if (afterAction != null) afterAction.run();
                mListener.onTabCloseRequested(item.id);
            });
        }
        tabView.setOnClickListener(v -> {
            if (afterAction != null) afterAction.run();
            mListener.onTabSelected(item.id);
        });
    }

    /** Shows only the tabs that fit (active one always); the rest go to the overflow menu. */
    private void fitTabs() {
        int n = mStrip.getChildCount();
        int[] w = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            View c = mStrip.getChildAt(i);
            c.setVisibility(View.VISIBLE);
            c.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            w[i] = c.getMeasuredWidth();
            total += w[i];
        }
        int avail = ((View) mScroll.getParent()).getWidth() - mScroll.getLeft();
        boolean overflow = total > avail;
        if (overflow) avail -= (int) (56 * mContext.getResources().getDisplayMetrics().density);
        int used = 0;
        boolean[] show = new boolean[n];
        for (int i = 0; i < n && i < mState.items.size(); i++) {
            if (mState.items.get(i).active) { show[i] = true; used += w[i]; }
        }
        int hidden = 0;
        for (int i = 0; i < n; i++) {
            // A tab is shown when at least half of it fits; the strip stays scrollable for the rest.
            if (!show[i] && (!overflow || used + w[i] / 2 <= avail)) { show[i] = true; used += w[i]; }
        }
        for (int i = 0; i < n; i++) {
            mStrip.getChildAt(i).setVisibility(show[i] ? View.VISIBLE : View.GONE);
            if (!show[i]) hidden++;
        }
        mOverflowBtn.setText("(" + hidden + ") ⌃");
        mOverflowBtn.setVisibility(overflow && hidden > 0 ? View.VISIBLE : View.GONE);
    }
}
