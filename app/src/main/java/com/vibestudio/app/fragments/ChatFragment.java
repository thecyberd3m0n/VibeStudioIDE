package com.vibestudio.app.fragments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.chat.connection.ModelConnectionManager;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.ToolMessageFactory;
import com.vibestudio.app.service.ChatService;
import com.vibestudio.app.view.BaseToolMessageView;

import java.util.List;

public class ChatFragment extends Fragment implements ChatService.OnChatMessageListener {

    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private ScrollView mChatScroll;
    private LinearLayout mChatContainer;
    private EditText mMsgInput;
    private FrameLayout mBtnActionButton;
    private TextView mTvActionIcon;
    private ProgressBar mProgressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        if (context == null) return null;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        layout.setBackgroundColor(Color.parseColor("#1E1E1E"));

        mChatScroll = new ScrollView(context);
        mChatContainer = new LinearLayout(context);
        mChatContainer.setOrientation(LinearLayout.VERTICAL);
        mChatScroll.addView(mChatContainer);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.0f);
        mChatScroll.setLayoutParams(scrollParams);

        mProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setIndeterminate(true);
        mProgressBar.setVisibility(View.GONE);

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, 12, 0, 0);

        mMsgInput = new EditText(context);
        mMsgInput.setHint("Message VibeStudio...");
        mMsgInput.setHintTextColor(Color.parseColor("#666666"));
        mMsgInput.setTextColor(Color.parseColor("#FFFFFF"));
        mMsgInput.setBackgroundColor(Color.parseColor("#1E1E24"));
        mMsgInput.setPadding(24, 20, 24, 20);

        LinearLayout.LayoutParams inParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        mMsgInput.setLayoutParams(inParams);

        // Round action button container (48dp circle)
        int buttonSizePx = dpToPx(context, 48);
        mBtnActionButton = new FrameLayout(context);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(buttonSizePx, buttonSizePx);
        btnParams.setMargins(12, 0, 0, 0);
        mBtnActionButton.setLayoutParams(btnParams);
        mBtnActionButton.setBackgroundResource(R.drawable.round_send_button_bg);

        mTvActionIcon = new TextView(context);
        mTvActionIcon.setText("➔");
        mTvActionIcon.setTextColor(Color.parseColor("#121212"));
        mTvActionIcon.setTextSize(20);
        mTvActionIcon.setTypeface(null, Typeface.BOLD);
        
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        iconParams.gravity = Gravity.CENTER;
        mTvActionIcon.setLayoutParams(iconParams);
        mBtnActionButton.addView(mTvActionIcon);

        mBtnActionButton.setOnClickListener(v -> onActionButtonClicked(context));

        mMsgInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateActionButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        inputRow.addView(mMsgInput);
        inputRow.addView(mBtnActionButton);

        layout.addView(mChatScroll);
        layout.addView(mProgressBar);
        layout.addView(inputRow);

        ChatService.getInstance().addListener(this);

        // Render current chat history from ChatService
        renderChatHistory(context);

        return layout;
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private void onActionButtonClicked(Context context) {
        boolean isLoading = ChatService.getInstance().isLoading();
        String text = mMsgInput.getText() != null ? mMsgInput.getText().toString() : "";

        if (isLoading && text.trim().isEmpty()) {
            // User pressed STOP while prompt is empty and agent is operating
            ChatService.getInstance().stopAgentExecution();
            Toast.makeText(context, "Stopping Agent execution...", Toast.LENGTH_SHORT).show();
            updateActionButtonState();
            return;
        }

        if (text.trim().length() > 0) {
            if (!ModelConnectionManager.getInstance().isConnectedAndConfigured(context)) {
                Toast.makeText(context, ModelConnectionManager.getInstance().getConnectionStatusMessage(context), Toast.LENGTH_LONG).show();
            }
            mMsgInput.setText("");
            ChatService.getInstance().sendMessage(context, text);
            updateActionButtonState();
        }
    }

    private void updateActionButtonState() {
        if (mBtnActionButton == null || mTvActionIcon == null) return;

        boolean isLoading = ChatService.getInstance().isLoading();
        String text = mMsgInput.getText() != null ? mMsgInput.getText().toString() : "";

        if (isLoading && text.trim().isEmpty()) {
            // Show delicate STOP button state (subtle dark grey circle with red stop square)
            mBtnActionButton.setBackgroundResource(R.drawable.round_stop_button_bg);
            mTvActionIcon.setText("■");
            mTvActionIcon.setTextColor(Color.parseColor("#FF6B6B"));
        } else {
            // Show SEND button state
            mBtnActionButton.setBackgroundResource(R.drawable.round_send_button_bg);
            mTvActionIcon.setText("➔");
            mTvActionIcon.setTextColor(Color.parseColor("#121212"));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ChatService.getInstance().removeListener(this);
    }

    private void renderChatHistory(Context context) {
        if (mChatContainer == null) return;
        mChatContainer.removeAllViews();

        List<ChatMessage> history = ChatService.getInstance().getMessages();
        for (ChatMessage msg : history) {
            // Do not display raw background TOOL_RESULT messages in UI
            if (msg.getType() == ChatMessage.MessageType.TOOL_RESULT) {
                continue;
            }
            addChatMessageUI(context, msg);
        }

        updateLoadingUI(ChatService.getInstance().isLoading());
        scrollToBottom();
    }

    private void addChatMessageUI(Context context, ChatMessage msg) {
        if (context == null || mChatContainer == null) return;

        // Centralized SOLID check and view creation via ToolMessageFactory
        if (ToolMessageFactory.isToolMessage(msg)) {
            BaseToolMessageView toolView = ToolMessageFactory.createToolMessageView(context, msg);
            mChatContainer.addView(toolView);
            return;
        }

        // Standard User / Assistant / Error Message Card
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 14, 18, 14);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);

        if (msg.isError() || msg.getType() == ChatMessage.MessageType.ERROR) {
            params.gravity = Gravity.LEFT;
            params.width = MATCH_PARENT;
            card.setBackgroundResource(R.drawable.bg_error_card);
        } else if (msg.isUser()) {
            params.gravity = Gravity.RIGHT;
            card.setBackgroundColor(Color.parseColor("#3700B3"));
        } else {
            params.gravity = Gravity.LEFT;
            card.setBackgroundColor(Color.parseColor("#25252A"));
        }
        card.setLayoutParams(params);

        TextView tvSender = new TextView(context);
        tvSender.setText(msg.getSender());
        if (msg.isError() || msg.getType() == ChatMessage.MessageType.ERROR) {
            tvSender.setTextColor(Color.parseColor("#CF6679"));
        } else if (msg.isUser()) {
            tvSender.setTextColor(Color.parseColor("#03DAC6"));
        } else {
            tvSender.setTextColor(Color.parseColor("#BB86FC"));
        }
        tvSender.setTextSize(12);
        tvSender.setTypeface(null, Typeface.BOLD);
        tvSender.setTextIsSelectable(true);

        TextView tvText = new TextView(context);
        tvText.setText(msg.getText());
        if (msg.isError() || msg.getType() == ChatMessage.MessageType.ERROR) {
            tvText.setTextColor(Color.parseColor("#FFB4AB"));
        } else {
            tvText.setTextColor(Color.parseColor("#FFFFFF"));
        }
        tvText.setTextSize(14);
        tvText.setPadding(0, 4, 0, 0);
        tvText.setTextIsSelectable(true);

        card.addView(tvSender);
        card.addView(tvText);
        mChatContainer.addView(card);
    }

    private void updateLoadingUI(boolean isLoading) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        updateActionButtonState();
    }

    private void scrollToBottom() {
        if (mChatScroll != null) {
            mChatScroll.post(new Runnable() {
                @Override
                public void run() {
                    mChatScroll.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }
    }

    @Override
    public void onMessageAdded(ChatMessage message) {
        Context context = getContext();
        if (context != null) {
            if (message.getType() != ChatMessage.MessageType.TOOL_RESULT) {
                addChatMessageUI(context, message);
                scrollToBottom();
            }
        }
    }

    @Override
    public void onResponseLoading(boolean isLoading) {
        updateLoadingUI(isLoading);
    }
}
