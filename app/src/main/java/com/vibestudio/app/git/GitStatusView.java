package com.vibestudio.app.git;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
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
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vibestudio.app.R;
import com.vibestudio.app.service.LogViewerService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GitStatusView extends LinearLayout {

    private static final String TAG = "GitStatusView";

    private File mRepoDir;
    private OnCloseListener mCloseListener;

    private EditText mEtCommitMessage;
    private Button mBtnCommit;
    private Button mBtnPush;
    private Button mBtnPull;
    private ImageButton mBtnRefresh;
    private ImageButton mBtnClose;
    private LinearLayout mLlContainer;

    public interface OnCloseListener {
        void onClose();
    }

    public static class GitFileStatus {
        public String status; // e.g. "M", "A", "D", "??", "MM", etc.
        public String filePath;
        public boolean isStaged;

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
        mBtnCommit = findViewById(R.id.btn_git_commit);
        mBtnPush = findViewById(R.id.btn_git_push);
        mBtnPull = findViewById(R.id.btn_git_pull);
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

        if (mBtnCommit != null) {
            mBtnCommit.setOnClickListener(v -> executeCommit());
        }

        if (mBtnPush != null) {
            mBtnPush.setOnClickListener(v -> executePush());
        }

        if (mBtnPull != null) {
            mBtnPull.setOnClickListener(v -> executePull());
        }
    }

    public void setRepoDir(File repoDir) {
        this.mRepoDir = repoDir;
        refreshStatus();
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.mCloseListener = listener;
    }

    public void refreshStatus() {
        if (mRepoDir == null || getContext() == null) return;

        GitManager.getInstance().runGitCommandAsync(getContext(), "status --porcelain", mRepoDir, (success, output) -> {
            post(() -> {
                if (mLlContainer == null) return;
                mLlContainer.removeAllViews();

                if (!success || output == null) {
                    TextView tvErr = new TextView(getContext());
                    tvErr.setText("Failed to get git status.");
                    tvErr.setTextColor(Color.parseColor("#CF6679"));
                    tvErr.setPadding(32, 32, 32, 32);
                    mLlContainer.addView(tvErr);
                    return;
                }

                List<GitFileStatus> stagedFiles = new ArrayList<>();
                List<GitFileStatus> unstagedFiles = new ArrayList<>();

                String[] lines = output.split("\n");
                for (String line : lines) {
                    if (line.length() < 3) continue;
                    String statusIndex = line.substring(0, 1);
                    String statusWorktree = line.substring(1, 2);
                    String filePath = line.substring(3).trim();

                    if (!" ".equals(statusIndex) && !"?".equals(statusIndex)) {
                        stagedFiles.add(new GitFileStatus(statusIndex, filePath, true));
                    }
                    if (!" ".equals(statusWorktree)) {
                        unstagedFiles.add(new GitFileStatus(statusWorktree.equals("?") ? "??" : statusWorktree, filePath, false));
                    }
                }

                renderSection("STAGED CHANGES", stagedFiles, true);
                renderSection("CHANGES", unstagedFiles, false);
            });
        });
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
            String cmd = isStaged ? "restore --staged ." : "add .";
            GitManager.getInstance().runGitCommandAsync(getContext(), cmd, mRepoDir, (success, output) -> post(this::refreshStatus));
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

            TextView btnStageUnstage = new TextView(getContext());
            btnStageUnstage.setText(isStaged ? "−" : "+");
            btnStageUnstage.setTextColor(Color.parseColor("#82AAFF"));
            btnStageUnstage.setTextSize(16);
            btnStageUnstage.setTypeface(null, Typeface.BOLD);
            btnStageUnstage.setPadding(24, 8, 16, 8);
            btnStageUnstage.setClickable(true);
            btnStageUnstage.setFocusable(true);
            btnStageUnstage.setOnClickListener(v -> {
                String cmd = isStaged ? "restore --staged '" + file.filePath + "'" : "add '" + file.filePath + "'";
                GitManager.getInstance().runGitCommandAsync(getContext(), cmd, mRepoDir, (success, output) -> post(this::refreshStatus));
            });
            row.addView(btnStageUnstage);

            mLlContainer.addView(row);
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

    private void executePush() {
        Toast.makeText(getContext(), "Pushing changes...", Toast.LENGTH_SHORT).show();
        GitManager.getInstance().runGitCommandAsync(getContext(), "push", mRepoDir, (success, output) -> {
            post(() -> {
                if (success) {
                    Toast.makeText(getContext(), "Push successful", Toast.LENGTH_SHORT).show();
                    refreshStatus();
                } else {
                    Toast.makeText(getContext(), "Push output: " + output, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void executePull() {
        Toast.makeText(getContext(), "Pulling changes...", Toast.LENGTH_SHORT).show();
        GitManager.getInstance().runGitCommandAsync(getContext(), "pull", mRepoDir, (success, output) -> {
            post(() -> {
                if (success) {
                    Toast.makeText(getContext(), "Pull successful", Toast.LENGTH_SHORT).show();
                    refreshStatus();
                } else {
                    Toast.makeText(getContext(), "Pull output: " + output, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
