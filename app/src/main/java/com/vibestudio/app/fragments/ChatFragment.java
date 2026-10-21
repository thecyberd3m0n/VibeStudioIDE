package com.vibestudio.app.fragments;

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

    private ScrollView mChatScroll;
    private LinearLayout mChatContainer;
    private EditText mMsgInput;
    private FrameLayout mBtnActionButton;
    private TextView mTvActionIcon;
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
            }
        });

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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
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

        inputRow.addView(mMsgInput);
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

    private void onActionButtonClicked(Context context) {
        if (mChatSession == null) return;
        if (mSkillAutocompleteContainer != null) {
            mSkillAutocompleteContainer.setVisibility(View.GONE);
        }

        boolean isLoading = mChatSession.isLoading();
        String text = mMsgInput.getText() != null ? mMsgInput.getText().toString() : "";

        if (isLoading && text.trim().isEmpty()) {
            // User pressed STOP while prompt is empty and agent is operating
            mChatSession.stopAgentExecution();
            Toast.makeText(context, "Stopping Agent execution...", Toast.LENGTH_SHORT).show();
            updateActionButtonState();
            return;
        }

        if (text.trim().length() > 0) {
            if (!ModelConnectionManager.getInstance().isConnectedAndConfigured(context)) {
                Toast.makeText(context, ModelConnectionManager.getInstance().getConnectionStatusMessage(context), Toast.LENGTH_LONG).show();
            }
            mMsgInput.setText("");
            mChatSession.sendMessage(context, text);
            updateActionButtonState();
        }
    }

    private void updateActionButtonState() {
        if (mBtnActionButton == null || mTvActionIcon == null) return;

        boolean isLoading = mChatSession != null && mChatSession.isLoading();
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
            addChatMessageUI(context, message);
            updateChangesetBar(context);
            scrollToBottom();
        }
    }

    @Override
    public void onResponseLoading(boolean isLoading) {
        updateLoadingUI(isLoading);
            Context context = getContext();
            if (context != null) updateChangesetBar(context);
    }
}
