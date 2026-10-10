package com.vibestudio.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.vibestudio.app.editor.ThemeManager;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.terminal.TerminalSessionManager;

public class TerminalFragment extends Fragment {

    private static final String TAG = "TerminalFragment";

    private TerminalSession mTerminalSession;
    private TerminalView mTerminalView;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Runnable mTitleUpdateRunnable;

    public static TerminalFragment newInstance() {
        return new TerminalFragment();
    }

    public static TerminalFragment newInstance(TerminalSession session) {
        TerminalFragment fragment = new TerminalFragment();
        fragment.mTerminalSession = session;
        return fragment;
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
            if (mTerminalSession == null) {
                mTerminalSession = tsm.createSession(context.getApplicationContext());
            }
            attachToSession();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startTitlePolling();
    }

    @Override
    public void onPause() {
        super.onPause();
        hideSoftKeyboard();
        stopTitlePolling();
    }

    private void startTitlePolling() {
        if (mTitleUpdateRunnable == null) {
            mTitleUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    updateTerminalTitle();
                    mMainHandler.postDelayed(this, 1000);
                }
            };
        }
        mMainHandler.post(mTitleUpdateRunnable);
    }

    private void stopTitlePolling() {
        if (mTitleUpdateRunnable != null) {
            mMainHandler.removeCallbacks(mTitleUpdateRunnable);
        }
    }

    private void updateTerminalTitle() {
        if (mTerminalSession == null) return;
        String title = mTerminalSession.getTitle();
        if (title == null || title.isEmpty()) {
            title = TerminalSessionManager.getInstance().getForegroundProcessName(mTerminalSession);
        }
        TabItem tab = TabManager.getInstance().findTabByFragment(TerminalFragment.this);
        if (tab != null && !title.equals(tab.getTitle())) {
            tab.setTitle(title);
            TabManager.getInstance().notifyTabUpdated(tab);
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
        if (mTerminalSession == null) return;
        TerminalSessionManager tsm = TerminalSessionManager.getInstance();

        if (mTerminalView != null && mTerminalSession.isRunning()) {
            mTerminalView.setBackgroundColor(ThemeManager.getInstance().getPrimaryBackgroundColor());
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
                public void logStackTrace(String tag, Exception e) { LogViewerService.getInstance().e(TAG, "Terminal error", e); }
            });

            mTerminalView.attachSession(mTerminalSession);
            tsm.registerTerminalView(mTerminalSession, mTerminalView);
            showSoftKeyboard();
            LogViewerService.getInstance().i(TAG, "Attached TerminalView to session");
        } else {
            LogViewerService.getInstance().w(TAG, "Unable to attach TerminalView: session is not running");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hideSoftKeyboard();
        if (mTerminalSession != null) {
            TerminalSessionManager.getInstance().unregisterTerminalView(mTerminalSession);
        }
        mTerminalView = null;
    }

    public TerminalSession getTerminalSession() {
        return mTerminalSession;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }
}
