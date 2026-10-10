package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;

import java.util.List;

public class TwoFingerSwipeLayout extends FrameLayout {

    private final int mTouchSlop;
    private boolean mIsTwoFingerSwiping = false;
    private float mInitialX0, mInitialY0, mInitialX1, mInitialY1;
    private float mCurrentX0, mCurrentX1;
    private float mTranslationX = 0f;

    private TabItem mCurrentTab;
    private TabItem mNextTab; // Tab to preview on the left or right during swipe

    private final Paint mOverlayPaint;

    public TwoFingerSwipeLayout(Context context) {
        this(context, null);
    }

    public TwoFingerSwipeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoFingerSwipeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);

        mOverlayPaint = new Paint();
        mOverlayPaint.setColor(Color.argb(40, 0, 0, 0)); // Subtle dimming overlay
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int pointerCount = ev.getPointerCount();
        int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerCount == 2) {
                    mInitialX0 = ev.getX(0);
                    mInitialY0 = ev.getY(0);
                    mInitialX1 = ev.getX(1);
                    mInitialY1 = ev.getY(1);
                    mCurrentX0 = mInitialX0;
                    mCurrentX1 = mInitialX1;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 2 && !mIsTwoFingerSwiping) {
                    float dx0 = ev.getX(0) - mInitialX0;
                    float dy0 = ev.getY(0) - mInitialY0;
                    float dx1 = ev.getX(1) - mInitialX1;
                    float dy1 = ev.getY(1) - mInitialY1;

                    // Both fingers must move horizontally in the same direction
                    boolean sameDirectionX = (dx0 * dx1 > 0);
                    boolean isHorizontal = Math.abs(dx0) > Math.abs(dy0) && Math.abs(dx1) > Math.abs(dy1);
                    float avgDx = (dx0 + dx1) / 2f;

                    if (sameDirectionX && isHorizontal && Math.abs(avgDx) > mTouchSlop * 1.5f) {
                        mIsTwoFingerSwiping = true;
                        prepareSwipePreview(avgDx);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsTwoFingerSwiping) {
                    finishSwipe();
                    return true;
                }
                break;
        }

        return mIsTwoFingerSwiping || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int pointerCount = ev.getPointerCount();
        int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (mIsTwoFingerSwiping) {
                    if (pointerCount >= 2) {
                        mCurrentX0 = ev.getX(0);
                        mCurrentX1 = ev.getX(1);
                    }
                    float avgInitialX = (mInitialX0 + mInitialX1) / 2f;
                    float avgCurrentX = (mCurrentX0 + mCurrentX1) / 2f;
                    float deltaX = avgCurrentX - avgInitialX;

                    updateSwipeTranslation(deltaX);
                    return true;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsTwoFingerSwiping) {
                    finishSwipe();
                    return true;
                }
                break;
        }

        return super.onTouchEvent(ev);
    }

    private void prepareSwipePreview(float dx) {
        TabManager tm = TabManager.getInstance();
        mCurrentTab = tm.getActiveTab();
        if (mCurrentTab == null) return;

        List<TabItem> tabs = tm.getTabs();
        int currentIndex = tabs.indexOf(mCurrentTab);
        if (currentIndex == -1) return;

        int targetIndex = (dx > 0) ? currentIndex - 1 : currentIndex + 1;
        if (targetIndex >= 0 && targetIndex < tabs.size()) {
            mNextTab = tabs.get(targetIndex);
            View nextView = (mNextTab.getFragment() != null) ? mNextTab.getFragment().getView() : null;
            if (nextView != null) {
                nextView.setVisibility(View.VISIBLE);
                nextView.bringToFront();
            }
        } else {
            mNextTab = null;
        }
    }

    private void updateSwipeTranslation(float deltaX) {
        TabManager tm = TabManager.getInstance();
        List<TabItem> tabs = tm.getTabs();
        if (mCurrentTab == null) return;

        int currentIndex = tabs.indexOf(mCurrentTab);
        int targetIndex = (deltaX > 0) ? currentIndex - 1 : currentIndex + 1;

        // Resistance effect when swiping out of bounds
        if (targetIndex < 0 || targetIndex >= tabs.size()) {
            deltaX *= 0.3f; // Rubber band resistance
        }

        mTranslationX = deltaX;

        View currentView = (mCurrentTab.getFragment() != null) ? mCurrentTab.getFragment().getView() : null;
        if (currentView != null) {
            currentView.setTranslationX(mTranslationX);
        }

        if (mNextTab != null) {
            View nextView = (mNextTab.getFragment() != null) ? mNextTab.getFragment().getView() : null;
            if (nextView != null) {
                float width = getWidth();
                if (width <= 0) width = 1000f;

                float startPos = (deltaX > 0) ? -width : width;
                nextView.setTranslationX(startPos + mTranslationX);
                nextView.setVisibility(View.VISIBLE);
            }
        }

        invalidate();
    }

    private void finishSwipe() {
        mIsTwoFingerSwiping = false;

        float width = getWidth();
        if (width <= 0) width = 1000f;

        TabManager tm = TabManager.getInstance();
        List<TabItem> tabs = tm.getTabs();
        int currentIndex = (mCurrentTab != null) ? tabs.indexOf(mCurrentTab) : -1;

        boolean isSwipeThresholdMet = Math.abs(mTranslationX) > width * 0.25f;
        int targetIndex = (mTranslationX > 0) ? currentIndex - 1 : currentIndex + 1;

        if (isSwipeThresholdMet && targetIndex >= 0 && targetIndex < tabs.size()) {
            final TabItem targetTab = tabs.get(targetIndex);
            final float targetTransX = (mTranslationX > 0) ? width : -width;

            View currentView = (mCurrentTab != null && mCurrentTab.getFragment() != null) ? mCurrentTab.getFragment().getView() : null;
            View nextView = (mNextTab != null && mNextTab.getFragment() != null) ? mNextTab.getFragment().getView() : null;

            if (currentView != null) {
                currentView.animate()
                        .translationX(targetTransX)
                        .setDuration(200)
                        .withEndAction(() -> {
                            currentView.setTranslationX(0f);
                            tm.selectTab(targetTab);
                        })
                        .start();
            } else {
                tm.selectTab(targetTab);
            }

            if (nextView != null) {
                nextView.animate()
                        .translationX(0f)
                        .setDuration(200)
                        .start();
            }
        } else {
            // Animate back to original position
            View currentView = (mCurrentTab != null && mCurrentTab.getFragment() != null) ? mCurrentTab.getFragment().getView() : null;
            View nextView = (mNextTab != null && mNextTab.getFragment() != null) ? mNextTab.getFragment().getView() : null;

            if (currentView != null) {
                currentView.animate()
                        .translationX(0f)
                        .setDuration(200)
                        .start();
            }

            if (nextView != null) {
                float startPos = (mTranslationX > 0) ? -width : width;
                nextView.animate()
                        .translationX(startPos)
                        .setDuration(200)
                        .withEndAction(() -> {
                            nextView.setTranslationX(0f);
                            nextView.setVisibility(View.GONE);
                        })
                        .start();
            }
        }

        mTranslationX = 0f;
        mCurrentTab = null;
        mNextTab = null;
    }
}
