package com.vibestudio.app.mcp;

import android.content.Context;
import android.webkit.WebView;

import com.termux.terminal.TerminalSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ToolSessionManager {

    private static ToolSessionManager sInstance;
    private final Random mRandom = new Random();

    public static class ToolSession {
        public final int id;
        public final ToolType toolType;
        public final Object sessionHandle;

        public ToolSession(int id, ToolType toolType, Object sessionHandle) {
            this.id = id;
            this.toolType = toolType;
            this.sessionHandle = sessionHandle;
        }

        public WebView getWebView() {
            return sessionHandle instanceof WebView ? (WebView) sessionHandle : null;
        }

        public TerminalSession getTerminalSession() {
            return sessionHandle instanceof TerminalSession ? (TerminalSession) sessionHandle : null;
        }
    }

    public static class SessionValidationResult {
        public final ToolSession session;
        public final String errorMessage;

        public SessionValidationResult(ToolSession session, String errorMessage) {
            this.session = session;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return session != null && errorMessage == null;
        }
    }

    // Provider map for tools
    private final Map<ToolType, ToolSessionProvider> mProviders = new HashMap<>();

    // Global registry mapping integer session ID -> ToolSession (Agent-created sessions ONLY)
    private final Map<Integer, ToolSession> mSessionsById = new HashMap<>();

    // Global map linking ToolType -> active Agent integer session ID
    private final Map<ToolType, Integer> mActiveSessionByToolType = new HashMap<>();

    private ToolSessionManager() {
        registerProvider(new BrowserSessionProvider());
        registerProvider(new TerminalSessionProvider());
        registerProvider(new FileSessionProvider());
        registerProvider(new EditorSessionProvider());
    }

    public static synchronized ToolSessionManager getInstance() {
        if (sInstance == null) {
            sInstance = new ToolSessionManager();
        }
        return sInstance;
    }

    public void registerProvider(ToolSessionProvider provider) {
        if (provider != null && provider.getToolType() != null) {
            mProviders.put(provider.getToolType(), provider);
        }
    }

    public synchronized int getOrCreateSession(ToolType toolType, Context context, boolean forceNew) {
        if (!forceNew) {
            Integer existingId = mActiveSessionByToolType.get(toolType);
            if (existingId != null) {
                ToolSession session = mSessionsById.get(existingId);
                if (session != null && isSessionAlive(session)) {
                    return existingId;
                }
            }
        }

        ToolSessionProvider provider = mProviders.get(toolType);
        if (provider == null) {
            throw new IllegalStateException("No provider registered for ToolType: " + toolType);
        }

        Object handle = provider.createSessionHandle(context);

        int newId = 1000 + mRandom.nextInt(9000);
        ToolSession newSession = new ToolSession(newId, toolType, handle);
        mSessionsById.put(newId, newSession);
        mActiveSessionByToolType.put(toolType, newId);

        provider.openUiTab(context, newId, handle);

        return newId;
    }

    public synchronized SessionValidationResult validateSession(int sessionId, ToolType expectedToolType) {
        if (sessionId <= 0) {
            return new SessionValidationResult(null, "Invalid session ID: " + sessionId);
        }

        ToolSession session = mSessionsById.get(sessionId);
        if (session == null) {
            return new SessionValidationResult(null, "Session #" + sessionId + " does not exist or has been closed.");
        }

        if (expectedToolType != null && session.toolType != expectedToolType) {
            return new SessionValidationResult(null, "Session #" + sessionId + " is a " + session.toolType + " session, not a " + expectedToolType + " session.");
        }

        if (!isSessionAlive(session)) {
            mSessionsById.remove(sessionId);
            if (mActiveSessionByToolType.get(session.toolType) != null && mActiveSessionByToolType.get(session.toolType) == sessionId) {
                mActiveSessionByToolType.remove(session.toolType);
            }
            return new SessionValidationResult(null, "Session #" + sessionId + " is no longer alive.");
        }

        return new SessionValidationResult(session, null);
    }

    public synchronized ToolSession getSession(int sessionId) {
        return mSessionsById.get(sessionId);
    }

    public synchronized boolean isSessionAlive(ToolSession session) {
        if (session == null) return false;
        ToolSessionProvider provider = mProviders.get(session.toolType);
        return provider != null && provider.isSessionAlive(session.sessionHandle);
    }
}
