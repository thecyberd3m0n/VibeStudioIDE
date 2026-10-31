package com.vibestudio.app.view;

import android.content.Context;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Tap toggles one line. A long-press toggles the line and then drags: every line the finger passes over gets
 * the same state as the first one, and the list scrolls when the finger nears its top or bottom edge.
 * A plain swipe is left to the RecyclerView, so scrolling keeps working.
 */
public class LogSelectionTouchListener implements RecyclerView.OnItemTouchListener {
    private static final int EDGE_DP = 56;
    private static final int SCROLL_STEP_DP = 10;

    private final RecyclerView mRecyclerView;
    private final LogLineAdapter mAdapter;
    private final GestureDetector mDetector;
    private final int mEdgePx;
    private final int mStepPx;

    private boolean mDragging;
    private boolean mDragSelects;
    private int mLastPosition = RecyclerView.NO_POSITION;
    private float mLastY;

    private final Runnable mAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (!mDragging) return;
            int height = mRecyclerView.getHeight();
            if (mLastY < mEdgePx) {
                mRecyclerView.scrollBy(0, -mStepPx);
            } else if (mLastY > height - mEdgePx) {
                mRecyclerView.scrollBy(0, mStepPx);
            } else {
                return;
            }
            applyDragAt(mLastY);
            mRecyclerView.postOnAnimation(this);
        }
    };

    public LogSelectionTouchListener(Context context, RecyclerView recyclerView, LogLineAdapter adapter) {
        mRecyclerView = recyclerView;
        mAdapter = adapter;
        float density = context.getResources().getDisplayMetrics().density;
        mEdgePx = (int) (EDGE_DP * density);
        mStepPx = (int) (SCROLL_STEP_DP * density);
        mDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                int position = positionAt(e.getY());
                if (position != RecyclerView.NO_POSITION) mAdapter.toggle(position);
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                int position = positionAt(e.getY());
                if (position == RecyclerView.NO_POSITION) return;
                mDragging = true;
                mDragSelects = mAdapter.toggle(position);
                mLastPosition = position;
                mLastY = e.getY();
                mRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                mRecyclerView.getParent().requestDisallowInterceptTouchEvent(true);
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        mDetector.onTouchEvent(e);
        int action = e.getActionMasked();
        if (!mDragging && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            return false;
        }
        return mDragging;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                mLastY = e.getY();
                applyDragAt(mLastY);
                mRecyclerView.removeCallbacks(mAutoScroll);
                mRecyclerView.postOnAnimation(mAutoScroll);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endDrag();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

    private void endDrag() {
        mDragging = false;
        mLastPosition = RecyclerView.NO_POSITION;
        mRecyclerView.removeCallbacks(mAutoScroll);
    }

    /** Applies the drag state to every line between the previous and the current finger position. */
    private void applyDragAt(float y) {
        int position = positionAt(y);
        if (position == RecyclerView.NO_POSITION || mLastPosition == RecyclerView.NO_POSITION) return;
        int from = Math.min(mLastPosition, position);
        int to = Math.max(mLastPosition, position);
        for (int i = from; i <= to; i++) mAdapter.setSelected(i, mDragSelects);
        mLastPosition = position;
    }

    /** The line under y; above or below the list it is the first or last visible line, so a drag can run past the edge. */
    private int positionAt(float y) {
        View child = mRecyclerView.findChildViewUnder(mRecyclerView.getWidth() / 2f, y);
        if (child != null) return mRecyclerView.getChildAdapterPosition(child);
        RecyclerView.LayoutManager manager = mRecyclerView.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return RecyclerView.NO_POSITION;
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        if (y < 0) return linear.findFirstVisibleItemPosition();
        if (y > mRecyclerView.getHeight()) return linear.findLastVisibleItemPosition();
        return RecyclerView.NO_POSITION;
    }
}
