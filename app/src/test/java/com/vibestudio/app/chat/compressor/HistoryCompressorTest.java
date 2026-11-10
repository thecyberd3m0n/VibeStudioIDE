package com.vibestudio.app.chat.compressor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.vibestudio.app.chat.model.ChatMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HistoryCompressorTest {

    private static String huge() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500_000; i++) sb.append('x');
        return sb.toString();
    }

    private static ChatMessage call(String id) {
        return new ChatMessage("Model", "{\"tool\":\"t\",\"args\":{}}", false, ChatMessage.MessageType.TOOL_CALL, "t", null)
                .withToolLink(id, "turn", null, null);
    }

    private static ChatMessage result(String id) {
        return new ChatMessage("System Tool", "r", true, ChatMessage.MessageType.TOOL_RESULT, "t", null)
                .withToolLink(id, "turn", null, null);
    }

    @Test
    public void theKeptPartNeverStartsWithAResultWhoseCallWasSummarized() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("Model", "welcome", false));
        history.add(new ChatMessage("You", huge(), true));
        history.add(call("c1"));
        history.add(result("c1"));
        history.add(call("c2"));
        history.add(result("c2"));   // size - 4 lands here: the cut must move back to its call
        history.add(call("c3"));
        history.add(result("c3"));
        history.add(new ChatMessage("Model", "answer", false));

        List<ChatMessage> compressed = HistoryCompressor.compressIfNeeded(history);

        ChatMessage firstKept = compressed.get(2);
        assertEquals(ChatMessage.MessageType.TOOL_CALL, firstKept.getType());
        assertEquals("c2", firstKept.getCallId());
        assertNotNull(compressed.get(1).getText());
        assertTrue(compressed.get(1).getText().startsWith("[Context Summary"));
    }
}
