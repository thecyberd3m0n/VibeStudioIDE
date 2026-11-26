package com.vibestudio.app.fragments;

import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import com.vibestudio.app.fragments.ImageViewerFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.PopupMenu;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import com.vibestudio.app.chat.model.ChatAttachment;


import java.io.File;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.chat.connection.ModelConnectionManager;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.chat.skills.Skill;
import java.util.ArrayList;

import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import android.widget.Button;
import com.vibestudio.app.chat.session.ChatSessionManager;
import com.vibestudio.app.mcp.ToolMessageFactory;
import com.vibestudio.app.view.BaseToolMessageView;

import java.util.List;

public class ChatFragment extends Fragment implements ChatSession.OnChatMessageListener {

    public static class SkillSpan {
        private final String mSkillName;

        public SkillSpan(String skillName) {
            this.mSkillName = skillName;
        }

        public String getSkillName() {
            return mSkillName;
        }
    }


    private static final String ARG_SESSION_ID = "session_id";
    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private ChatSession mChatSession;
    private String mSessionId;
    private static final int REQUEST_CODE_PICK_IMAGE = 2001;
    private static final int REQUEST_CODE_TAKE_PHOTO = 2002;
    private static final int REQUEST_CODE_PICK_FILE = 2003;

    private LinearLayout mAttachmentsPreviewContainer;
    private HorizontalScrollView mAttachmentsPreviewScroll;
    private final List<ChatAttachment> mPendingAttachments = new ArrayList<>();


    private ScrollView mChatScroll;
    private LinearLayout mChatContainer;
    private EditText mMsgInput;
    private FrameLayout mBtnActionButton;
    private TextView mTvActionIcon;
    private TextView mTvCharCounter;
    private Markwon mMarkwon;
    private ProgressBar mProgressBar;
    private LinearLayout mChangesetBar;

    private LinearLayout mSkillAutocompleteContainer;
    private ScrollView mSkillAutocompleteScroll;
    private LinearLayout mSkillAutocompleteList;

    public static ChatFragment newInstance(ChatSession session) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        if (session != null) {
            args.putString(ARG_SESSION_ID, session.getId());
        }
        fragment.setArguments(args);
        return fragment;
    }

    private void ensureSession() {
        if (mSessionId == null && getArguments() != null) {
            mSessionId = getArguments().getString(ARG_SESSION_ID);
        }
        if (mChatSession == null && mSessionId != null) {
            mChatSession = ChatSessionManager.getInstance().getSession(mSessionId);
        }
        if (mChatSession == null) {
            mChatSession = ChatSessionManager.getInstance().createSession();
            mSessionId = mChatSession.getId();
        }
    }

    public ChatSession getChatSession() {
        ensureSession();
        return mChatSession;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureSession();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ensureSession();
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

        mAttachmentsPreviewScroll = new HorizontalScrollView(context);
        mAttachmentsPreviewScroll.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        mAttachmentsPreviewScroll.setVisibility(View.GONE);
        mAttachmentsPreviewScroll.setPadding(0, 0, 0, 8);

        mAttachmentsPreviewContainer = new LinearLayout(context);
        mAttachmentsPreviewContainer.setOrientation(LinearLayout.HORIZONTAL);
        mAttachmentsPreviewScroll.addView(mAttachmentsPreviewContainer);

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, 12, 0, 0);

        int addBtnSizePx = dpToPx(context, 48);
        FrameLayout btnAdd = new FrameLayout(context);
        LinearLayout.LayoutParams addBtnParams = new LinearLayout.LayoutParams(addBtnSizePx, addBtnSizePx);
        addBtnParams.setMargins(0, 0, 12, 0);
        btnAdd.setLayoutParams(addBtnParams);
        btnAdd.setBackgroundResource(R.drawable.round_add_button_bg);

        TextView tvAddIcon = new TextView(context);
        tvAddIcon.setText("+");
        tvAddIcon.setTextColor(Color.parseColor("#A0A0A0"));
        tvAddIcon.setTextSize(24);
        tvAddIcon.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams addIconParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        addIconParams.gravity = Gravity.CENTER;
        tvAddIcon.setLayoutParams(addIconParams);
        btnAdd.addView(tvAddIcon);

        btnAdd.setOnClickListener(v -> showAttachmentDropdown(v, context));

        inputRow.addView(btnAdd);

        mMsgInput = new EditText(context);
        mMsgInput.setHint("Message VibeStudio...");
        mMsgInput.setHintTextColor(Color.parseColor("#666666"));
        mMsgInput.setTextColor(Color.parseColor("#FFFFFF"));
        mMsgInput.setBackgroundColor(Color.parseColor("#1E1E24"));
        mMsgInput.setPadding(24, 20, 24, 20);

        mMsgInput.setFilters(new InputFilter[] {
            new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    if (source != null && source.length() == 0 && dend - dstart == 1) {
                        SkillSpan[] spans = dest.getSpans(0, dest.length(), SkillSpan.class);
                        for (SkillSpan span : spans) {
                            int spanStart = dest.getSpanStart(span);
                            int spanEnd = dest.getSpanEnd(span);
                            if (spanStart < 0 || spanEnd < 0) continue;

                            boolean touchesSpan = (dstart >= spanStart && dstart <= spanEnd) ||
                                                  (dstart == spanEnd && dend == spanEnd + 1) ||
                                                  (dstart == spanEnd && spanEnd < dest.length() && dest.charAt(spanEnd) == ' ');

                            if (touchesSpan) {
                                final int deleteStart = spanStart;
                                final int deleteEnd = (spanEnd < dest.length() && dest.charAt(spanEnd) == ' ') ? spanEnd + 1 : spanEnd;
                                mMsgInput.post(() -> {
                                    Editable editable = mMsgInput.getText();
                                    if (editable != null) {
                                        int st = Math.min(deleteStart, editable.length());
                                        int en = Math.min(deleteEnd, editable.length());
                                        if (st >= 0 && en > st) {
                                            editable.removeSpan(span);
                                            editable.delete(st, en);
                                        }
                                    }
                                });
                                return "";
                            }
                        }
                    }
                    return null;
                }
            },
            new InputFilter.LengthFilter(ChatSession.MAX_INPUT_CHARS)
        });

        LinearLayout inputWrapper = new LinearLayout(context);
        inputWrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams inWrapperParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        inputWrapper.setLayoutParams(inWrapperParams);

        LinearLayout.LayoutParams inParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        mMsgInput.setLayoutParams(inParams);
        inputWrapper.addView(mMsgInput);

        mTvCharCounter = new TextView(context);
        mTvCharCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        mTvCharCounter.setTextColor(Color.parseColor("#707070"));
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        counterParams.gravity = Gravity.END;
        counterParams.setMargins(0, 4, 4, 0);
        mTvCharCounter.setLayoutParams(counterParams);
        inputWrapper.addView(mTvCharCounter);
        updateCharCounter();

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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCharCounter();
                updateActionButtonState();
                checkAndShowSkillAutocomplete(context, s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s == null || mChatSession == null) return;
                String text = s.toString();
                List<Skill> skills = mChatSession.getAvailableSkills();
                if (skills == null) return;
                for (Skill skill : skills) {
                    String cmd = skill.getSlashCommand();
                    int index = 0;
                    while ((index = text.indexOf(cmd, index)) != -1) {
                        int spanStart = index;
                        int spanEnd = index + cmd.length();

                        boolean validPre = (spanStart == 0 || Character.isWhitespace(text.charAt(spanStart - 1)));
                        boolean validPost = (spanEnd == text.length() || Character.isWhitespace(text.charAt(spanEnd)));

                        if (validPre && validPost) {
                            SkillSpan[] existingSpans = s.getSpans(spanStart, spanEnd, SkillSpan.class);
                            if (existingSpans.length == 0) {
                                SkillSpan skillSpan = new SkillSpan(skill.getName());
                                ForegroundColorSpan colorSpan = new ForegroundColorSpan(Color.parseColor("#388BFD"));
                                StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
                                UnderlineSpan underlineSpan = new UnderlineSpan();

                                s.setSpan(skillSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                s.setSpan(colorSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                s.setSpan(boldSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                s.setSpan(underlineSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                        index = spanEnd;
                    }
                }
            }
        });

        inputRow.addView(inputWrapper);
        inputRow.addView(mBtnActionButton);

                // Scope Topbar Header
        if (mChatSession != null && mChatSession.getWorkspacePath() != null && !mChatSession.getWorkspacePath().isEmpty()) {
            java.io.File wsDir = new java.io.File(mChatSession.getWorkspacePath());
            boolean isRepo = com.vibestudio.app.git.GitManager.getInstance().isGitRoot(wsDir) || com.vibestudio.app.git.GitManager.getInstance().isGitRepository(wsDir);
            int iconResId = isRepo ? R.drawable.ic_git_repo : R.drawable.ic_folder;
            String scopeName = wsDir.getName();

            LinearLayout topBar = new LinearLayout(context);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setBackgroundColor(Color.parseColor("#1A1D24"));
            topBar.setPadding(24, 16, 24, 16);

            TextView labelText = new TextView(context);
            labelText.setText("Scope: ");
            labelText.setTextColor(Color.parseColor("#9095A0"));
            labelText.setTextSize(13);
            labelText.setTypeface(null, Typeface.BOLD);
            topBar.addView(labelText);

            ImageView iconView = new ImageView(context);
            iconView.setImageResource(iconResId);
            int iconSize = dpToPx(context, 18);
            LinearLayout.LayoutParams topbarIconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMargins(4, 0, 8, 0);
            iconView.setLayoutParams(topbarIconParams);
            topBar.addView(iconView);

            TextView nameText = new TextView(context);
            nameText.setText(scopeName);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(13);
            nameText.setTypeface(null, Typeface.BOLD);
            topBar.addView(nameText);

            LinearLayout.LayoutParams topBarParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            topBar.setLayoutParams(topBarParams);
            layout.addView(topBar);
        }

        // Skill Autocomplete Container (Drop-Up Popup View)
        mSkillAutocompleteContainer = new LinearLayout(context);
        mSkillAutocompleteContainer.setOrientation(LinearLayout.VERTICAL);
        mSkillAutocompleteContainer.setVisibility(View.GONE);

        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        popupBg.setCornerRadii(new float[]{
                dpToPx(context, 8), dpToPx(context, 8),
                dpToPx(context, 8), dpToPx(context, 8),
                0, 0, 0, 0
        });
        popupBg.setColor(Color.parseColor("#252526"));
        popupBg.setStroke(dpToPx(context, 1), Color.parseColor("#3A3E4B"));
        mSkillAutocompleteContainer.setBackground(popupBg);
        mSkillAutocompleteContainer.setPadding(0, 4, 0, 4);

        mSkillAutocompleteScroll = new ScrollView(context);
        LinearLayout.LayoutParams popupScrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, dpToPx(context, 160));
        mSkillAutocompleteScroll.setLayoutParams(popupScrollParams);

        mSkillAutocompleteList = new LinearLayout(context);
        mSkillAutocompleteList.setOrientation(LinearLayout.VERTICAL);
        mSkillAutocompleteScroll.addView(mSkillAutocompleteList);
        mSkillAutocompleteContainer.addView(mSkillAutocompleteScroll);

        layout.addView(mChatScroll);
        layout.addView(mSkillAutocompleteContainer);
        // Changeset Bar
        mChangesetBar = new LinearLayout(context);
        mChangesetBar.setOrientation(LinearLayout.HORIZONTAL);
        mChangesetBar.setGravity(Gravity.CENTER_VERTICAL);
        mChangesetBar.setBackgroundColor(Color.parseColor("#1F2430"));
        mChangesetBar.setPadding(20, 12, 20, 12);
        mChangesetBar.setVisibility(View.GONE);

        layout.addView(mProgressBar);
        layout.addView(mChangesetBar);
        layout.addView(mAttachmentsPreviewScroll);
        layout.addView(inputRow);

        if (mChatSession != null) {
            mChatSession.addListener(this);
            renderChatHistory(context);
            updateChangesetBar(context);
        }

        return layout;
    }

    
    
    private void checkAndShowSkillAutocomplete(Context context, String input) {
        if (mSkillAutocompleteContainer == null || mSkillAutocompleteList == null) return;
        if (input == null || input.isEmpty()) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
            return;
        }

        int selectionEnd = mMsgInput.getSelectionEnd();
        if (selectionEnd <= 0) selectionEnd = input.length();
        String sub = input.substring(0, selectionEnd);

        int lastSlash = sub.lastIndexOf("/");
        if (lastSlash < 0) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
            return;
        }

        if (lastSlash > 0 && !Character.isWhitespace(sub.charAt(lastSlash - 1))) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
            return;
        }

        String query = sub.substring(lastSlash + 1);
        if (query.contains(" ")) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
            return;
        }

        List<Skill> allSkills = getChatSession().getAvailableSkills();
        List<Skill> matchedSkills = new ArrayList<>();
        for (Skill s : allSkills) {
            if (s.getName().toLowerCase().startsWith(query.toLowerCase())
                    || s.getName().toLowerCase().contains(query.toLowerCase())) {
                matchedSkills.add(s);
            }
        }

        if (matchedSkills.isEmpty()) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
            return;
        }

        renderSkillAutocompleteList(context, matchedSkills, lastSlash, selectionEnd);
        mSkillAutocompleteContainer.setVisibility(View.VISIBLE);
    }

    private void renderSkillAutocompleteList(Context context, List<Skill> skills, int lastSlashIdx, int selectionEnd) {
        mSkillAutocompleteList.removeAllViews();

        for (Skill skill : skills) {
            LinearLayout itemRow = new LinearLayout(context);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(dpToPx(context, 16), dpToPx(context, 10), dpToPx(context, 16), dpToPx(context, 10));
            itemRow.setClickable(true);
            itemRow.setFocusable(true);

            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            itemRow.setBackgroundResource(outValue.resourceId);

            TextView tvCmd = new TextView(context);
            tvCmd.setText(skill.getSlashCommand());
            tvCmd.setTextColor(Color.parseColor("#80D0FF"));
            tvCmd.setTextSize(14);
            tvCmd.setTypeface(null, Typeface.BOLD);
            tvCmd.setPadding(0, 0, dpToPx(context, 12), 0);
            itemRow.addView(tvCmd);

            TextView tvDesc = new TextView(context);
            tvDesc.setText(skill.getDescription());
            tvDesc.setTextColor(Color.parseColor("#A0A5B5"));
            tvDesc.setTextSize(12);
            tvDesc.setSingleLine(true);
            tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
            tvDesc.setLayoutParams(descParams);
            itemRow.addView(tvDesc);

            itemRow.setOnClickListener(v -> {
                Editable text = mMsgInput.getText();
                if (text != null) {
                    String cmd = skill.getSlashCommand();
                    String replacement = cmd + " ";
                    int spanStart = lastSlashIdx;
                    int spanEnd = lastSlashIdx + cmd.length();

                    text.replace(lastSlashIdx, selectionEnd, replacement);

                    SkillSpan skillSpan = new SkillSpan(skill.getName());
                    ForegroundColorSpan colorSpan = new ForegroundColorSpan(Color.parseColor("#388BFD"));
                    StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
                    UnderlineSpan underlineSpan = new UnderlineSpan();

                    text.setSpan(skillSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(colorSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(boldSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(underlineSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    mMsgInput.setSelection(spanStart + replacement.length());
                }
                mSkillAutocompleteContainer.setVisibility(View.GONE);
            });

            mSkillAutocompleteList.addView(itemRow);
        }
    }

    private boolean mIsChangesetExpanded = true;

    public void refreshChangeset() {
        Context context = getContext();
        if (context != null) {
            updateChangesetBar(context);
        }
    }

    private void updateChangesetBar(Context context) {
        if (mChangesetBar == null || mChatSession == null) return;
        WorkspaceChangesetTracker tracker = mChatSession.getChangesetTracker(context);
        if (tracker == null) return;

        List<WorkspaceChangesetTracker.FileChangeRecord> changes = tracker.getChanges();
        if (changes.isEmpty()) {
            mChangesetBar.setVisibility(View.GONE);
            return;
        }

        mChangesetBar.removeAllViews();
        mChangesetBar.setOrientation(LinearLayout.VERTICAL);
        mChangesetBar.setVisibility(View.VISIBLE);

        // Header Row (Summary + Toggle Icon + Keep All + Undo All)
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, mIsChangesetExpanded ? 8 : 0);

        TextView tvToggle = new TextView(context);
        tvToggle.setText(mIsChangesetExpanded ? "▼ " : "▶ ");
        tvToggle.setTextColor(Color.parseColor("#80D0FF"));
        tvToggle.setTextSize(12);
        tvToggle.setPadding(0, 0, 8, 0);
        headerRow.addView(tvToggle);

        TextView infoText = new TextView(context);
        infoText.setText("⚡ Agent Changes (" + changes.size() + ")");
        infoText.setTextColor(Color.parseColor("#FFD166"));
        infoText.setTextSize(13);
        infoText.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        infoText.setLayoutParams(infoParams);
        headerRow.addView(infoText);

        headerRow.setOnClickListener(v -> {
            mIsChangesetExpanded = !mIsChangesetExpanded;
            updateChangesetBar(context);
        });

        TextView btnKeepAll = new TextView(context);
        btnKeepAll.setText("Keep");
        btnKeepAll.setTextColor(Color.WHITE);
        btnKeepAll.setTextSize(11);
        btnKeepAll.setTypeface(null, Typeface.BOLD);
        btnKeepAll.setGravity(Gravity.CENTER);
        btnKeepAll.setPadding(dpToPx(context, 10), 0, dpToPx(context, 10), 0);
        btnKeepAll.setClickable(true);
        btnKeepAll.setFocusable(true);
        
        android.graphics.drawable.GradientDrawable keepBg = new android.graphics.drawable.GradientDrawable();
        keepBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        keepBg.setCornerRadius(dpToPx(context, 4));
        keepBg.setColor(Color.parseColor("#0E639C"));
        btnKeepAll.setBackground(keepBg);

        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(WRAP_CONTENT, dpToPx(context, 26));
        keepParams.setMargins(6, 0, 6, 0);
        btnKeepAll.setLayoutParams(keepParams);
        btnKeepAll.setOnClickListener(v -> {
            tracker.clear();
            updateChangesetBar(context);
            Toast.makeText(context, "All agent changes kept.", Toast.LENGTH_SHORT).show();
        });
        headerRow.addView(btnKeepAll);

        TextView btnUndoAll = new TextView(context);
        btnUndoAll.setText("Undo");
        btnUndoAll.setTextColor(Color.WHITE);
        btnUndoAll.setTextSize(11);
        btnUndoAll.setTypeface(null, Typeface.BOLD);
        btnUndoAll.setGravity(Gravity.CENTER);
        btnUndoAll.setPadding(dpToPx(context, 10), 0, dpToPx(context, 10), 0);
        btnUndoAll.setClickable(true);
        btnUndoAll.setFocusable(true);

        android.graphics.drawable.GradientDrawable undoBg = new android.graphics.drawable.GradientDrawable();
        undoBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        undoBg.setCornerRadius(dpToPx(context, 4));
        undoBg.setColor(Color.parseColor("#3A3E4B"));
        btnUndoAll.setBackground(undoBg);

        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(WRAP_CONTENT, dpToPx(context, 26));
        btnUndoAll.setLayoutParams(undoParams);
        btnUndoAll.setOnClickListener(v -> {
            boolean ok = tracker.rollbackAll();
            updateChangesetBar(context);
            Toast.makeText(context, ok ? "All agent changes reverted!" : "Failed to revert some changes.", Toast.LENGTH_SHORT).show();
        });
        headerRow.addView(btnUndoAll);

        mChangesetBar.addView(headerRow);

        // Expandable List of Edited Files
        if (mIsChangesetExpanded) {
            ScrollView fileListScroll = new ScrollView(context);
            int maxListHeight = dpToPx(context, 140);
            LinearLayout.LayoutParams listScrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            if (changes.size() > 3) listScrollParams.height = maxListHeight;
            fileListScroll.setLayoutParams(listScrollParams);
            

            LinearLayout fileContainer = new LinearLayout(context);
            fileContainer.setOrientation(LinearLayout.VERTICAL);

            for (WorkspaceChangesetTracker.FileChangeRecord record : changes) {
                File file = new File(record.getFilePath());
                String relativeName = mChatSession.getWorkspacePath() != null
                        ? file.getAbsolutePath().replace(mChatSession.getWorkspacePath() + "/", "")
                        : file.getName();

                LinearLayout fileRow = new LinearLayout(context);
                fileRow.setOrientation(LinearLayout.HORIZONTAL);
                fileRow.setGravity(Gravity.CENTER_VERTICAL);
                fileRow.setPadding(8, 8, 8, 8);
                fileRow.setBackgroundColor(Color.parseColor("#171A21"));
                LinearLayout.LayoutParams fileRowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                fileRowParams.setMargins(0, 4, 0, 4);
                fileRow.setLayoutParams(fileRowParams);

                TextView tvBadge = new TextView(context);
                String badgeText = "[M]";
                int badgeColor = Color.parseColor("#E5C07B");
                if (record.getChangeType() == WorkspaceChangesetTracker.ChangeType.CREATED) {
                    badgeText = "[A]";
                    badgeColor = Color.parseColor("#98C379");
                } else if (record.getChangeType() == WorkspaceChangesetTracker.ChangeType.DELETED) {
                    badgeText = "[D]";
                    badgeColor = Color.parseColor("#E06C75");
                }
                tvBadge.setText(badgeText + " ");
                tvBadge.setTextColor(badgeColor);
                tvBadge.setTextSize(12);
                tvBadge.setTypeface(null, Typeface.BOLD);
                fileRow.addView(tvBadge);

                TextView tvFileName = new TextView(context);
                tvFileName.setText(relativeName);
                tvFileName.setTextColor(Color.parseColor("#61AFEF"));
                tvFileName.setTextSize(12);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
                tvFileName.setLayoutParams(nameParams);
                fileRow.addView(tvFileName);

                int[] stats = WorkspaceChangesetTracker.getLineStats(record);
                int additions = stats[0];
                int deletions = stats[1];

                TextView tvStats = new TextView(context);
                tvStats.setTextSize(11);
                tvStats.setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0);

                SpannableStringBuilder statsBuilder = new SpannableStringBuilder();
                int addStart = statsBuilder.length();
                statsBuilder.append("+").append(String.valueOf(additions));
                statsBuilder.setSpan(new ForegroundColorSpan(Color.parseColor("#4EC9B0")), addStart, statsBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                int sepStart = statsBuilder.length();
                statsBuilder.append(" / ");
                statsBuilder.setSpan(new ForegroundColorSpan(Color.parseColor("#7A828E")), sepStart, statsBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                int delStart = statsBuilder.length();
                statsBuilder.append("-").append(String.valueOf(deletions));
                statsBuilder.setSpan(new ForegroundColorSpan(Color.parseColor("#E06C75")), delStart, statsBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                tvStats.setText(statsBuilder);
                fileRow.addView(tvStats);

                // Clicking row opens diff view in MainActivity
                fileRow.setOnClickListener(v -> {
                    if (getActivity() instanceof com.vibestudio.app.activity.MainActivity) {
                        File f1 = record.getBackupFile();
                        File f2 = new File(record.getFilePath());
                        if (f1 != null && f2 != null) {
                            ((com.vibestudio.app.activity.MainActivity) getActivity()).openDiffInEditor(f1.getAbsolutePath(), f2.getAbsolutePath());
                        }
                    }
                });

                Button btnSingleUndo = new Button(context);
                btnSingleUndo.setText("↩");
                btnSingleUndo.setTextColor(Color.parseColor("#E06C75"));
                btnSingleUndo.setTextSize(12);
                btnSingleUndo.setBackgroundColor(Color.TRANSPARENT);
                LinearLayout.LayoutParams singleUndoParams = new LinearLayout.LayoutParams(dpToPx(context, 28), dpToPx(context, 28));
                btnSingleUndo.setLayoutParams(singleUndoParams);
                btnSingleUndo.setOnClickListener(v -> {
                    boolean ok = tracker.rollbackSingle(record);
                    updateChangesetBar(context);
                    Toast.makeText(context, ok ? "Reverted " + relativeName : "Failed to revert " + relativeName, Toast.LENGTH_SHORT).show();
                });
                fileRow.addView(btnSingleUndo);

                fileContainer.addView(fileRow);
            }

            fileListScroll.addView(fileContainer);
            mChangesetBar.addView(fileListScroll);
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    
    
    private void openAttachmentInImageViewer(ChatAttachment attachment) {
        if (attachment == null || attachment.getBase64Data() == null) return;
        if (attachment.getMimeType() != null && attachment.getMimeType().startsWith("image/")) {
            ImageViewerFragment fragment = ImageViewerFragment.newInstanceWithBase64(attachment.getName(), attachment.getBase64Data());
            TabManager.getInstance().openTab(TabType.IMAGE, attachment.getName(), fragment, true);
        }
    }

    private void showAttachmentDropdown(View anchor, Context context) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenu().add(0, 1, 0, "Photo");
        popup.getMenu().add(0, 2, 1, "Camera");
        popup.getMenu().add(0, 3, 2, "File");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    openGalleryPicker();
                    return true;
                case 2:
                    openCameraPicker();
                    return true;
                case 3:
                    openFilePicker();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void openGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Photo"), REQUEST_CODE_PICK_IMAGE);
    }

    private void openCameraPicker() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (getActivity() != null && intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO);
        } else {
            Toast.makeText(getContext(), "Camera unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_CODE_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        Context context = getContext();
        if (context == null) return;

        if (requestCode == REQUEST_CODE_PICK_IMAGE && data != null && data.getData() != null) {
            handleSelectedUri(context, data.getData(), ChatAttachment.AttachmentType.PHOTO);
        } else if (requestCode == REQUEST_CODE_TAKE_PHOTO && data != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            if (photo != null) {
                handleBitmap(context, photo, "camera_photo.jpg", ChatAttachment.AttachmentType.CAMERA);
            }
        } else if (requestCode == REQUEST_CODE_PICK_FILE && data != null && data.getData() != null) {
            handleSelectedUri(context, data.getData(), ChatAttachment.AttachmentType.FILE);
        }
    }

    
    private byte[] resizeImageIfNeeded(byte[] bytes, String mimeType) {
        if (mimeType == null || !mimeType.startsWith("image/") || "image/gif".equals(mimeType)) {
            return bytes;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

            int width = options.outWidth;
            int height = options.outHeight;
            if (width <= 0 || height <= 0) return bytes;

            if (width <= 768 && height <= 768) {
                return bytes;
            }

            if (width > 2048 || height > 2048) {
                float scaleFactor = 2048.0f / Math.max(width, height);
                width = Math.round(width * scaleFactor);
                height = Math.round(height * scaleFactor);
            }

            float scaleFactor = 768.0f / Math.min(width, height);
            width = Math.round(width * scaleFactor);
            height = Math.round(height * scaleFactor);

            Bitmap original = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (original == null) return bytes;

            Bitmap scaled = Bitmap.createScaledBitmap(original, width, height, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Bitmap.CompressFormat format = ("image/png".equalsIgnoreCase(mimeType)) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            scaled.compress(format, 85, baos);
            if (original != scaled) original.recycle();
            scaled.recycle();
            return baos.toByteArray();
        } catch (Exception e) {
            return bytes;
        }
    }

    private void handleSelectedUri(Context context, Uri uri, ChatAttachment.AttachmentType type) {
        try {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "application/octet-stream";

            String fileName = getFileNameFromUri(context, uri);
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            is.close();

            byte[] bytes = baos.toByteArray();
            if (mimeType.startsWith("image/")) {
                bytes = resizeImageIfNeeded(bytes, mimeType);
                if ("image/jpg".equalsIgnoreCase(mimeType)) mimeType = "image/jpeg";
            }
            String base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP);

            Bitmap thumbnail = null;
            if (mimeType.startsWith("image/")) {
                thumbnail = decodeSampledBitmapFromBytes(bytes, 120, 120);
            }

            ChatAttachment attachment = new ChatAttachment(fileName, mimeType, base64Data, type, thumbnail);
            addPendingAttachment(context, attachment);
        } catch (Exception e) {
            Toast.makeText(context, "Failed to load file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleBitmap(Context context, Bitmap bitmap, String fileName, ChatAttachment.AttachmentType type) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] bytes = baos.toByteArray();
            bytes = resizeImageIfNeeded(bytes, "image/jpeg");
            String base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP);

            ChatAttachment attachment = new ChatAttachment(fileName, "image/jpeg", base64Data, type, bitmap);
            addPendingAttachment(context, attachment);
        } catch (Exception e) {
            Toast.makeText(context, "Failed to process image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Context context, Uri uri) {
        String fileName = uri.getLastPathSegment();
        if (fileName == null) fileName = "file";
        int cut = fileName.lastIndexOf('/');
        if (cut != -1) {
            fileName = fileName.substring(cut + 1);
        }
        return fileName;
    }

    private Bitmap decodeSampledBitmapFromBytes(byte[] bytes, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void addPendingAttachment(Context context, ChatAttachment attachment) {
        mPendingAttachments.add(attachment);
        updatePendingAttachmentsUI(context);
        updateActionButtonState();
    }

    private void removePendingAttachment(Context context, ChatAttachment attachment) {
        mPendingAttachments.remove(attachment);
        updatePendingAttachmentsUI(context);
        updateActionButtonState();
    }

    private void updatePendingAttachmentsUI(Context context) {
        if (mAttachmentsPreviewContainer == null || mAttachmentsPreviewScroll == null) return;
        mAttachmentsPreviewContainer.removeAllViews();

        if (mPendingAttachments.isEmpty()) {
            mAttachmentsPreviewScroll.setVisibility(View.GONE);
            return;
        }

        mAttachmentsPreviewScroll.setVisibility(View.VISIBLE);

        for (final ChatAttachment attachment : mPendingAttachments) {
            LinearLayout chip = new LinearLayout(context);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackgroundColor(Color.parseColor("#2C2C35"));
            chip.setPadding(16, 10, 16, 10);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            chipParams.setMargins(0, 0, 12, 0);
            chip.setLayoutParams(chipParams);

            if (attachment.getThumbnail() != null) {
                ImageView thumbView = new ImageView(context);
                thumbView.setImageBitmap(attachment.getThumbnail());
                thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                int thumbSize = dpToPx(context, 24);
                LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
                thumbParams.setMargins(0, 0, 12, 0);
                thumbView.setLayoutParams(thumbParams);
                chip.addView(thumbView);
            } else {
                TextView iconView = new TextView(context);
                iconView.setText("📎");
                iconView.setTextSize(14);
                iconView.setPadding(0, 0, 12, 0);
                chip.addView(iconView);
            }

            TextView tvName = new TextView(context);
            String name = attachment.getName();
            if (name.length() > 18) {
                name = name.substring(0, 15) + "...";
            }
            tvName.setText(name);
            tvName.setTextColor(Color.parseColor("#EEEEEE"));
            tvName.setTextSize(13);
            chip.addView(tvName);

            TextView btnRemove = new TextView(context);
            btnRemove.setText(" ✕");
            btnRemove.setTextColor(Color.parseColor("#FF6B6B"));
            btnRemove.setTextSize(14);
            btnRemove.setTypeface(null, Typeface.BOLD);
            btnRemove.setPadding(12, 0, 0, 0);
            btnRemove.setOnClickListener(v -> removePendingAttachment(context, attachment));
            chip.addView(btnRemove);

            chip.setOnClickListener(v -> openAttachmentInImageViewer(attachment));
            mAttachmentsPreviewContainer.addView(chip);
        }
    }

    private void onActionButtonClicked(Context context) {
        if (mChatSession == null) return;
        if (mSkillAutocompleteContainer != null) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
        }

        boolean isLoading = mChatSession.isLoading();
        String text = mMsgInput.getText() != null ? mMsgInput.getText().toString() : "";

        if (isLoading && text.trim().isEmpty() && mPendingAttachments.isEmpty()) {
            // User pressed STOP while prompt is empty and agent is operating
            mChatSession.stopAgentExecution();
            Toast.makeText(context, "Stopping Agent execution...", Toast.LENGTH_SHORT).show();
            updateActionButtonState();
            return;
        }

        boolean hasText = text.trim().length() > 0;
        boolean hasAttachments = !mPendingAttachments.isEmpty();

        if (hasText || hasAttachments) {
            if (!ModelConnectionManager.getInstance().isConnectedAndConfigured(context)) {
                Toast.makeText(context, ModelConnectionManager.getInstance().getConnectionStatusMessage(context), Toast.LENGTH_LONG).show();
            }
            mMsgInput.setText("");
            List<ChatAttachment> attachmentsToSend = new ArrayList<>(mPendingAttachments);
            mPendingAttachments.clear();
            updatePendingAttachmentsUI(context);
            mChatSession.sendMessage(context, text, attachmentsToSend);
            updateActionButtonState();
        }
    }

    private void updateCharCounter() {
        if (mTvCharCounter == null || mMsgInput == null) return;
        int currentLength = mMsgInput.getText() != null ? mMsgInput.getText().length() : 0;
        mTvCharCounter.setText(currentLength + "/" + ChatSession.MAX_INPUT_CHARS + " characters");
        if (currentLength >= ChatSession.MAX_INPUT_CHARS) {
            mTvCharCounter.setTextColor(Color.parseColor("#FF5252"));
        } else if (currentLength >= (int)(ChatSession.MAX_INPUT_CHARS * 0.9)) {
            mTvCharCounter.setTextColor(Color.parseColor("#FF9800"));
        } else {
            mTvCharCounter.setTextColor(Color.parseColor("#707070"));
        }
    }

    private void updateActionButtonState() {
        if (mBtnActionButton == null || mTvActionIcon == null) return;

        boolean isLoading = mChatSession != null && mChatSession.isLoading();
        String text = mMsgInput.getText() != null ? mMsgInput.getText().toString() : "";

        if (isLoading && text.trim().isEmpty() && mPendingAttachments.isEmpty()) {
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
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context != null) {
            updateChangesetBar(context);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            Context context = getContext();
            if (context != null) {
                updateChangesetBar(context);
            }
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Context context = getContext();
            if (context != null) {
                updateChangesetBar(context);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mChatSession != null) {
            mChatSession.removeListener(this);
        }
    }

    private void renderChatHistory(Context context) {
        if (mChatContainer == null || mChatSession == null) return;
        mChatContainer.removeAllViews();

        List<ChatMessage> history = mChatSession.getMessages();
        for (ChatMessage msg : history) {
            addChatMessageUI(context, msg);
        }

        updateLoadingUI(mChatSession.isLoading());
        scrollToBottom();
    }

    private void addChatMessageUI(Context context, ChatMessage msg) {
        if (context == null || mChatContainer == null) return;

        if (ToolMessageFactory.isReasoningMessage(msg)) {
            int childCount = mChatContainer.getChildCount();
            if (childCount > 0) {
                View lastChild = mChatContainer.getChildAt(childCount - 1);
                if (lastChild instanceof com.vibestudio.app.view.ReasoningToolMessage) {
                    ((com.vibestudio.app.view.ReasoningToolMessage) lastChild).appendToolMessage(msg);
                    return;
                }
            }
            com.vibestudio.app.view.ReasoningToolMessage reasoningView = ToolMessageFactory.createReasoningMessageView(context, msg);
            mChatContainer.addView(reasoningView);
            return;
        }

        if (msg.getType() == ChatMessage.MessageType.TOOL_IMAGE) {
            mChatContainer.addView(new com.vibestudio.app.view.ToolImageMessage(context, msg, this::openAttachmentInImageViewer));
            return;
        }


        // Centralized SOLID check and view creation via ToolMessageFactory
        if (ToolMessageFactory.isToolMessage(msg)) {
            int childCount = mChatContainer.getChildCount();
            if (childCount > 0) {
                View lastChild = mChatContainer.getChildAt(childCount - 1);
                if (lastChild instanceof BaseToolMessageView) {
                    BaseToolMessageView lastToolView = (BaseToolMessageView) lastChild;
                    BaseToolMessageView newToolView = ToolMessageFactory.createToolMessageView(context, msg);
                    if (newToolView.getClass().isInstance(lastToolView)) {
                        lastToolView.appendToolMessage(msg);
                        return;
                    }
                }
            }
            BaseToolMessageView toolView = ToolMessageFactory.createToolMessageView(context, msg);
            mChatContainer.addView(toolView);
            return;
        }

        boolean isErrorMsg = msg.isError() || msg.getType() == ChatMessage.MessageType.ERROR;

        if (isErrorMsg) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundResource(R.drawable.bg_error_card);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);

            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setPadding(18, 14, 18, 14);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
            textContainer.setLayoutParams(textParams);

            TextView tvSender = new TextView(context);
            tvSender.setText(msg.getSender());
            tvSender.setTextColor(Color.parseColor("#CF6679"));
            tvSender.setTextSize(12);
            tvSender.setTypeface(null, Typeface.BOLD);
            tvSender.setTextIsSelectable(true);

            TextView tvText = new TextView(context);
            tvText.setText(msg.getText());
            tvText.setTextColor(Color.parseColor("#FFB4AB"));
            tvText.setTextSize(14);
            tvText.setPadding(0, 4, 0, 0);
            tvText.setTextIsSelectable(true);

            textContainer.addView(tvSender);
            textContainer.addView(tvText);
            card.addView(textContainer);

            FrameLayout refreshContainer = new FrameLayout(context);
            LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
            refreshContainer.setLayoutParams(refreshParams);
            refreshContainer.setPadding(24, 0, 24, 0);
            refreshContainer.setFocusable(true);
            refreshContainer.setClickable(true);

            ImageView iconRefresh = new ImageView(context);
            iconRefresh.setImageResource(R.drawable.ic_refresh);
            iconRefresh.setAlpha(0.5f);
            int iconSizePx = (int) (20 * context.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSizePx, iconSizePx);
            iconParams.gravity = Gravity.CENTER;
            iconRefresh.setLayoutParams(iconParams);

            refreshContainer.addView(iconRefresh);

            refreshContainer.setOnClickListener(v -> {
                if (mChatSession != null && !mChatSession.isLoading()) {
                    mChatSession.retry(context);
                    renderChatHistory(context);
                }
            });

            card.addView(refreshContainer);
            mChatContainer.addView(card);
            return;
        }

        // Standard User / Assistant Message Card
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 14, 18, 14);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);

        if (msg.isUser()) {
            params.gravity = Gravity.RIGHT;
            card.setBackgroundColor(Color.parseColor("#3700B3"));
        } else {
            params.gravity = Gravity.LEFT;
            card.setBackgroundColor(Color.parseColor("#25252A"));
        }
        card.setLayoutParams(params);

        TextView tvSender = new TextView(context);
        tvSender.setText(msg.getSender());
        if (msg.isUser()) {
            tvSender.setTextColor(Color.parseColor("#03DAC6"));
        } else {
            tvSender.setTextColor(Color.parseColor("#BB86FC"));
        }
        tvSender.setTextSize(12);
        tvSender.setTypeface(null, Typeface.BOLD);
        tvSender.setTextIsSelectable(true);

        TextView tvText = new TextView(context);
        tvText.setTextColor(Color.parseColor("#FFFFFF"));
        tvText.setTextSize(14);
        tvText.setPadding(0, 4, 0, 0);
        if (msg.isUser()) {
            tvText.setText(msg.getText());
        } else {
            tvText.setLinkTextColor(Color.parseColor("#80D0FF"));
            getMarkwon(context).setMarkdown(tvText, msg.getText());
        }
        tvText.setTextIsSelectable(true);

        card.addView(tvSender);
        card.addView(tvText);

        if (msg.hasAttachments()) {
            HorizontalScrollView attachmentsScroll = new HorizontalScrollView(context);
            attachmentsScroll.setPadding(0, 8, 0, 0);
            LinearLayout attachmentsLayout = new LinearLayout(context);
            attachmentsLayout.setOrientation(LinearLayout.HORIZONTAL);
            attachmentsScroll.addView(attachmentsLayout);

            for (ChatAttachment att : msg.getAttachments()) {
                LinearLayout attChip = new LinearLayout(context);
                attChip.setOrientation(LinearLayout.HORIZONTAL);
                attChip.setGravity(Gravity.CENTER_VERTICAL);
                attChip.setBackgroundColor(Color.parseColor("#1E1E24"));
                attChip.setPadding(12, 8, 12, 8);
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                chipParams.setMargins(0, 0, 8, 0);
                attChip.setLayoutParams(chipParams);

                if (att.getThumbnail() != null) {
                    ImageView thumbView = new ImageView(context);
                    thumbView.setImageBitmap(att.getThumbnail());
                    thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    int thumbSize = dpToPx(context, 20);
                    LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
                    thumbParams.setMargins(0, 0, 8, 0);
                    thumbView.setLayoutParams(thumbParams);
                    attChip.addView(thumbView);
                } else {
                    TextView iconView = new TextView(context);
                    iconView.setText("📎");
                    iconView.setTextSize(12);
                    iconView.setPadding(0, 0, 8, 0);
                    attChip.addView(iconView);
                }

                TextView tvAttName = new TextView(context);
                String name = att.getName();
                if (name.length() > 16) {
                    name = name.substring(0, 13) + "...";
                }
                tvAttName.setText(name);
                tvAttName.setTextColor(Color.parseColor("#CCCCCC"));
                tvAttName.setTextSize(12);
                attChip.addView(tvAttName);

                final ChatAttachment clickAtt = att;
                attChip.setOnClickListener(v -> openAttachmentInImageViewer(clickAtt));
                attachmentsLayout.addView(attChip);
            }
            card.addView(attachmentsScroll);
        }

        mChatContainer.addView(card);
    }

    private Markwon getMarkwon(Context context) {
        if (mMarkwon == null) {
            mMarkwon = Markwon.builder(context.getApplicationContext())
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(context.getApplicationContext()))
                    .build();
        }
        return mMarkwon;
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
    public void onMessageAdded(final ChatMessage message) {
        android.app.Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Context context = getContext();
                    if (context != null) {
                        addChatMessageUI(context, message);
                        updateChangesetBar(context);
                        scrollToBottom();
                    }
                }
            });
        }
    }

    @Override
    public void onResponseLoading(final boolean isLoading) {
        android.app.Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateLoadingUI(isLoading);
                    Context context = getContext();
                    if (context != null) updateChangesetBar(context);
                }
            });
        }
    }
}