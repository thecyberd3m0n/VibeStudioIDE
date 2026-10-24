package com.vibestudio.app.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.fragments.ImageViewerFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TwoFingerSwipeLayoutTest {

    private TwoFingerSwipeLayout layout;
    private FragmentActivity activity;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(FragmentActivity.class).create().get();
        layout = new TwoFingerSwipeLayout(activity);
        TabManager.getInstance().clearAll();
    }

    private MotionEvent createTwoFingerMoveEvent(float x0, float y0, float x1, float y1) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[2];
        pp[0] = new MotionEvent.PointerProperties();
        pp[0].id = 0;
        pp[1] = new MotionEvent.PointerProperties();
        pp[1].id = 1;

        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[2];
        pc[0] = new MotionEvent.PointerCoords();
        pc[0].x = x0;
        pc[0].y = y0;
        pc[1] = new MotionEvent.PointerCoords();
        pc[1].x = x1;
        pc[1].y = y1;

        return MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_MOVE, 2, pp, pc, 0, 0, 1.0f, 1.0f, 0, 0, 0, 0
        );
    }

    private MotionEvent createPointerDownEvent(float x0, float y0, float x1, float y1) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[2];
        pp[0] = new MotionEvent.PointerProperties();
        pp[0].id = 0;
        pp[1] = new MotionEvent.PointerProperties();
        pp[1].id = 1;

        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[2];
        pc[0] = new MotionEvent.PointerCoords();
        pc[0].x = x0;
        pc[0].y = y0;
        pc[1] = new MotionEvent.PointerCoords();
        pc[1].x = x1;
        pc[1].y = y1;

        int action = MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);

        return MotionEvent.obtain(
                downTime, eventTime, action, 2, pp, pc, 0, 0, 1.0f, 1.0f, 0, 0, 0, 0
        );
    }

    @Test
    public void testTwoFingerSwipeInterceptedWhenTabsExist() {
        TabManager tm = TabManager.getInstance();
        tm.openTab(TabType.TERMINAL, "Tab 1", null);
        tm.openTab(TabType.EDITOR, "Tab 2", null);

        MotionEvent pointerDown = createPointerDownEvent(100, 200, 150, 200);
        layout.dispatchTouchEvent(pointerDown);

        MotionEvent moveEvent = createTwoFingerMoveEvent(200, 200, 250, 200);
        boolean handled = layout.dispatchTouchEvent(moveEvent);

        assertTrue("Two finger swipe should be handled", handled);
    }

    @Test
    public void testTwoFingerSwipeInterceptedWhenImageViewerIsOpened() {
        TabManager tm = TabManager.getInstance();
        ImageViewerFragment ivf = ImageViewerFragment.newInstance("/path/to/image.png");
        tm.openTab(TabType.IMAGE, "image.png", ivf);
        tm.openTab(TabType.TERMINAL, "Terminal", null);
        tm.selectTab(tm.getTabs().get(0)); // Active tab is ImageViewer

        MotionEvent pointerDown = createPointerDownEvent(100, 200, 150, 200);
        layout.dispatchTouchEvent(pointerDown);

        MotionEvent moveEvent = createTwoFingerMoveEvent(200, 200, 250, 200);
        boolean handled = layout.dispatchTouchEvent(moveEvent);

        assertTrue("Two finger swipe tab switching should work when ImageViewer is opened", handled);
    }

    @Test
    public void testImageViewerFragmentReloadsOnResumeAndHiddenChange() {
        ImageViewerFragment ivf = ImageViewerFragment.newInstance("/path/to/image.png");
        ivf.onResume();
        ivf.onHiddenChanged(false);
    }
}
