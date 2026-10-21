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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.util.ArrayList;
import java.util.List;

public class GitStatusView extends LinearLayout implements GitManager.GitStateChangeListener {

    private static final String TAG = "GitStatusView";

    private File mRepoDir;
    private OnCloseListener mCloseListener;
    private GitRepositoryState mLastState;

    private TextView mTvBranchName;
    private View mLlBranchSelector;
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

        mLlBranchSelector = findViewById(R.id.ll_branch_selector);
        mTvBranchName = findViewById(R.id.tv_current_branch);
        mEtCommitMessage = findViewById(R.id.et_commit_message);
        mBtnAction = findViewById(R.id.btn_git_action);
        mBtnRefresh = findViewById(R.id.btn_git_refresh);
        mBtnClose = findViewById(R.id.btn_git_close);
        mLlContainer = findViewById(R.id.ll_git_items_container);

        if (mLlBranchSelector != null) {
            mLlBranchSelector.setOnClickListener(v -> showBranchSelectionDialog());
        }

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

    @Override
    public void onGitStateChanged(GitRepositoryState state) {
        if (mRepoDir != null && state != null && state.repoRoot != null &&
            mRepoDir.getAbsolutePath().equals(state.repoRoot.getAbsolutePath())) {
            renderState(state);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GitManager.getInstance().addStateChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        GitManager.getInstance().removeStateChangeListener(this);
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

            if (mTvBranchName != null) {
                String bName = (state != null && state.branchName != null && !state.branchName.isEmpty()) ? state.branchName : "HEAD";
                mTvBranchName.setText(bName);
            }

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

    private void showBranchSelectionDialog() {
        if (mRepoDir == null || getContext() == null) return;

        Toast.makeText(getContext(), "Fetching branches...", Toast.LENGTH_SHORT).show();

        GitManager.getInstance().runGitCommandAsync(getContext(), "branch -a --format=\"%(refname:short)\"", mRepoDir, (success, output) -> {
            post(() -> {
                if (!success || output == null) {
                    Toast.makeText(getContext(), "Failed to list branches: " + output, Toast.LENGTH_SHORT).show();
                    return;
                }

                String currentBranch = (mLastState != null && mLastState.branchName != null) ? mLastState.branchName : "";
                List<String> rawBranches = new ArrayList<>();
                String[] lines = output.split("\n");
                for (String line : lines) {
                    String b = line.trim();
                    if (b.startsWith("origin/HEAD")) continue;
                    if (!b.isEmpty() && !rawBranches.contains(b)) {
                        rawBranches.add(b);
                    }
                }

                LinearLayout dialogLayout = new LinearLayout(getContext());
                dialogLayout.setOrientation(LinearLayout.VERTICAL);
                dialogLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

                EditText searchBox = new EditText(getContext());
                searchBox.setHint("Search branches...");
                searchBox.setTextSize(13);
                searchBox.setBackgroundColor(Color.parseColor("#22222E"));
                searchBox.setTextColor(Color.WHITE);
                searchBox.setHintTextColor(Color.parseColor("#606070"));
                searchBox.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
                dialogLayout.addView(searchBox);

                ListView listView = new ListView(getContext());
                listView.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(320)));

                List<String> displayItems = new ArrayList<>();
                List<String> branchValues = new ArrayList<>();

                Runnable updateList = () -> {
                    String filter = searchBox.getText().toString().trim().toLowerCase();
                    displayItems.clear();
                    branchValues.clear();

                    if (filter.isEmpty()) {
                        displayItems.add("+ Create New Branch...");
                        branchValues.add("__CREATE_NEW__");
                    }

                    for (String b : rawBranches) {
                        if (filter.isEmpty() || b.toLowerCase().contains(filter)) {
                            if (b.equals(currentBranch)) {
                                displayItems.add("✓ " + b + " (current)");
                            } else {
                                displayItems.add(b);
                            }
                            branchValues.add(b);
                        }
                    }
                };

                updateList.run();

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        getContext(), android.R.layout.simple_list_item_1, displayItems) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(Color.parseColor("#E0E0E0"));
                        tv.setTextSize(13);
                        tv.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                        return tv;
                    }
                };

                listView.setAdapter(adapter);
                dialogLayout.addView(listView);

                searchBox.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        updateList.run();
                        adapter.notifyDataSetChanged();
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });

                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setTitle("Git Branches (" + rawBranches.size() + ")")
                        .setView(dialogLayout)
                        .setNegativeButton("Cancel", null)
                        .create();

                listView.setOnItemClickListener((parent, view, position, id) -> {
                    dialog.dismiss();
                    String selectedVal = branchValues.get(position);
                    if ("__CREATE_NEW__".equals(selectedVal)) {
                        showCreateBranchDialog();
                    } else {
                        showBranchActionMenu(selectedVal, currentBranch);
                    }
                });

                dialog.show();
            });
        });
    }

    private void showCreateBranchDialog() {
        if (mRepoDir == null || getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Create New Branch");

        final EditText input = new EditText(getContext());
        input.setHint("Branch name");
        input.setPadding(32, 24, 32, 24);
        builder.setView(input);

        builder.setPositiveButton("Create & Checkout", (dialog, which) -> {
            String newBranch = input.getText().toString().trim();
            if (!newBranch.isEmpty()) {
                String safeBranch = newBranch.replace("'", "'\\''");
                executeGitAction("checkout -b '" + safeBranch + "'", "Created and switched to " + newBranch);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showBranchActionMenu(String targetBranch, String currentBranch) {
        if (mRepoDir == null || getContext() == null) return;

        boolean isCurrent = targetBranch.equals(currentBranch);
        String safeTarget = targetBranch.replace("'", "'\\''");
        String safeCurrent = currentBranch.replace("'", "'\\''");

        String[] actions = new String[] {
            "Checkout",
            "Merge to Current (" + currentBranch + ")",
            "Merge Current (" + currentBranch + ") to " + targetBranch,
            "Rebase Current onto " + targetBranch,
            "Cherry-Pick Commit"
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Branch: " + targetBranch)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Checkout
                            if (isCurrent) {
                                Toast.makeText(getContext(), "Already on " + targetBranch, Toast.LENGTH_SHORT).show();
                            } else {
                                executeGitAction("checkout '" + safeTarget + "'", "Switched to branch " + targetBranch);
                            }
                            break;

                        case 1: // Merge to Current
                            if (isCurrent) {
                                Toast.makeText(getContext(), "Cannot merge branch into itself", Toast.LENGTH_SHORT).show();
                            } else {
                                executeGitAction("merge '" + safeTarget + "'", "Merged " + targetBranch + " into " + currentBranch);
                            }
                            break;

                        case 2: // Merge Current to Branch
                            if (isCurrent) {
                                Toast.makeText(getContext(), "Cannot merge branch into itself", Toast.LENGTH_SHORT).show();
                            } else {
                                executeGitAction("checkout '" + safeTarget + "' && git merge '" + safeCurrent + "'", "Merged " + currentBranch + " into " + targetBranch);
                            }
                            break;

                        case 3: // Rebase
                            if (isCurrent) {
                                Toast.makeText(getContext(), "Cannot rebase branch onto itself", Toast.LENGTH_SHORT).show();
                            } else {
                                executeGitAction("rebase '" + safeTarget + "'", "Rebased onto " + targetBranch);
                            }
                            break;

                        case 4: // Cherry-Pick
                            showCherryPickDialog();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCherryPickDialog() {
        if (mRepoDir == null || getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Cherry-Pick Commit");

        final EditText input = new EditText(getContext());
        input.setHint("Commit SHA / Hash");
        input.setPadding(32, 24, 32, 24);
        builder.setView(input);

        builder.setPositiveButton("Cherry-Pick", (dialog, which) -> {
            String hash = input.getText().toString().trim();
            if (!hash.isEmpty()) {
                String safeHash = hash.replace("'", "'\\''");
                executeGitAction("cherry-pick '" + safeHash + "'", "Cherry-picked commit " + hash);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void executeGitAction(String command, String successMsg) {
        GitManager.getInstance().runGitCommandAsync(getContext(), command, mRepoDir, (success, output) -> {
            post(() -> {
                if (success) {
                    Toast.makeText(getContext(), successMsg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Action failed: " + output, Toast.LENGTH_LONG).show();
                }
                refreshStatus();
            });
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

        if (hasChanges || hasCommitMessage) {
            executeCommit();
        } else if (needsSync || !hasChanges) {
            syncChanges();
        }
    }

    private void renderSection(String title, List<GitFileStatus> files, boolean isStaged) {
        if (files == null || files.isEmpty()) return;

        TextView sectionTitle = new TextView(getContext());
        sectionTitle.setText(title + " (" + files.size() + ")");
        sectionTitle.setTextColor(Color.parseColor("#A0A0B0"));
        sectionTitle.setTextSize(11);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(4));
        mLlContainer.addView(sectionTitle);

        for (GitFileStatus file : files) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

            TypedValue outValueRow = new TypedValue();
            if (getContext() != null && getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValueRow, true)) {
                row.setBackgroundResource(outValueRow.resourceId);
            }

            TextView tvStatus = new TextView(getContext());
            tvStatus.setText(file.status);
            tvStatus.setTextSize(12);
            tvStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvStatus.setGravity(Gravity.CENTER);

            int statusColor = Color.parseColor("#CCCCCC");
            if (file.status.contains("M")) statusColor = Color.parseColor("#E2C08D");
            else if (file.status.contains("A") || file.status.contains("?")) statusColor = Color.parseColor("#73C991");
            else if (file.status.contains("D")) statusColor = Color.parseColor("#F14C4C");
            tvStatus.setTextColor(statusColor);

            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(dpToPx(24), WRAP_CONTENT);
            row.addView(tvStatus, statusParams);

            TextView tvPath = new TextView(getContext());
            tvPath.setText(file.filePath);
            tvPath.setTextColor(Color.parseColor("#E0E0E0"));
            tvPath.setTextSize(13);
            tvPath.setSingleLine(true);
            tvPath.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
            pathParams.setMarginStart(dpToPx(12));
            row.addView(tvPath, pathParams);

            if (file.additions > 0 || file.deletions > 0) {
                TextView tvDiff = new TextView(getContext());
                String diffStr = "+" + file.additions + " -" + file.deletions;
                tvDiff.setText(diffStr);
                tvDiff.setTextSize(11);
                tvDiff.setTextColor(Color.parseColor("#808090"));
                tvDiff.setPadding(0, 0, dpToPx(8), 0);
                row.addView(tvDiff);
            }

            row.setOnClickListener(v -> {
                if (mRepoDir == null || file.filePath == null) return;
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
                        String showCmd = "show HEAD:" + file.filePath;
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
                String safePath = file.filePath.replace("'", "'\\''");
                String cmd = isStaged ? "reset HEAD -- '" + safePath + "'" : "add -- '" + safePath + "'";
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
            frag.setOnDiffRevertListener(new EditorFragment.OnDiffRevertListener() {
                @Override
                public boolean onAcceptHunk(EditorFragment fragment, EditorFragment.DiffHunk hunk, String f1, String f2) {
                    boolean ok = fragment.defaultApplyHunkAccept(hunk);
                    if (mRepoDir != null && getContext() != null) {
                        GitManager.getInstance().refreshRepositoryState(getContext().getApplicationContext(), mRepoDir, null);
                    }
                    return true;
                }

                @Override
                public boolean onRevertHunk(EditorFragment fragment, EditorFragment.DiffHunk hunk, String f1, String f2) {
                    boolean ok = fragment.defaultApplyHunkRevert(hunk);
                    if (mRepoDir != null && getContext() != null) {
                        GitManager.getInstance().refreshRepositoryState(getContext().getApplicationContext(), mRepoDir, null);
                    }
                    return true;
                }

                @Override
                public boolean onRevertAll(EditorFragment fragment, String f1, String f2) {
                    boolean ok = fragment.defaultApplyRevertAll();
                    if (mRepoDir != null && getContext() != null) {
                        GitManager.getInstance().refreshRepositoryState(getContext().getApplicationContext(), mRepoDir, null);
                    }
                    return true;
                }
            });
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
        String safePath = file.filePath.replace("'", "'\\''");
        new AlertDialog.Builder(getContext())
                .setTitle("Revert File")
                .setMessage("Are you sure you want to revert changes in " + file.filePath + "?")
                .setPositiveButton("Revert", (dialog, which) -> {
                    String cmd;
                    if ("??".equals(file.status)) {
                        cmd = "clean -f -- '" + safePath + "'";
                    } else if (file.isStaged) {
                        cmd = "checkout HEAD -- '" + safePath + "'";
                    } else {
                        cmd = "checkout -- '" + safePath + "'";
                    }
                    GitManager.getInstance().runGitCommandAsync(getContext(), cmd, mRepoDir, (success, output) -> refreshStatus());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
