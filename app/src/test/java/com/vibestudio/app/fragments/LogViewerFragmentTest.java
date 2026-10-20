package com.vibestudio.app.fragments;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml")
public class LogViewerFragmentTest {

    private FragmentActivity mActivity;
    private LogViewerFragment mFragment;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        mFragment = new LogViewerFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, mFragment)
                .commitNow();
    }

    @Test
    public void testProcessListRendersManagedCards() {
        View view = mFragment.getView();
        assertNotNull(view);

        View containerList = view.findViewById(R.id.container_process_list);
        View containerDetail = view.findViewById(R.id.container_log_detail);

        assertEquals(View.VISIBLE, containerList.getVisibility());
        assertEquals(View.GONE, containerDetail.getVisibility());

        LinearLayout processContainer = view.findViewById(R.id.ll_process_container);
        assertNotNull(processContainer);
        // Expecting 2 managed process cards (Global Logcat, Git Service)
        assertEquals(2, processContainer.getChildCount());
    }

    @Test
    public void testCardClickOpensDetailView() {
        View view = mFragment.getView();
        assertNotNull(view);

        LinearLayout processContainer = view.findViewById(R.id.ll_process_container);
        View globalCard = processContainer.getChildAt(0);
        assertNotNull(globalCard);

        globalCard.performClick();

        View containerList = view.findViewById(R.id.container_process_list);
        View containerDetail = view.findViewById(R.id.container_log_detail);

        assertEquals(View.GONE, containerList.getVisibility());
        assertEquals(View.VISIBLE, containerDetail.getVisibility());

        TextView titleView = view.findViewById(R.id.tv_log_detail_title);
        assertNotNull(titleView);
        assertEquals("Global Logcat (~10KB Max)", titleView.getText().toString());

        // Click Back
        View btnBack = view.findViewById(R.id.btn_back_to_processes);
        assertNotNull(btnBack);
        btnBack.performClick();

        assertEquals(View.VISIBLE, containerList.getVisibility());
        assertEquals(View.GONE, containerDetail.getVisibility());
    }
}
