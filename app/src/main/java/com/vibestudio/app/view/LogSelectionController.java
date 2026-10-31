package com.vibestudio.app.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/**
 * Line selection for the log list: shows the floating Copy / Select all toolbar (the one used for
 * selecting text elsewhere in Android) while at least one line is selected.
 */
public class LogSelectionController extends ActionMode.Callback2 implements LogLineAdapter.SelectionListener {
    private static final int MENU_COPY = 1;
    private static final int MENU_SELECT_ALL = 2;

    private final RecyclerView mRecyclerView;
    private final LogLineAdapter mAdapter;
    private ActionMode mActionMode;

    public LogSelectionController(Context context, RecyclerView recyclerView, LogLineAdapter adapter) {
        mRecyclerView = recyclerView;
        mAdapter = adapter;
        mAdapter.setSelectionListener(this);
        mRecyclerView.addOnItemTouchListener(new LogSelectionTouchListener(context, recyclerView, adapter));
        if (mRecyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (mActionMode != null) mActionMode.invalidateContentRect();
            }
        });
    }

    /** Drops the selection and hides the toolbar. */
    public void finish() {
        if (mActionMode != null) mActionMode.finish();
        mAdapter.clearSelection();
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        if (selectedCount == 0) {
            if (mActionMode != null) mActionMode.finish();
            return;
        }
        if (mActionMode == null) {
            mActionMode = mRecyclerView.startActionMode(this, ActionMode.TYPE_FLOATING);
        } else {
            mActionMode.invalidateContentRect();
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        menu.add(Menu.NONE, MENU_COPY, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_SELECT_ALL, 1, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        Context context = mRecyclerView.getContext();
        switch (item.getItemId()) {
            case MENU_COPY:
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("VibeStudio logs", mAdapter.getSelectedText()));
                    Toast.makeText(context, "Copied " + mAdapter.getSelectedCount() + " line(s)", Toast.LENGTH_SHORT).show();
                }
                finish();
                return true;
            case MENU_SELECT_ALL:
                mAdapter.selectAll();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        mAdapter.clearSelection();
    }

    /** The toolbar floats above the first selected line, or the top of the list while that line is scrolled away. */
    @Override
    public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
        View child = null;
        int first = mAdapter.getFirstSelected();
        if (first >= 0 && mRecyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            child = mRecyclerView.getLayoutManager().findViewByPosition(first);
        }
        if (child != null) {
            outRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        } else {
            outRect.set(0, 0, mRecyclerView.getWidth(), 1);
        }
    }
}
