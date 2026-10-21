package com.vibestudio.app.git;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vibestudio.app.R;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.fragments.EditorFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;
import java.util.List;

public class GitStatusView extends LinearLayout implements GitManager.GitStateChangeListener {

    private static final String TAG = "GitStatusView";

    private File mRepoDir;
    private OnCloseListener mCloseListener;
    private GitRepositoryState mLastState;

    private EditText mEtCommitMessage;
    private Button mBtnAction;
    private ImageButton mBtnRefresh;
    private View mBtnClose;
    private LinearLayout mLlContainer;

    public interface OnCloseListener {
        void onClose();
    }

    public interface OnStatusUpdatedListener {
        void onStatusUpdated();
    }

    private OnStatusUpdatedListener mStatusUpdatedListener;

    public void setOnStatusUpdatedListener(OnStatusUpdatedListener listener) {
        this.mStatusUpdatedListener = listener;
    }

    public static class GitFileStatus {
        public String status;
        public String filePath;
        public boolean isStaged;
        public int additions;
        public int deletions;

        public GitFileStatus(String status, String filePath, boolean isStaged) {
            this.status = status;
            this.filePath = filePath;
            this.isStaged = isStaged;
        }
    }

    public GitStatusView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public GitStatusView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.fragment_git_status, this, true);

        mEtCommitMessage = findViewById(R.id.et_commit_message);
        mBtnAction = findViewById(R.id.btn_git_action);
        mBtnRefresh = findViewById(R.id.btn_git_refresh);
        mBtnClose = findViewById(R.id.btn_git_close);
        mLlContainer = findViewById(R.id.ll_git_items_container);

        if (mBtnRefresh != null) {
            mBtnRefresh.setOnClickListener(v -> refreshStatus());
        }

        if (mBtnClose != null) {
            mBtnClose.setOnClickListener(v -> {
                if (mCloseListener != null) {
                    mCloseListener.onClose();
                }
            });
        }

        View titleBar = findViewById(R.id.tv_git_title);
        if (titleBar != null && titleBar.getParent() instanceof View) {
            View headerBar = (View) titleBar.getParent();
            final float[] startY = new float[1];
            final float[] initialTransY = new float[1];
            final boolean[] isDragging = new boolean[1];

            headerBar.setOnTouchListener((v, event) -> {
                View target = (getParent() instanceof View) ? (View) getParent() : this;
                int height = target.getHeight();
                if (height <= 0 && target.getParent() instanceof View) {
                    height = ((View) target.getParent()).getHeight();
                }
                if (height <= 0) height = target.getMeasuredHeight();
                if (height <= 0) height = 1000;

                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY[0] = event.getRawY();
                        setTranslationY(0);
                        initialTransY[0] = target.getTranslationY();
                        isDragging[0] = false;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - startY[0];
                        if (Math.abs(dy) > 10) {
                            isDragging[0] = true;
                        }
                        float targetTrans = initialTransY[0] + dy;
                        if (targetTrans < 0) targetTrans = 0;
                        if (targetTrans > height) targetTrans = height;
                        target.setTranslationY(targetTrans);
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        float totalDy = event.getRawY() - startY[0];
                        if (!isDragging[0] && Math.abs(totalDy) < 15) {
                            v.performClick();
                            return true;
                        }
                        float threshold = Math.max(30f, height * 0.15f);
                        if (totalDy > threshold) {
                            if (mCloseListener != null) {
                                mCloseListener.onClose();
                            } else {
                                target.animate().translationY(height).setDuration(200).start();
                            }
                        } else {
                            target.animate().translationY(0).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            });
        }

        if (mBtnAction != null) {
            mBtnAction.setOnClickListener(v -> handleActionButtonClick());
        }

        if (mEtCommitMessage != null) {
            mEtCommitMessage.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateButtonStates();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        updateButtonStates();
    }

    public void setRepoDir(File repoDir) {
        this.mRepoDir = repoDir;
        GitRepositoryState cached = GitManager.getInstance().getCachedState(repoDir);
        if (cached != null) {
            renderState(cached);
        }
        refreshStatus();
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.mCloseListener = listener;
    }

    public void refreshStatus() {
        if (mRepoDir == null || getContext() == null) return;
        GitManager.getInstance().refreshRepositoryState(getContext(), mRepoDir, this::renderState);
    }

    public void renderState(GitRepositoryState state) {
        mLastState = state;
        post(() -> {
            if (mLlContainer == null) return;
            mLlContainer.removeAllViews();

            updateButtonStates();

            if (state == null) {
                TextView tvErr = new TextView(getContext());
                tvErr.setText("Failed to get git status.");
                tvErr.setTextColor(Color.parseColor("#CF6679"));
                tvErr.setPadding(32, 32, 32, 32);
                mLlContainer.addView(tvErr);
                return;
            }

            renderSection("STAGED CHANGES", state.stagedFiles, true);
            renderSection("CHANGES", state.unstagedFiles, false);

            if (mStatusUpdatedListener != null) {
                mStatusUpdatedListener.onStatusUpdated();
            }
        });
    }

    private void updateButtonStates() {
        boolean hasChanges = mLastState != null && mLastState.hasChanges();
        boolean hasCommitMessage = mEtCommitMessage != null && mEtCommitMessage.getText().toString().trim().length() > 0;
        boolean needsSync = mLastState != null && mLastState.needsSync();

        if (mBtnAction != null) {
            if (needsSync) {
                String syncText = "Sync Changes";
                if (mLastState.ahead > 0 || mLastState.behind > 0) {
                    StringBuilder sb = new StringBuilder("Sync Changes (");
                    if (mLastState.ahead > 0) sb.append("↑").append(mLastState.ahead);
                    if (mLastState.behind > 0) {
                        if (mLastState.ahead > 0) sb.append(" ");
                        sb.append("↓").append(mLastState.behind);
                    }
                    sb.append(")");
                    syncText = sb.toString();
                }
                mBtnAction.setText(syncText);
                mBtnAction.setBackgroundColor(Color.parseColor("#2D2D3A"));
                mBtnAction.setEnabled(true);
                mBtnAction.setAlpha(1.0f);
            } else if (hasChanges || hasCommitMessage) {
                mBtnAction.setText("Commit");
                mBtnAction.setBackgroundColor(Color.parseColor("#0E639C"));
                mBtnAction.setEnabled(true);
                mBtnAction.setAlpha(1.0f);
            } else {
                mBtnAction.setText("Sync Changes");
                mBtnAction.setBackgroundColor(Color.parseColor("#2D2D3A"));
                mBtnAction.setEnabled(true);
                mBtnAction.setAlpha(1.0f);
            }
        }
    }

    private void handleActionButtonClick() {
        boolean hasChanges = mLastState != null && mLastState.hasChanges();
        boolean hasCommitMessage = mEtCommitMessage != null && mEtCommitMessage.getText().toString().trim().length() > 0;
        boolean needsSync = mLastState != null && mLastState.needsSync();

        if (needsSync) {
            syncChanges();
        } else if (hasChanges || hasCommitMessage) {
            executeCommit();
        } else {
            syncChanges();
        }
    }

    @Override
    public void onGitStateChanged(GitRepositoryState state) {
        if (mRepoDir != null && state != null && state.repoRoot != null) {
            if (state.repoRoot.getAbsolutePath().equals(mRepoDir.getAbsolutePath())) {
                renderState(state);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && mRepoDir != null) {
            refreshStatus();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE && mRepoDir != null) {
            refreshStatus();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GitManager.getInstance().addStateChangeListener(this);
        if (mRepoDir != null) {
            refreshStatus();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        GitManager.getInstance().removeStateChangeListener(this);
    }

    private void renderSection(String title, List<GitFileStatus> files, boolean isStaged) {
        if (files.isEmpty()) return;

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(32, 24, 32, 8);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(title + " (" + files.size() + ")");
        tvTitle.setTextColor(Color.parseColor("#82AAFF"));
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        header.addView(tvTitle);

        TextView btnActionAll = new TextView(getContext());
        btnActionAll.setText(isStaged ? "− Unstage All" : "+ Stage All");
        btnActionAll.setTextColor(Color.parseColor("#A0A0C0"));
        btnActionAll.setTextSize(12);
        btnActionAll.setPadding(16, 8, 16, 8);
        btnActionAll.setClickable(true);
        btnActionAll.setFocusable(true);
        btnActionAll.setOnClickListener(v -> {
            String cmd = "pull && git --no-pager push";
            GitManager.getInstance().runGitCommandAsync(getContext(), cmd, mRepoDir, (success, output) -> refreshStatus());
        });
        header.addView(btnActionAll);

        mLlContainer.addView(header);

        for (GitFileStatus file : files) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(32, 16, 32, 16);

            TextView tvStatus = new TextView(getContext());
            tvStatus.setText(file.status);
            tvStatus.setTextSize(12);
            tvStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

            if (file.status.contains("M")) {
                tvStatus.setTextColor(Color.parseColor("#E2C08D"));
            } else if (file.status.contains("A") || file.status.contains("?")) {
                tvStatus.setTextColor(Color.parseColor("#73C991"));
            } else if (file.status.contains("D")) {
                tvStatus.setTextColor(Color.parseColor("#FF6B6B"));
            } else {
                tvStatus.setTextColor(Color.parseColor("#A0A0A0"));
            }

            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            statusParams.setMarginEnd(24);
            tvStatus.setLayoutParams(statusParams);
            row.addView(tvStatus);

            TextView tvPath = new TextView(getContext());
            tvPath.setText(file.filePath);
            tvPath.setTextColor(Color.parseColor("#E0E0E0"));
            tvPath.setTextSize(13);
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
            tvPath.setLayoutParams(pathParams);
            row.addView(tvPath);

            LinearLayout llChanges = new LinearLayout(getContext());
            llChanges.setOrientation(HORIZONTAL);
            llChanges.setGravity(Gravity.CENTER_VERTICAL);

            if (file.additions > 0) {
                TextView tvAdd = new TextView(getContext());
                tvAdd.setText("+" + file.additions);
                tvAdd.setTextColor(Color.parseColor("#73C991"));
                tvAdd.setTextSize(12);
                tvAdd.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                lp.setMarginEnd(12);
                tvAdd.setLayoutParams(lp);
                llChanges.addView(tvAdd);
            }

            if (file.deletions > 0) {
                TextView tvDel = new TextView(getContext());
                tvDel.setText("-" + file.deletions);
                tvDel.setTextColor(Color.parseColor("#FF6B6B"));
                tvDel.setTextSize(12);
                tvDel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                lp.setMarginEnd(12);
                tvDel.setLayoutParams(lp);
                llChanges.addView(tvDel);
            }

            row.addView(llChanges);

            row.setClickable(true);
            row.setFocusable(true);
            TypedValue outValueRow = new TypedValue();
            if (getContext() != null && getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValueRow, true)) {
                row.setBackgroundResource(outValueRow.resourceId);
            }
            row.setOnClickListener(v -> {
                File fullFile = new File(mRepoDir, file.filePath);
                if ("A".equals(file.status) || "??".equals(file.status) || "?".equals(file.status)) {
                    try {
                        File emptyTemp = File.createTempFile("git_empty_", "_" + fullFile.getName(), getContext().getCacheDir());
                        emptyTemp.deleteOnExit();
                        openDiffView(emptyTemp.getAbsolutePath(), fullFile.getAbsolutePath());
                    } catch (IOException e) {
                        openFileInEditor(fullFile.getAbsolutePath());
                    }
                } else if ("D".equals(file.status)) {
                    try {
                        File tempHeadFile = File.createTempFile("git_head_", "_" + fullFile.getName(), getContext().getCacheDir());
                        tempHeadFile.deleteOnExit();
                        File emptyTemp = File.createTempFile("git_empty_", "_" + fullFile.getName(), getContext().getCacheDir());
                        emptyTemp.deleteOnExit();
                        String showCmd = "show HEAD:" + file.filePath;
                        GitManager.getInstance().runGitCommandAsync(getContext(), showCmd, mRepoDir, (success, output) -> {
                            post(() -> {
                                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempHeadFile))) {
                                    writer.write(success ? output : "");
                                } catch (IOException ignored) {}
                                openDiffView(tempHeadFile.getAbsolutePath(), emptyTemp.getAbsolutePath());
                            });
                        });
                    } catch (IOException e) {
                        openFileInEditor(fullFile.getAbsolutePath());
                    }
                } else {
                    try {
                        File tempHeadFile = File.createTempFile("git_head_", "_" + fullFile.getName(), getContext().getCacheDir());
                        tempHeadFile.deleteOnExit();
                        String showCmd = file.isStaged ? "show HEAD:" + file.filePath : "show :0:" + file.filePath;
                        GitManager.getInstance().runGitCommandAsync(getContext(), showCmd, mRepoDir, (success, output) -> {
                            post(() -> {
                                final String baseContent = (success && output != null) ? output : "";
                                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempHeadFile))) {
                                    writer.write(baseContent);
                                } catch (IOException ignored) {}
                                openDiffView(tempHeadFile.getAbsolutePath(), fullFile.getAbsolutePath());
                            });
                        });
                    } catch (IOException e) {
                        openFileInEditor(fullFile.getAbsolutePath());
                    }
                }
            });

            TypedValue outValueBtn = new TypedValue();
            int borderlessId = 0;
            if (getContext() != null && getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValueBtn, true)) {
                borderlessId = outValueBtn.resourceId;
            }

            TextView btnStageUnstage = new TextView(getContext());
            btnStageUnstage.setText(isStaged ? "−" : "+");
            btnStageUnstage.setTextColor(Color.parseColor("#82AAFF"));
            btnStageUnstage.setTextSize(18);
            btnStageUnstage.setTypeface(null, Typeface.BOLD);
            btnStageUnstage.setPadding(16, 8, 16, 8);
            btnStageUnstage.setClickable(true);
            btnStageUnstage.setFocusable(true);
            if (borderlessId != 0) btnStageUnstage.setBackgroundResource(borderlessId);
            btnStageUnstage.setOnClickListener(v -> {
                String cmd = "pull && git --no-pager push";
                GitManager.getInstance().runGitCommandAsync(getContext(), cmd, mRepoDir, (success, output) -> refreshStatus());
            });
            row.addView(btnStageUnstage);

            ImageButton btnRevertFile = new ImageButton(getContext());
            btnRevertFile.setImageResource(R.drawable.ic_undo);
            btnRevertFile.setColorFilter(Color.parseColor("#CF6679"));
            btnRevertFile.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            if (borderlessId != 0) btnRevertFile.setBackgroundResource(borderlessId);
            else btnRevertFile.setBackgroundResource(android.R.color.transparent);
            btnRevertFile.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

            int iconSize = dpToPx(28);
            LinearLayout.LayoutParams revertParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            revertParams.setMarginStart(dpToPx(16));
            btnRevertFile.setLayoutParams(revertParams);
            btnRevertFile.setContentDescription("Revert file changes");
            btnRevertFile.setOnClickListener(v -> showRevertFileDialog(file));
            row.addView(btnRevertFile);

            mLlContainer.addView(row);
        }
    }

    private void openDiffView(String file1, String file2) {
        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).openDiffInEditor(file1, file2);
        } else {
            EditorFragment frag = EditorFragment.newDiffInstance(file1, file2);
            String title = "Diff: " + new File(file1).getName() + " ↔ " + new File(file2).getName();
            TabManager.getInstance().openTab(TabType.EDITOR, title, "🔀", frag, true);
        }
    }

    private void openFileInEditor(String path) {
        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).openFileInEditor(path);
        } else {
            EditorFragment frag = EditorFragment.newInstance(path);
            TabManager.getInstance().openTab(TabType.EDITOR, new File(path).getName(), "📝", frag, true);
        }
    }

    private void executeCommit() {
        if (mEtCommitMessage == null) return;
        String msg = mEtCommitMessage.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a commit message", Toast.LENGTH_SHORT).show();
            return;
        }

        String safeMsg = msg.replace("'", "'\\''");
        GitManager.getInstance().runGitCommandAsync(getContext(), "commit -m '" + safeMsg + "'", mRepoDir, (success, output) -> {
            post(() -> {
                if (success) {
                    Toast.makeText(getContext(), "Commit successful", Toast.LENGTH_SHORT).show();
                    mEtCommitMessage.setText("");
                    refreshStatus();
                } else {
                    Toast.makeText(getContext(), "Commit failed: " + output, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void syncChanges() {
        if (mRepoDir == null || getContext() == null) return;
        Toast.makeText(getContext(), "Syncing changes...", Toast.LENGTH_SHORT).show();
        GitManager.getInstance().syncRepository(getContext(), mRepoDir, (success, output) -> {
            post(() -> {
                if (success) {
                    Toast.makeText(getContext(), "Sync successful", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Sync output: " + output, Toast.LENGTH_LONG).show();
                }
                refreshStatus();
            });
        });
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showRevertFileDialog(GitFileStatus file) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Revert File")
                .setMessage("Are you sure you want to revert changes in " + file.filePath + "?")
                .setPositiveButton("Revert", (dialog, which) -> {
                    String cmd;
                    if ("??".equals(file.status)) {
                        cmd = "clean -f '" + file.filePath + "'";
                    } else if (file.isStaged) {
                        cmd = "checkout HEAD -- '" + file.filePath + "'";
                    } else {
                        cmd = "checkout -- '" + file.filePath + "'";
                    }
                    GitManager.getInstance().runGitCommandAsync(getContext(), cmd, mRepoDir, (success, output) -> refreshStatus());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
