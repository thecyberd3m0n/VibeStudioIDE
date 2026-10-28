package com.vibestudio.app.fragments;

import android.content.Context;


import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.editor.TextMateManager;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.util.FontUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.ContentChangeEvent;

public class EditorFragment extends Fragment {

    private static final String TAG = "EditorFragment";
    private static final String ARG_FILE_PATH = "file_path";
    private static final String ARG_FILE1_PATH = "file1_path";
    private static final String ARG_FILE2_PATH = "file2_path";
    private static final String ARG_IS_DIFF_MODE = "is_diff_mode";

    private CodeEditor mCodeEditor;
    private TextView mTvFilePath;
    private Button mBtnSave;
    private ImageButton mBtnUndo;
    private ImageButton mBtnMore;
    private FrameLayout mFlOverlayContainer;

    private String mFilePath;
    private String mFile1Path;
    private String mFile2Path;
    private boolean mIsDiffMode = false;

    private String mSavedContent = "";
    private boolean mIsLoading = false;
    private boolean mIsSaving = false;
    private boolean mIsModified = false;
    private boolean mHasTrailingNewline = false;

    private final List<DiffHunk> mDiffHunks = new ArrayList<>();
    private List<DiffLine> mCurrentDiffLines = new ArrayList<>();
    private final List<View> mBgOverlayViews = new ArrayList<>();
    private final List<View> mBtnOverlayViews = new ArrayList<>();

    public interface OnDiffRevertListener {
        boolean onAcceptHunk(EditorFragment fragment, DiffHunk hunk, String file1Path, String file2Path);
        boolean onRevertHunk(EditorFragment fragment, DiffHunk hunk, String file1Path, String file2Path);
        boolean onRevertAll(EditorFragment fragment, String file1Path, String file2Path);
    }

    private OnDiffRevertListener mOnDiffRevertListener;

    public void setOnDiffRevertListener(OnDiffRevertListener listener) {
        this.mOnDiffRevertListener = listener;
    }


    public static class HunkOption {
        public static final String OPTION_ACCEPT = "accept";
        public static final String OPTION_REVERT = "revert";

        private final String id;
        private final String label;
        private final int iconResId;
        private final int iconTintColor;
        private final OnHunkOptionClickListener listener;

        public interface OnHunkOptionClickListener {
            boolean onOptionClick(EditorFragment fragment, HunkOption option, DiffHunk hunk, String file1Path, String file2Path);
        }

        public HunkOption(String id, String label, int iconResId, int iconTintColor, OnHunkOptionClickListener listener) {
            this.id = id;
            this.label = label;
            this.iconResId = iconResId;
            this.iconTintColor = iconTintColor;
            this.listener = listener;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public int getIconResId() { return iconResId; }
        public int getIconTintColor() { return iconTintColor; }
        public OnHunkOptionClickListener getListener() { return listener; }
    }

    private List<HunkOption> mCustomHunkOptions;

    public void setHunkOptions(List<HunkOption> options) {
        this.mCustomHunkOptions = options;
        if (mIsDiffMode && mCodeEditor != null) {
            rebuildOverlayViews();
        }
    }

    public List<HunkOption> getHunkOptions() {
        if (mCustomHunkOptions != null) {
            return mCustomHunkOptions;
        }
        List<HunkOption> defaults = new ArrayList<>();
        defaults.add(new HunkOption(HunkOption.OPTION_ACCEPT, "Accept Hunk", R.drawable.ic_check, Color.parseColor("#A6E3A1"), null));
        defaults.add(new HunkOption(HunkOption.OPTION_REVERT, "Revert Hunk", R.drawable.ic_undo, Color.parseColor("#CF6679"), null));
        return defaults;
    }
    public static class DiffHunk {
        public int line1Start; // 0-based start in base file
        public int line1Count; // line count in base file
        public int line2Start; // 0-based start in modified file
        public int line2Count; // line count in modified file
        public int editorStartLine; // 0-based line index in diff editor view
        public List<String> deletedLines = new ArrayList<>();
        public List<String> addedLines = new ArrayList<>();

        public DiffHunk(int line2Start, int line2Count, int editorStartLine) {
            this(0, 0, line2Start, line2Count, editorStartLine);
        }

        public DiffHunk(int line1Start, int line1Count, int line2Start, int line2Count, int editorStartLine) {
            this.line1Start = line1Start;
            this.line1Count = line1Count;
            this.line2Start = line2Start;
            this.line2Count = line2Count;
            this.editorStartLine = editorStartLine;
        }
    }

    public static class DiffLine {
        public enum Type { UNCHANGED, ADDED, DELETED, HEADER }
        public Type type;
        public String text;

        public DiffLine(Type type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    public static EditorFragment newInstance() {
        return new EditorFragment();
    }

    public static EditorFragment newInstance(String filePath) {
        EditorFragment fragment = new EditorFragment();
        fragment.mFilePath = filePath;
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        fragment.setArguments(args);
        return fragment;
    }

    public static EditorFragment newDiffInstance(String file1Path, String file2Path) {
        EditorFragment fragment = new EditorFragment();
        fragment.mFile1Path = file1Path;
        fragment.mFile2Path = file2Path;
        fragment.mIsDiffMode = true;
        Bundle args = new Bundle();
        args.putString(ARG_FILE1_PATH, file1Path);
        args.putString(ARG_FILE2_PATH, file2Path);
        args.putBoolean(ARG_IS_DIFF_MODE, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mFilePath = getArguments().getString(ARG_FILE_PATH);
            mFile1Path = getArguments().getString(ARG_FILE1_PATH);
            mFile2Path = getArguments().getString(ARG_FILE2_PATH);
            mIsDiffMode = getArguments().getBoolean(ARG_IS_DIFF_MODE, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_editor, container, false);
        mCodeEditor = view.findViewById(R.id.code_editor);
        mTvFilePath = view.findViewById(R.id.tv_file_path);
        mBtnSave = view.findViewById(R.id.btn_save);
        mBtnUndo = view.findViewById(R.id.btn_undo);
        mBtnMore = view.findViewById(R.id.btn_more);

        if (mBtnMore != null) {
            mBtnMore.setOnClickListener(v -> showContextMenu(v));
        }
        mFlOverlayContainer = view.findViewById(R.id.overlay_hunks_container);

        if (mBtnSave != null) {
            mBtnSave.setOnClickListener(v -> performSave());
        }

        updateFilePathUI();
        updateSaveButtonState(false);

        if (mCodeEditor != null) {
            SchemeDarcula darkScheme = new SchemeDarcula();
            darkScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E2E);
            darkScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF181825);
            mCodeEditor.setColorScheme(darkScheme);

            Typeface monoTypeface = FontUtils.getMonospaceTypeface(getContext());
            mCodeEditor.setTypefaceText(monoTypeface);
            mCodeEditor.setTypefaceLineNumber(monoTypeface);

            if (mIsDiffMode) {
                if (mBtnSave != null) mBtnSave.setVisibility(View.GONE);
                if (mBtnUndo != null) {
                    mBtnUndo.setVisibility(View.VISIBLE);
                    mBtnUndo.setOnClickListener(v -> showRevertAllDialog());
                }
                mCodeEditor.setTextSize(14f);
                mCodeEditor.setEditable(false);

                setupScrollSync();
                loadDiffContent(mFile1Path, mFile2Path);
            } else {
                if (mBtnUndo != null) mBtnUndo.setVisibility(View.GONE);
                mCodeEditor.setText("");
                mCodeEditor.setTextSize(14f);

                if (mFilePath != null && !mFilePath.isEmpty()) {
                    loadFileContent(mFilePath);
                }

                mCodeEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
                    if (!mIsLoading && !mIsSaving && mCodeEditor != null) {
                        String current = mCodeEditor.getText().toString();
                        boolean modified = (mFilePath != null && !mFilePath.isEmpty())
                                ? !current.equals(mSavedContent)
                                : !current.isEmpty();
                        runOnMainThread(() -> updateSaveButtonState(modified));
                    }
                });
            }
        }
        return view;
    }

    public void rebuildOverlayViews() {
        if (mFlOverlayContainer == null || getContext() == null) return;
        mFlOverlayContainer.removeAllViews();
        mBgOverlayViews.clear();
        mBtnOverlayViews.clear();

        if (!mIsDiffMode || mCodeEditor == null) return;

        int lineHeight = mCodeEditor.getRowHeight();
        if (lineHeight <= 0) {
            lineHeight = dpToPx(20);
            if (lineHeight <= 0) lineHeight = dpToPx(20);
        }

        // 1. Pre-create background highlight views for added and deleted lines
        if (mCurrentDiffLines != null) {
            for (int i = 0; i < mCurrentDiffLines.size(); i++) {
                DiffLine line = mCurrentDiffLines.get(i);
                if (line.type == DiffLine.Type.ADDED || line.type == DiffLine.Type.DELETED) {
                    View bgView = new View(getContext());
                    bgView.setClickable(false);
                    bgView.setFocusable(false);
                    int color = (line.type == DiffLine.Type.ADDED) ? 0x602E7D32 : 0x60C62828;
                    bgView.setBackgroundColor(color);

                    FrameLayout.LayoutParams bgLp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, lineHeight);
                    bgView.setLayoutParams(bgLp);
                    bgView.setVisibility(View.GONE);
                    mFlOverlayContainer.addView(bgView);
                    mBgOverlayViews.add(bgView);
                }
            }
        }

        // 2. Pre-create declarable hunk option buttons for each diff hunk
        List<HunkOption> options = getHunkOptions();
        int btnSize = dpToPx(28);
        if (mDiffHunks != null) {
            for (DiffHunk hunk : mDiffHunks) {
                LinearLayout hunkContainer = new LinearLayout(getContext());
                hunkContainer.setOrientation(LinearLayout.HORIZONTAL);
                hunkContainer.setGravity(Gravity.CENTER_VERTICAL);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, btnSize);
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.rightMargin = dpToPx(16);
                hunkContainer.setLayoutParams(lp);
                hunkContainer.setVisibility(View.GONE);

                for (HunkOption option : options) {
                    ImageButton btn = new ImageButton(getContext());
                    btn.setImageResource(option.getIconResId());
                    btn.setColorFilter(option.getIconTintColor());
                    btn.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);

                    if (option.getLabel() != null) {
                        btn.setContentDescription(option.getLabel());
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            btn.setTooltipText(option.getLabel());
                        }
                    }

                    GradientDrawable btnBg = new GradientDrawable();
                    btnBg.setShape(GradientDrawable.RECTANGLE);
                    btnBg.setCornerRadius(dpToPx(14));
                    btnBg.setColor(Color.parseColor("#2A2D3E"));
                    btnBg.setStroke(dpToPx(1), Color.parseColor("#4C4D68"));
                    btn.setBackground(btnBg);

                    btn.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

                    LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(btnSize, btnSize);
                    btnLp.setMargins(0, 0, dpToPx(6), 0);
                    btn.setLayoutParams(btnLp);

                    btn.setOnClickListener(v -> performHunkOption(option, hunk));

                    hunkContainer.addView(btn);
                }

                mFlOverlayContainer.addView(hunkContainer);
                mBtnOverlayViews.add(hunkContainer);
            }
        }

        updateHunkOverlayPositions();
    }

        private void setupScrollSync() {
        if (mCodeEditor == null) return;

        mCodeEditor.subscribeEvent(ScrollEvent.class, (event, unsubscribe) -> updateHunkOverlayPositions());

        mCodeEditor.getViewTreeObserver().addOnScrollChangedListener(this::updateHunkOverlayPositions);

        mCodeEditor.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (mIsDiffMode && mCodeEditor != null) {
                updateHunkOverlayPositions();
            }
            return true;
        });
    }

    public void updateHunkOverlayPositions() {
        if (!mIsDiffMode || mFlOverlayContainer == null || mCodeEditor == null || getContext() == null) {
            return;
        }

        int lineHeight = mCodeEditor.getRowHeight();
        if (lineHeight <= 0) {
            lineHeight = dpToPx(20);
            if (lineHeight <= 0) lineHeight = dpToPx(20);
        }



        int offsetY = mCodeEditor.getOffsetY();
        int containerHeight = mFlOverlayContainer.getHeight();
        if (containerHeight <= 0) containerHeight = mCodeEditor.getHeight();

        // 1. Update line backgrounds via translationY
        if (mCurrentDiffLines != null) {
            int bgIdx = 0;
            for (int i = 0; i < mCurrentDiffLines.size(); i++) {
                DiffLine line = mCurrentDiffLines.get(i);
                if (line.type == DiffLine.Type.ADDED || line.type == DiffLine.Type.DELETED) {
                    if (bgIdx < mBgOverlayViews.size()) {
                        View bgView = mBgOverlayViews.get(bgIdx);
                        ViewGroup.LayoutParams lp = bgView.getLayoutParams();
                        if (lp.height != lineHeight) {
                            lp.height = lineHeight;
                            bgView.setLayoutParams(lp);
                        }

                        int lineTop = mCodeEditor.getRowTop(i) - offsetY;
                        if (containerHeight > 0 && (lineTop + lineHeight < -lineHeight || lineTop > containerHeight + lineHeight)) {
                            bgView.setVisibility(View.GONE);
                        } else {
                            bgView.setVisibility(View.VISIBLE);
                            bgView.setTranslationY(lineTop);
                            bgView.setTranslationX(0);
                        }
                    }
                    bgIdx++;
                }
            }
        }

        // 2. Update hunk option containers via translationY with vertical centering across ALL lines in the hunk
        int btnSize = dpToPx(28);
        if (mDiffHunks != null) {
            int btnIdx = 0;
            for (DiffHunk hunk : mDiffHunks) {
                if (btnIdx < mBtnOverlayViews.size()) {
                    View container = mBtnOverlayViews.get(btnIdx);
                    int totalHunkLines = hunk.deletedLines.size() + hunk.addedLines.size();
                    if (totalHunkLines <= 0) totalHunkLines = 1;

                    int hunkTop = mCodeEditor.getRowTop(hunk.editorStartLine) - offsetY;
                    int totalHunkHeight = totalHunkLines * lineHeight;
                    float btnTop = hunkTop + (totalHunkHeight - btnSize) / 2f;

                    if (containerHeight > 0 && (hunkTop + totalHunkHeight < -lineHeight || hunkTop > containerHeight + lineHeight)) {
                        container.setVisibility(View.GONE);
                    } else {
                        container.setVisibility(View.VISIBLE);
                        container.setTranslationY(btnTop);
                    }
                }
                btnIdx++;
            }
        }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showRevertAllDialog() {
        if (getContext() == null || !mIsDiffMode || mFile2Path == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Revert File")
                .setMessage("Do you want to revert this file?")
                .setPositiveButton("Revert", (dialog, which) -> revertAllToFile1())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void revertAllToFile1() {
        if (!mIsDiffMode || mFile1Path == null || mFile2Path == null) return;

        if (mOnDiffRevertListener != null) {
            boolean handled = mOnDiffRevertListener.onRevertAll(this, mFile1Path, mFile2Path);
            if (handled) return;
        }

        defaultApplyRevertAll();
    }

    public void performHunkOption(HunkOption option, DiffHunk hunk) {
        if (option == null || hunk == null || !mIsDiffMode) return;

        if (option.getListener() != null) {
            boolean handled = option.getListener().onOptionClick(this, option, hunk, mFile1Path, mFile2Path);
            if (handled) return;
        }

        if (HunkOption.OPTION_ACCEPT.equalsIgnoreCase(option.getId())) {
            acceptSingleHunk(hunk);
        } else if (HunkOption.OPTION_REVERT.equalsIgnoreCase(option.getId())) {
            revertSingleHunk(hunk);
        }
    }

    private void acceptSingleHunk(DiffHunk hunk) {
        if (!mIsDiffMode || mFile1Path == null || hunk == null) return;

        if (mOnDiffRevertListener != null) {
            boolean handled = mOnDiffRevertListener.onAcceptHunk(this, hunk, mFile1Path, mFile2Path);
            if (handled) return;
        }

        defaultApplyHunkAccept(hunk);
    }

    public boolean defaultApplyHunkAccept(DiffHunk hunk) {
        if (!mIsDiffMode || mFile1Path == null || hunk == null) return false;

        File file1 = new File(mFile1Path);
        if (!file1.exists()) return false;

        try {
            List<String> lines1 = readLinesFromFile(mFile1Path);

            int start = hunk.line1Start;
            int countToRemove = hunk.line1Count;

            if (start < 0) start = 0;
            if (start > lines1.size()) start = lines1.size();

            for (int i = 0; i < countToRemove && start < lines1.size(); i++) {
                lines1.remove(start);
            }

            for (int i = 0; i < hunk.addedLines.size(); i++) {
                int insertIndex = start + i;
                if (insertIndex >= 0 && insertIndex <= lines1.size()) {
                    lines1.add(insertIndex, hunk.addedLines.get(i));
                } else {
                    lines1.add(hunk.addedLines.get(i));
                }
            }

            boolean file1HadTrailing = checkTrailingNewline(file1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines1.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines1.get(i));
            }
            if (file1HadTrailing && sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append("\n");
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file1))) {
                writer.write(sb.toString());
            }

            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Accepted change", Toast.LENGTH_SHORT).show();
            }

            loadDiffContent(mFile1Path, mFile2Path);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to accept diff hunk", e);
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Failed to accept: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    private void revertSingleHunk(DiffHunk hunk) {
        if (!mIsDiffMode || mFile2Path == null || hunk == null) return;

        if (mOnDiffRevertListener != null) {
            boolean handled = mOnDiffRevertListener.onRevertHunk(this, hunk, mFile1Path, mFile2Path);
            if (handled) return;
        }

        defaultApplyHunkRevert(hunk);
    }

    public boolean defaultApplyHunkRevert(DiffHunk hunk) {
        if (!mIsDiffMode || mFile2Path == null || hunk == null) return false;

        File file2 = new File(mFile2Path);
        if (!file2.exists()) return false;

        try {
            List<String> lines2 = readLinesFromFile(mFile2Path);

            int start = hunk.line2Start;
            int countToRemove = hunk.line2Count;

            if (start < 0) start = 0;
            if (start > lines2.size()) start = lines2.size();

            for (int i = 0; i < countToRemove && start < lines2.size(); i++) {
                lines2.remove(start);
            }

            for (int i = 0; i < hunk.deletedLines.size(); i++) {
                int insertIndex = start + i;
                if (insertIndex >= 0 && insertIndex <= lines2.size()) {
                    lines2.add(insertIndex, hunk.deletedLines.get(i));
                } else {
                    lines2.add(hunk.deletedLines.get(i));
                }
            }

            boolean file2HadTrailing = checkTrailingNewline(file2);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines2.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines2.get(i));
            }
            if (file2HadTrailing && sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append("\n");
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file2))) {
                writer.write(sb.toString());
            }

            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Reverted change", Toast.LENGTH_SHORT).show();
            }

            loadDiffContent(mFile1Path, mFile2Path);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revert diff hunk", e);
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Failed to revert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    public boolean defaultApplyRevertAll() {
        if (!mIsDiffMode || mFile1Path == null || mFile2Path == null) return false;

        File file1 = new File(mFile1Path);
        File file2 = new File(mFile2Path);
        if (!file1.exists() || !file2.exists()) return false;

        try {
            boolean file1HadTrailing = checkTrailingNewline(file1);
            List<String> baseLines = readLinesFromFile(mFile1Path);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < baseLines.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(baseLines.get(i));
            }
            if (file1HadTrailing && sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append("\n");
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file2))) {
                writer.write(sb.toString());
            }
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Reverted file", Toast.LENGTH_SHORT).show();
            }
            loadDiffContent(mFile1Path, mFile2Path);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revert file", e);
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Failed to revert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else if (getActivity() != null) {
            getActivity().runOnUiThread(runnable);
        }
    }

    private void updateFilePathUI() {
        if (mTvFilePath != null) {
            if (mIsDiffMode) {
                String name1 = mFile1Path != null ? new File(mFile1Path).getName() : "HEAD";
                String name2 = mFile2Path != null ? new File(mFile2Path).getName() : "Worktree";
                mTvFilePath.setText("Diff: " + name1 + " ↔ " + name2);
            } else if (mFilePath != null && !mFilePath.isEmpty()) {
                mTvFilePath.setText(mFilePath);
            } else {
                mTvFilePath.setText("No file open");
            }
        }
    }

    private void updateSaveButtonState(boolean modified) {
        mIsModified = modified;
        if (mBtnSave != null) {
            mBtnSave.setEnabled(modified);
            mBtnSave.setAlpha(modified ? 1.0f : 0.4f);
        }
        updateTabTitleWithStar(modified);
    }

    private void updateTabTitleWithStar(boolean modified) {
        TabItem tab = TabManager.getInstance().findTabByFragment(this);
        if (tab == null) return;

        String baseTitle = getBaseTitle(tab);
        String targetTitle = modified ? baseTitle + " *" : baseTitle;

        if (!targetTitle.equals(tab.getTitle())) {
            TabManager.getInstance().updateTabTitle(tab, targetTitle);
        }
    }

    private String getBaseTitle(TabItem tab) {
        if (mIsDiffMode) {
            String name2 = mFile2Path != null ? new File(mFile2Path).getName() : "Diff";
            return "Diff: " + name2;
        }
        if (mFilePath != null && !mFilePath.isEmpty()) {
            return new File(mFilePath).getName();
        }
        String currentTitle = tab.getTitle();
        if (currentTitle != null && currentTitle.endsWith(" *")) {
            return currentTitle.substring(0, currentTitle.length() - 2);
        }
        return currentTitle != null ? currentTitle : "Editor";
    }

    public void performSave() {
        if (mFilePath != null && !mFilePath.isEmpty() && mCodeEditor != null) {
            String currentContent = mCodeEditor.getText().toString();
            saveFileContent(mFilePath, currentContent);
            if (getContext() != null) {
                String fileName = new File(mFilePath).getName();
                Toast.makeText(getContext(), "Saved " + fileName, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void loadDiffContent(String file1Path, String file2Path) {
        this.mFile1Path = file1Path;
        this.mFile2Path = file2Path;
        this.mIsDiffMode = true;
        updateFilePathUI();

        if (mCodeEditor != null && getContext() != null && file2Path != null) {
            TextMateManager.getInstance().applyLanguageAndTheme(getContext(), mCodeEditor, file2Path);
        }

        List<String> lines1 = readLinesFromFile(file1Path);
        List<String> lines2 = readLinesFromFile(file2Path);

        List<DiffLine> diffLines = new ArrayList<>(computeDiff(lines1, lines2));
        mCurrentDiffLines = diffLines;
        extractDiffHunks(diffLines);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diffLines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(diffLines.get(i).text);
        }

        if (mCodeEditor != null) {
            mCodeEditor.setText(sb.toString());
            rebuildOverlayViews();
            mCodeEditor.post(this::updateHunkOverlayPositions);
        }
    }

    public List<DiffLine> computeDiff(List<String> lines1, List<String> lines2) {
        List<DiffLine> result = new ArrayList<>();
        if (lines1 == null) lines1 = Collections.emptyList();
        if (lines2 == null) lines2 = Collections.emptyList();

        int n = lines1.size();
        int m = lines2.size();

        int start = 0;
        while (start < n && start < m && lines1.get(start).equals(lines2.get(start))) {
            result.add(new DiffLine(DiffLine.Type.UNCHANGED, lines1.get(start)));
            start++;
        }

        int end1 = n - 1;
        int end2 = m - 1;
        while (end1 >= start && end2 >= start && lines1.get(end1).equals(lines2.get(end2))) {
            end1--;
            end2--;
        }

        int subN = end1 - start + 1;
        int subM = end2 - start + 1;

        if (subN > 0 || subM > 0) {
            if (subN == 0) {
                for (int j = start; j <= end2; j++) {
                    result.add(new DiffLine(DiffLine.Type.ADDED, lines2.get(j)));
                }
            } else if (subM == 0) {
                for (int i = start; i <= end1; i++) {
                    result.add(new DiffLine(DiffLine.Type.DELETED, lines1.get(i)));
                }
            } else {
                int[][] lcs = new int[subN + 1][subM + 1];
                for (int i = 0; i < subN; i++) {
                    for (int j = 0; j < subM; j++) {
                        if (lines1.get(start + i).equals(lines2.get(start + j))) {
                            lcs[i + 1][j + 1] = lcs[i][j] + 1;
                        } else {
                            lcs[i + 1][j + 1] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                        }
                    }
                }

                int i = subN, j = subM;
                List<DiffLine> reverseDiff = new ArrayList<>();
                while (i > 0 || j > 0) {
                    if (i > 0 && j > 0 && lines1.get(start + i - 1).equals(lines2.get(start + j - 1))) {
                        reverseDiff.add(new DiffLine(DiffLine.Type.UNCHANGED, lines1.get(start + i - 1)));
                        i--;
                        j--;
                    } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                        reverseDiff.add(new DiffLine(DiffLine.Type.ADDED, lines2.get(start + j - 1)));
                        j--;
                    } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
                        reverseDiff.add(new DiffLine(DiffLine.Type.DELETED, lines1.get(start + i - 1)));
                        i--;
                    }
                }
                Collections.reverse(reverseDiff);
                result.addAll(reverseDiff);
            }
        }

        for (int k = end1 + 1; k < n; k++) {
            result.add(new DiffLine(DiffLine.Type.UNCHANGED, lines1.get(k)));
        }

        return result;
    }

    public void extractDiffHunks(List<DiffLine> diffLines) {
        mDiffHunks.clear();

        int line1Index = 0;
        int line2Index = 0;
        int editorLineIndex = 0;

        DiffHunk currentHunk = null;

        for (DiffLine line : diffLines) {
            if (line.type == DiffLine.Type.HEADER) {
                editorLineIndex++;
                continue;
            }

            if (line.type == DiffLine.Type.UNCHANGED) {
                if (currentHunk != null) {
                    mDiffHunks.add(currentHunk);
                    currentHunk = null;
                }
                line1Index++;
                line2Index++;
            } else if (line.type == DiffLine.Type.DELETED) {
                if (currentHunk == null) {
                    currentHunk = new DiffHunk(line1Index, 0, line2Index, 0, editorLineIndex);
                }
                currentHunk.line1Count++;
                currentHunk.deletedLines.add(line.text != null ? line.text : "");
                line1Index++;
            } else if (line.type == DiffLine.Type.ADDED) {
                if (currentHunk == null) {
                    currentHunk = new DiffHunk(line1Index, 0, line2Index, 0, editorLineIndex);
                }
                currentHunk.line2Count++;
                currentHunk.addedLines.add(line.text != null ? line.text : "");
                line2Index++;
            }

            editorLineIndex++;
        }

        if (currentHunk != null) {
            mDiffHunks.add(currentHunk);
        }
    }

    public List<DiffHunk> getDiffHunks() {
        return mDiffHunks;
    }

    public List<String> readLinesFromFile(String filePath) {
        List<String> lines = new ArrayList<>();
        if (filePath == null) return lines;
        File file = new File(filePath);
        if (!file.exists()) return lines;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading lines from file: " + filePath, e);
        }
        return lines;
    }

    public static boolean checkTrailingNewline(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) return false;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(file.length() - 1);
            byte b = raf.readByte();
            return b == '\n';
        } catch (Exception e) {
            return false;
        }
    }

    public void loadFileContent(String filePath) {
        this.mFilePath = filePath;
        updateFilePathUI();
        if (mCodeEditor != null && getContext() != null) {
            TextMateManager.getInstance().applyLanguageAndTheme(getContext(), mCodeEditor, mFilePath);
        }

        File file = new File(filePath);
        mHasTrailingNewline = checkTrailingNewline(file);

        if (!file.exists()) {
            LogViewerService.getInstance().w(TAG, "File does not exist: " + filePath);
            mSavedContent = "";
            if (mCodeEditor != null) {
                mCodeEditor.setText("");
            }
            updateSaveButtonState(false);
            return;
        }

        mIsLoading = true;
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    content.append("\n");
                }
                content.append(line);
                first = false;
            }
            mSavedContent = content.toString();
            if (mCodeEditor != null) {
                mCodeEditor.setText(mSavedContent);
            }
            updateSaveButtonState(false);
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + filePath, e);
            LogViewerService.getInstance().e(TAG, "Failed to load file: " + filePath + " - " + e.getMessage());
        } finally {
            mIsLoading = false;
        }
    }

    public void saveFileContent(String filePath, String content) {
        mIsSaving = true;
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (mHasTrailingNewline && !content.isEmpty() && !content.endsWith("\n")) {
            content = content + "\n";
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
            mSavedContent = content;
            updateSaveButtonState(false);

            // File saved successfully
        } catch (IOException e) {
            Log.e(TAG, "Error writing file: " + filePath, e);
            LogViewerService.getInstance().e(TAG, "Failed to save file: " + filePath + " - " + e.getMessage());
        } finally {
            mIsSaving = false;
        }
    }

    public String getFilePath() {
        return mFilePath;
    }

    public CodeEditor getCodeEditor() {
        return mCodeEditor;
    }

    public FrameLayout getOverlayContainer() {
        return mFlOverlayContainer;
    }

    void setTestViews(FrameLayout container, CodeEditor editor) {
        this.mFlOverlayContainer = container;
        this.mCodeEditor = editor;
        this.mIsDiffMode = true;
    }

    void setTestDiffLinesAndHunks(List<DiffLine> lines) {
        this.mCurrentDiffLines = lines;
        extractDiffHunks(lines);
        rebuildOverlayViews();
    }

    public List<DiffLine> getCurrentDiffLines() {
        return mCurrentDiffLines;
    }

    public boolean isModified() {
        return mIsModified;
    }

    private void showContextMenu(View anchor) {
        if (getContext() == null) return;
        Context themedContext = new android.view.ContextThemeWrapper(getContext(), android.R.style.Theme_DeviceDefault_Settings);
        PopupMenu popup = new PopupMenu(themedContext, anchor);

        if (mIsDiffMode) {
            popup.getMenu().add("Revert All Changes");
        } else {
            popup.getMenu().add("Save");
            popup.getMenu().add("Reload File");
        }

        boolean isWordWrap = mCodeEditor != null && mCodeEditor.isWordwrap();
        MenuItem wordWrapItem = popup.getMenu().add(0, 101, 0, "Word Wrap");
        wordWrapItem.setCheckable(true);
        wordWrapItem.setChecked(isWordWrap);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 101) {
                boolean newState = !item.isChecked();
                item.setChecked(newState);
                if (mCodeEditor != null) {
                    mCodeEditor.setWordwrap(newState);
                }
                return true;
            }
            String title = item.getTitle().toString();
            if ("Save".equals(title)) {
                performSave();
                return true;
            } else if ("Reload File".equals(title)) {
                if (mFilePath != null && !mFilePath.isEmpty()) {
                    loadFileContent(mFilePath);
                    Toast.makeText(getContext(), "File reloaded", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if ("Revert All Changes".equals(title)) {
                showRevertAllDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCodeEditor = null;
        mTvFilePath = null;
        mBtnSave = null;
        mBtnUndo = null;
        mFlOverlayContainer = null;
    }
}
