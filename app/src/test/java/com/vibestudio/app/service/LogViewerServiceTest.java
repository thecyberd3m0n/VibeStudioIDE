package com.vibestudio.app.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LogViewerServiceTest {

    private LogViewerService mService;

    @Before
    public void setUp() {
        mService = LogViewerService.getInstance();
        mService.clear();
    }

    @Test
    public void testLogCacheSizeCap() {
        // Append lines until total exceeds 10 KB (10,240 bytes)
        String thousandCharLine = new String(new char[1000]).replace('\0', 'A');
        for (int i = 0; i < 20; i++) {
            mService.i("TestTag", thousandCharLine);
        }

        String allLogs = mService.getAllLogs();
        int totalBytes = allLogs.getBytes().length;

        assertTrue("Log buffer bytes (" + totalBytes + ") should be <= 10240 bytes", totalBytes <= 10240);
    }

    @Test
    public void testClearLogs() {
        mService.i("TestTag", "Hello World");
        assertTrue(mService.getAllLogs().contains("Hello World"));

        mService.clear();
        assertEquals("", mService.getAllLogs());
    }
}
