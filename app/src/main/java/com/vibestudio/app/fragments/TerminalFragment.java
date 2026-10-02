package com.vibestudio.app.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.vibestudio.app.R;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

import java.util.UUID;

public class TerminalFragment extends Fragment {

    private static final String ARG_SESSION_ID = "arg_session_id";
    private static final String TAG = "TerminalFragment";

    private String mSessionId;
    private TerminalView mTerminalView;

    public static TerminalFragment newInstance(String sessionId) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mSessionId = getArguments().getString(ARG_SESSION_ID);
        }
        if (mSessionId == null) {
            mSessionId = "term_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        mTerminalView = view.findViewById(R.id.terminal_view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = getContext();
        if (context != null) {
            TerminalSessionManager tsm = TerminalSessionManager.getInstance();
            tsm.createSession(context.getApplicationContext(), mSessionId);
            attachToSession();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSessionId != null) {
            TerminalSessionManager.getInstance().setActiveSessionId(mSessionId);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        hideSoftKeyboard();
    }

    private void showSoftKeyboard() {
        if (mTerminalView != null) {
            mTerminalView.requestFocus();
            mTerminalView.post(() -> {
                Context ctx = getContext();
                if (ctx != null) {
                    InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(mTerminalView, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }
    }

    public void hideSoftKeyboard() {
        Context ctx = getContext();
        View focus = (mTerminalView != null) ? mTerminalView : (getActivity() != null ? getActivity().getCurrentFocus() : null);
        if (ctx != null && focus != null) {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    private void attachToSession() {
        TerminalSessionManager tsm = TerminalSessionManager.getInstance();
        TerminalSession session = tsm.getTerminalSession(mSessionId);

        if (mTerminalView != null && session != null && session.isRunning()) {
            mTerminalView.setBackgroundColor(Color.parseColor("#1E1E2E"));
            mTerminalView.setTerminalViewClient(new TerminalViewClient() {
                @Override
                public float onScale(float scale) { return 1.0f; }

                @Override
                public void onSingleTapUp(MotionEvent e) {
                    showSoftKeyboard();
                }

                @Override
                public boolean shouldBackButtonBeMappedToEscape() { return false; }

                @Override
                public boolean shouldEnforceCharBasedInput() { return true; }

                @Override
                public boolean shouldUseCtrlSpaceWorkaround() { return false; }

                @Override
                public boolean isTerminalViewSelected() { return true; }

                @Override
                public void copyModeChanged(boolean copyMode) {}

                @Override
                public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }

                @Override
                public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }

                @Override
                public boolean onLongPress(MotionEvent event) { return false; }

                @Override
                public boolean readControlKey() { return false; }

                @Override
                public boolean readAltKey() { return false; }

                @Override
                public boolean readShiftKey() { return false; }

                @Override
                public boolean readFnKey() { return false; }

                @Override
                public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }

                @Override
                public void onEmulatorSet() {}

                @Override
                public void logVerbose(String tag, String message) { LogViewerService.getInstance().d(tag, message); }

                @Override
                public void logDebug(String tag, String message) { LogViewerService.getInstance().d(tag, message); }

                @Override
                public void logInfo(String tag, String message) { LogViewerService.getInstance().i(tag, message); }

                @Override
                public void logWarn(String tag, String message) { LogViewerService.getInstance().w(TAG, message); }

                @Override
                public void logError(String tag, String message) { LogViewerService.getInstance().e(TAG, message); }

                @Override
                public void logStackTraceWithMessage(String tag, String message, Exception e) { LogViewerService.getInstance().e(TAG, message, e); }

                @Override
                public void logStackTrace(String tag, Exception e) { LogViewerService.getInstance().e(TAG, "TerminalView error", e); }
            });

            mTerminalView.attachSession(session);
            tsm.registerTerminalView(mSessionId, mTerminalView);
            showSoftKeyboard();
            LogViewerService.getInstance().i(TAG, "Attached TerminalView to session: " + mSessionId);
        } else {
            LogViewerService.getInstance().w(TAG, "Unable to attach TerminalView: session [" + mSessionId + "] is not running");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hideSoftKeyboard();
        TerminalSessionManager.getInstance().unregisterTerminalView(mSessionId);
        mTerminalView = null;
    }

    public String getSessionId() {
        return mSessionId;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }
}
