package com.vibestudio.app.fragments;

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

public class BrowserFragment extends Fragment implements BrowserManager.UrlChangeListener {

    private static final String TAG = "BrowserFragment";

    private EditText mEditUrl;
    private Button mBtnGo;
    private ViewGroup mContainerView;
    private WebView mWebView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_browser, container, false);

        mEditUrl = root.findViewById(R.id.edit_url);
        mBtnGo = root.findViewById(R.id.btn_go);
        mContainerView = root.findViewById(R.id.web_view_container);

        // Ensure global WebView is initialized in BrowserManager
        if (getContext() != null) {
            BrowserManager.getInstance().ensureInitialized(getContext());
        }

        mWebView = BrowserManager.getInstance().getWebView();
        if (mWebView != null) {
            // Detach from previous parent if attached
            if (mWebView.getParent() != null) {
                ((ViewGroup) mWebView.getParent()).removeView(mWebView);
            }
            // Attach active global WebView to fragment container
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
            BrowserManager.getInstance().navigate(urlText);
        }
    }

    @Override
    public void onUrlChanged(String newUrl) {
        if (getActivity() != null && mEditUrl != null) {
            getActivity().runOnUiThread(() -> {
                if (mEditUrl != null && !mEditUrl.hasFocus()) {
                    mEditUrl.setText(newUrl);
                }
            });
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
}
