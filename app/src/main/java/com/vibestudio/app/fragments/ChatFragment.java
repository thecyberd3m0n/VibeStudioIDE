package com.vibestudio.app.fragments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.service.ChatService;

import java.util.List;

public class ChatFragment extends Fragment implements ChatService.OnChatMessageListener {

    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private ScrollView mChatScroll;
    private LinearLayout mChatContainer;
    private EditText mMsgInput;
    private Button mBtnSend;
    private ProgressBar mProgressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        if (context == null) return null;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

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
        inputRow.setPadding(0, 12, 0, 0);

        mMsgInput = new EditText(context);
        mMsgInput.setHint("Message VibeStudio...");
        mMsgInput.setHintTextColor(Color.parseColor("#666666"));
        mMsgInput.setTextColor(Color.parseColor("#FFFFFF"));
        mMsgInput.setBackgroundColor(Color.parseColor("#1E1E24"));
        mMsgInput.setPadding(16, 16, 16, 16);

        LinearLayout.LayoutParams inParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        mMsgInput.setLayoutParams(inParams);

        mBtnSend = new Button(context);
        mBtnSend.setText("SEND");
        mBtnSend.setTextColor(Color.parseColor("#121212"));
        mBtnSend.setBackgroundColor(Color.parseColor("#03DAC6"));

        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mMsgInput.getText().toString();
                if (text.trim().length() > 0) {
                    mMsgInput.setText("");
                    ChatService.getInstance().sendMessage(context, text);
                }
            }
        });

        inputRow.addView(mMsgInput);
        inputRow.addView(mBtnSend);

        layout.addView(mChatScroll);
        layout.addView(mProgressBar);
        layout.addView(inputRow);

        ChatService.getInstance().addListener(this);

        // Render current chat history from ChatService
        renderChatHistory(context);

        return layout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ChatService.getInstance().removeListener(this);
    }

    private void renderChatHistory(Context context) {
        if (mChatContainer == null) return;
        mChatContainer.removeAllViews();

        List<ChatService.ChatMessage> history = ChatService.getInstance().getMessages();
        for (ChatService.ChatMessage msg : history) {
            // Do not display raw background TOOL_RESULT messages in UI
            if (msg.getType() == ChatService.ChatMessage.MessageType.TOOL_RESULT) {
                continue;
            }
            if (msg.getType() == ChatService.ChatMessage.MessageType.TOOL_CALL) {
                String text = msg.getText();
                String toolSummary = "⚙️ Executing tool call...";
                try {
                    if (text.contains("{") && text.contains("\"tool\"")) {
                        int jsonStart = text.indexOf("{");
                        int jsonEnd = text.lastIndexOf("}");
                        if (jsonStart != -1 && jsonEnd > jsonStart) {
                            org.json.JSONObject toolCallObj = new org.json.JSONObject(text.substring(jsonStart, jsonEnd + 1).trim());
                            String toolName = toolCallObj.optString("tool");
                            org.json.JSONObject args = toolCallObj.optJSONObject("args");
                            if (args == null) args = new org.json.JSONObject();

                            if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                                String skillName = args.optString("skill_name", "unknown");
                                toolSummary = "🔧 Requesting schema for skill: " + skillName;
                            } else if ("execute_command".equalsIgnoreCase(toolName)) {
                                String cmd = args.optString("command", "");
                                toolSummary = "⚡ Executing terminal command:\n" + cmd;
                            } else if ("read_terminal_output".equalsIgnoreCase(toolName)) {
                                toolSummary = "🔍 Inspecting terminal logs buffer";
                            } else {
                                toolSummary = "⚙️ Executing tool: " + toolName;
                            }
                        }
                    } else {
                        toolSummary = text;
                    }
                } catch (Exception ignored) {
                    toolSummary = text;
                }
                ChatService.ChatMessage summaryMsg = new ChatService.ChatMessage("System Tool", toolSummary, false, ChatService.ChatMessage.MessageType.TOOL_CALL);
                addChatMessageUI(context, summaryMsg);
                continue;
            }
            addChatMessageUI(context, msg);
        }

        updateLoadingUI(ChatService.getInstance().isLoading());
        scrollToBottom();
    }

    private void addChatMessageUI(Context context, ChatService.ChatMessage msg) {
        if (context == null || mChatContainer == null) return;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 14, 18, 14);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);

        if (msg.getType() == ChatService.ChatMessage.MessageType.TOOL_CALL) {
            params.gravity = android.view.Gravity.LEFT;
            card.setBackgroundColor(Color.parseColor("#1E2A38")); // Dark blue accent for tool invocation
        } else if (msg.isUser()) {
            params.gravity = android.view.Gravity.RIGHT;
            card.setBackgroundColor(Color.parseColor("#3700B3"));
        } else {
            params.gravity = android.view.Gravity.LEFT;
            card.setBackgroundColor(Color.parseColor("#25252A"));
        }
        card.setLayoutParams(params);

        TextView tvSender = new TextView(context);
        tvSender.setText(msg.getSender());
        if (msg.getType() == ChatService.ChatMessage.MessageType.TOOL_CALL) {
            tvSender.setTextColor(Color.parseColor("#64B5F6")); // Light blue header
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
        tvText.setTextColor(Color.parseColor("#FFFFFF"));
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
        if (mBtnSend != null) {
            mBtnSend.setEnabled(!isLoading);
        }
        if (mMsgInput != null) {
            mMsgInput.setEnabled(!isLoading);
        }
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
    public void onMessageAdded(ChatService.ChatMessage message) {
        Context context = getContext();
        if (context != null) {
            if (message.getType() != ChatService.ChatMessage.MessageType.TOOL_RESULT) {
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
