package com.vibestudio.app.chat.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatSessionManager {

    private static ChatSessionManager sInstance;

    private final Map<String, ChatSession> mSessions = new HashMap<>();

    private ChatSessionManager() {}

    public static synchronized ChatSessionManager getInstance() {
        if (sInstance == null) {
            sInstance = new ChatSessionManager();
        }
        return sInstance;
    }

    public synchronized ChatSession createSession() {
        ChatSession session = new ChatSession();
        mSessions.put(session.getId(), session);
        return session;
    }

    public synchronized ChatSession getSession(String id) {
        return mSessions.get(id);
    }

    public synchronized List<ChatSession> getSessions() {
        return new ArrayList<>(mSessions.values());
    }

    public synchronized void closeSession(String id) {
        ChatSession session = mSessions.remove(id);
        if (session != null && session.isLoading()) {
            session.stopAgentExecution();
        }
    }
}
