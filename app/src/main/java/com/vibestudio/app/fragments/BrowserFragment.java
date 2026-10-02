package com.vibestudio.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.browser.BrowserManager;

import java.util.UUID;

public class BrowserFragment extends Fragment implements BrowserManager.UrlChangeListener {

    private static final String ARG_SESSION_ID = "arg_session_id";
    private static final String TAG = "BrowserFragment";

    private String mSessionId;
    private EditText mEditUrl;
    private Button mBtnGo;
    private ViewGroup mContainerView;
    private WebView mWebView;

    public static BrowserFragment newInstance(String sessionId) {
        BrowserFragment fragment = new BrowserFragment();
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
            mSessionId = "browser_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_browser, container, false);

        mEditUrl = root.findViewById(R.id.edit_url);
        mBtnGo = root.findViewById(R.id.btn_go);
        mContainerView = root.findViewById(R.id.web_view_container);

        Context context = getContext();
        if (context != null) {
            mWebView = BrowserManager.getInstance().getOrCreateWebView(mSessionId, context);
        }

        if (mWebView != null) {
            if (mWebView.getParent() != null) {
                ((ViewGroup) mWebView.getParent()).removeView(mWebView);
            }
            mContainerView.addView(mWebView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            String currentUrl = mWebView.getUrl();
            if (!TextUtils.isEmpty(currentUrl)) {
                mEditUrl.setText(currentUrl);
            }
        }

        BrowserManager.getInstance().setUrlChangeListener(this);
        setupListeners();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSessionId != null) {
            BrowserManager.getInstance().setActiveSessionId(mSessionId);
        }
    }

    private void setupListeners() {
        mBtnGo.setOnClickListener(v -> navigateToEnteredUrl());

        mEditUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateToEnteredUrl();
                return true;
            }
            return false;
        });
    }

    private void navigateToEnteredUrl() {
        String urlText = mEditUrl.getText() != null ? mEditUrl.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(urlText)) {
            BrowserManager.getInstance().navigate(mSessionId, urlText);
        }
    }

    @Override
    public void onUrlChanged(String sessionId, String newUrl) {
        if (mSessionId != null && mSessionId.equals(sessionId)) {
            if (getActivity() != null && mEditUrl != null) {
                getActivity().runOnUiThread(() -> {
                    if (mEditUrl != null && !mEditUrl.hasFocus()) {
                        mEditUrl.setText(newUrl);
                    }
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        BrowserManager.getInstance().setUrlChangeListener(null);
        if (mContainerView != null && mWebView != null) {
            mContainerView.removeView(mWebView);
        }
        super.onDestroyView();
    }

    public String getSessionId() {
        return mSessionId;
    }
}
