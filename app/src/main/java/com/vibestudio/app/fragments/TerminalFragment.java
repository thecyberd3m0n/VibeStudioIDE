package com.vibestudio.app.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.vibestudio.app.R;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

public class TerminalFragment extends Fragment {

    private static final String TAG = "TerminalFragment";
    private TerminalView mTerminalView;

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
            TerminalSessionManager.getInstance().ensureSessionStarted(context.getApplicationContext());
            attachToGlobalSession();
        }
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

    private void attachToGlobalSession() {
        TerminalSession globalSession = TerminalSessionManager.getInstance().getTerminalSession();
        if (mTerminalView != null && globalSession != null && globalSession.isRunning()) {
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
                public void logWarn(String tag, String message) { LogViewerService.getInstance().w(tag, message); }

                @Override
                public void logError(String tag, String message) { LogViewerService.getInstance().e(tag, message); }

                @Override
                public void logStackTraceWithMessage(String tag, String message, Exception e) { LogViewerService.getInstance().e(tag, message, e); }

                @Override
                public void logStackTrace(String tag, Exception e) { LogViewerService.getInstance().e(tag, "TerminalView error", e); }
            });

            mTerminalView.attachSession(globalSession);
            TerminalSessionManager.getInstance().setTerminalView(mTerminalView);
            showSoftKeyboard();
            LogViewerService.getInstance().i(TAG, "Attached TerminalView to existing global TerminalSession");
        } else {
            LogViewerService.getInstance().w(TAG, "Unable to attach TerminalView: global session is not running");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        TerminalSessionManager.getInstance().setTerminalView(null);
        mTerminalView = null;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }
}
