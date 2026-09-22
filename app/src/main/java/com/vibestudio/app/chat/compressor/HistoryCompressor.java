package com.vibestudio.app.chat.compressor;

import com.vibestudio.app.chat.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class HistoryCompressor {

    public static final int TOKEN_THRESHOLD = 100000; // 100k tokens threshold from gemini.sh

    public static int estimateTokenCount(List<ChatMessage> history) {
        if (history == null) return 0;
        int charCount = 0;
        for (ChatMessage msg : history) {
            if (msg.getText() != null) {
                charCount += msg.getText().length();
            }
        }
        return charCount / 4; // ~4 characters per token heuristic
    }

    public static List<ChatMessage> compressIfNeeded(List<ChatMessage> history) {
        if (history == null || history.size() <= 6) {
            return history != null ? new ArrayList<>(history) : new ArrayList<ChatMessage>();
        }

        int estimatedTokens = estimateTokenCount(history);
        if (estimatedTokens < TOKEN_THRESHOLD) {
            return new ArrayList<>(history);
        }

        // Keep system/welcome message at index 0, summarize middle, keep last 4 messages
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(history.get(0));

        int midEndIndex = history.size() - 4;
        StringBuilder summary = new StringBuilder("[Context Summary of previous conversation steps]:\n");
        for (int i = 1; i < midEndIndex; i++) {
            ChatMessage msg = history.get(i);
            String text = msg.getText();
            if (text != null && text.length() > 200) {
                text = text.substring(0, 200) + "...";
            }
            summary.append("- ").append(msg.getSender()).append(": ").append(text).append("\n");
        }

        compressed.add(new ChatMessage("System Tool", summary.toString(), false, ChatMessage.MessageType.TOOL_RESULT));

        for (int i = midEndIndex; i < history.size(); i++) {
            compressed.add(history.get(i));
        }

        return compressed;
    }
}
