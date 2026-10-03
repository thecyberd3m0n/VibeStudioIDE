package com.vibestudio.app.fragments;

import android.content.Context;
import android.graphics.Bitmap;
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
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;

public class BrowserFragment extends Fragment implements BrowserManager.BrowserStateListener {

    private static final String TAG = "BrowserFragment";

    private EditText mEditUrl;
    private Button mBtnGo;
    private ViewGroup mContainerView;
    private WebView mWebView;

    public static BrowserFragment newInstance() {
        return new BrowserFragment();
    }

    public static BrowserFragment newInstance(WebView webView) {
        BrowserFragment fragment = new BrowserFragment();
        fragment.mWebView = webView;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_browser, container, false);

        mEditUrl = root.findViewById(R.id.edit_url);
        mBtnGo = root.findViewById(R.id.btn_go);
        mContainerView = root.findViewById(R.id.web_view_container);

        Context context = getContext();
        if (mWebView == null && context != null) {
            mWebView = BrowserManager.getInstance().createWebView(context);
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
            BrowserManager.getInstance().setStateListener(mWebView, this);
        }

        setupListeners();

        return root;
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
        if (!TextUtils.isEmpty(urlText) && mWebView != null) {
            BrowserManager.getInstance().navigate(mWebView, urlText);
        }
    }

    @Override
    public void onUrlChanged(WebView view, String newUrl) {
        if (mWebView != null && mWebView == view) {
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
    public void onTitleChanged(WebView view, String title) {
        if (mWebView != null && mWebView == view) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    TabItem tab = TabManager.getInstance().findTabByFragment(BrowserFragment.this);
                    if (tab != null) {
                        tab.setTitle(title);
                        TabManager.getInstance().notifyTabUpdated(tab);
                    }
                });
            }
        }
    }

    @Override
    public void onFaviconChanged(WebView view, Bitmap favicon) {
        if (mWebView != null && mWebView == view) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    TabItem tab = TabManager.getInstance().findTabByFragment(BrowserFragment.this);
                    if (tab != null) {
                        tab.setFavicon(favicon);
                        TabManager.getInstance().notifyTabUpdated(tab);
                    }
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (mWebView != null) {
            BrowserManager.getInstance().setStateListener(mWebView, null);
        }
        if (mContainerView != null && mWebView != null) {
            mContainerView.removeView(mWebView);
        }
        super.onDestroyView();
    }

    public WebView getWebView() {
        return mWebView;
    }
}
