package com.vibestudio.app.chat.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ToolCallParsingTest {

    @Test
    public void findsEveryCallInOrderAroundProse() throws Exception {
        String reply = "First I look around.\n"
                + "{\"tool\":\"file_list_directory\",\"args\":{\"path\":\"a\"}}\n"
                + "{\"planned_delay_ms\":0,\"tool\":\"editor_read_file\",\"args\":{\"path\":\"b\"}}";

        List<JSONObject> calls = ToolExecutionManager.parseToolCalls(reply);

        assertEquals(2, calls.size());
        assertEquals("file_list_directory", calls.get(0).getString("tool"));
        assertEquals("editor_read_file", calls.get(1).getString("tool"));
    }

    @Test
    public void bracesInsideStringArgumentsDoNotSplitACall() throws Exception {
        String reply = "{\"tool\":\"editor_search_replace\",\"args\":{\"old\":\"if (x) { y(); }\",\"new\":\"}\"}}";

        List<JSONObject> calls = ToolExecutionManager.parseToolCalls(reply);

        assertEquals(1, calls.size());
        assertEquals("if (x) { y(); }", calls.get(0).getJSONObject("args").getString("old"));
    }

    @Test
    public void jsonWithoutAToolKeyIsNotACall() {
        assertTrue(ToolExecutionManager.parseToolCalls("{\"result\":\"ok\"} and {\"tool\":\"\"}").isEmpty());
        assertTrue(ToolExecutionManager.parseToolCalls("plain text").isEmpty());
        assertTrue(ToolExecutionManager.parseToolCalls(null).isEmpty());
    }

    @Test
    public void recoversTheMalformedCallSyntax() throws Exception {
        List<JSONObject> calls = ToolExecutionManager.parseToolCalls("call:file_list_directory{\"path\":\"x\"}");

        assertEquals(1, calls.size());
        assertEquals("file_list_directory", calls.get(0).getString("tool"));
        assertEquals("x", calls.get(0).getJSONObject("args").getString("path"));
    }
}
