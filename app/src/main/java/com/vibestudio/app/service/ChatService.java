package com.vibestudio.app.service;

import android.content.Context;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.chat.session.ChatSessionManager;
import com.vibestudio.app.mcp.McpClientManager;

import java.util.List;

/**
 * Legacy compatibility wrapper for ChatService.
 * Delegates to the active default ChatSession via ChatSessionManager.
 */
public class ChatService {

    private static ChatService sInstance;

    public interface OnChatMessageListener {
        void onMessageAdded(ChatMessage message);
        void onResponseLoading(boolean isLoading);
    }

    private ChatService() {}

    public static synchronized ChatService getInstance() {
        if (sInstance == null) {
            sInstance = new ChatService();
        }
        return sInstance;
    }

    private ChatSession getDefaultSession() {
        List<ChatSession> sessions = ChatSessionManager.getInstance().getSessions();
        if (!sessions.isEmpty()) {
            return sessions.get(0);
        }
        return ChatSessionManager.getInstance().createSession();
    }

    public List<ChatMessage> getMessages() {
        return getDefaultSession().getMessages();
    }

    public boolean isLoading() {
        return getDefaultSession().isLoading();
    }

    public boolean isStopRequested() {
        return getDefaultSession().isStopRequested();
    }

    public void stopAgentExecution() {
        getDefaultSession().stopAgentExecution();
    }

    public void addListener(final OnChatMessageListener listener) {
        if (listener == null) return;
        getDefaultSession().addListener(new ChatSession.OnChatMessageListener() {
            @Override
            public void onMessageAdded(ChatMessage message) {
                listener.onMessageAdded(message);
            }

            @Override
            public void onResponseLoading(boolean isLoading) {
                listener.onResponseLoading(isLoading);
            }
        });
    }

    public void removeListener(OnChatMessageListener listener) {
        // No-op or handle if needed
    }

    public McpClientManager getMcpClientManager() {
        return getDefaultSession().getMcpClientManager();
    }

    public void sendMessage(Context context, String userText) {
        getDefaultSession().sendMessage(context, userText);
    }
}
