package com.vibestudio.app.fragments;

import com.vibestudio.app.git.GitManager;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
    private final List<ImageButton> mBtnOverlayViews = new ArrayList<>();

    public static class DiffHunk {
        public int line2Start; // 0-based start in modified file
        public int line2Count; // deleted/replaced line count
        public int editorStartLine; // 0-based line index in diff editor view
        public List<String> deletedLines = new ArrayList<>();
        public List<String> addedLines = new ArrayList<>();

        public DiffHunk(int line2Start, int line2Count, int editorStartLine) {
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

        // 2. Pre-create revert buttons for each diff hunk with rounded button styling
        int btnSize = dpToPx(28);
        if (mDiffHunks != null) {
            for (DiffHunk hunk : mDiffHunks) {
                ImageButton btnRevertHunk = new ImageButton(getContext());
                btnRevertHunk.setImageResource(R.drawable.ic_undo);
                btnRevertHunk.setColorFilter(Color.parseColor("#CF6679"));
                btnRevertHunk.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);

                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setShape(GradientDrawable.RECTANGLE);
                btnBg.setCornerRadius(dpToPx(14));
                btnBg.setColor(Color.parseColor("#2A2D3E"));
                btnBg.setStroke(dpToPx(1), Color.parseColor("#4C4D68"));
                btnRevertHunk.setBackground(btnBg);

                btnRevertHunk.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(btnSize, btnSize);
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.rightMargin = dpToPx(16);
                btnRevertHunk.setLayoutParams(lp);
                btnRevertHunk.setVisibility(View.GONE);

                btnRevertHunk.setOnClickListener(v -> revertSingleHunk(hunk));
                mFlOverlayContainer.addView(btnRevertHunk);
                mBtnOverlayViews.add(btnRevertHunk);
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

        if (mCurrentDiffLines != null && !mCurrentDiffLines.isEmpty() && mBgOverlayViews.isEmpty() && mBtnOverlayViews.isEmpty()) {
            rebuildOverlayViews();
            return;
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
                            bgView.setTranslationX(-mCodeEditor.getOffsetX());
                        }
                    }
                    bgIdx++;
                }
            }
        }

        // 2. Update revert buttons via translationY with vertical centering across ALL lines in the hunk
        int btnSize = dpToPx(28);
        if (mDiffHunks != null) {
            int btnIdx = 0;
            for (DiffHunk hunk : mDiffHunks) {
                if (btnIdx < mBtnOverlayViews.size()) {
                    ImageButton btn = mBtnOverlayViews.get(btnIdx);
                    int totalHunkLines = hunk.deletedLines.size() + hunk.addedLines.size();
                    if (totalHunkLines <= 0) totalHunkLines = 1;

                    int hunkTop = mCodeEditor.getRowTop(hunk.editorStartLine) - offsetY;
                    int totalHunkHeight = totalHunkLines * lineHeight;
                    float btnTop = hunkTop + (totalHunkHeight - btnSize) / 2f;

                    if (containerHeight > 0 && (hunkTop + totalHunkHeight < -lineHeight || hunkTop > containerHeight + lineHeight)) {
                        btn.setVisibility(View.GONE);
                    } else {
                        btn.setVisibility(View.VISIBLE);
                        btn.setTranslationY(btnTop);
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
                .setMessage("Do you want to revert this file to index?")
                .setPositiveButton("Revert", (dialog, which) -> revertAllToFile1())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void revertAllToFile1() {
        if (!mIsDiffMode || mFile1Path == null || mFile2Path == null) return;
        try {
            File targetFile = new File(mFile2Path);
            boolean file1HadTrailing = checkTrailingNewline(new File(mFile1Path));
            List<String> baseLines = readLinesFromFile(mFile1Path);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < baseLines.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(baseLines.get(i));
            }
            if (file1HadTrailing && sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append("\n");
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
                writer.write(sb.toString());
            }
            if (getContext() != null) {
                Toast.makeText(getContext(), "Reverted file to index", Toast.LENGTH_SHORT).show();
            }
            loadDiffContent(mFile1Path, mFile2Path);

            File repoRoot = GitManager.getInstance().findGitRoot(targetFile);
            if (repoRoot != null && getContext() != null) {
                GitManager.getInstance().refreshRepositoryState(getContext().getApplicationContext(), repoRoot, null);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to revert file to index", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Failed to revert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void revertSingleHunk(DiffHunk hunk) {
        if (!mIsDiffMode || mFile2Path == null) return;
        List<DiffHunk> singleList = new ArrayList<>();
        singleList.add(hunk);
        applyHunkRevert(singleList, "Reverted change");
    }

    private void applyHunkRevert(List<DiffHunk> hunksToRevert, String successMsg) {
        File file2 = new File(mFile2Path);
        try {
            List<String> lines2 = readLinesFromFile(mFile2Path);

            Collections.sort(hunksToRevert, (h1, h2) -> Integer.compare(h2.line2Start, h1.line2Start));

            for (DiffHunk hunk : hunksToRevert) {
                int start = hunk.line2Start;
                int countToRemove = hunk.line2Count;

                for (int i = 0; i < countToRemove && start < lines2.size(); i++) {
                    lines2.remove(start);
                }

                for (int i = 0; i < hunk.deletedLines.size(); i++) {
                    int insertIndex = start + i;
                    if (insertIndex <= lines2.size()) {
                        lines2.add(insertIndex, hunk.deletedLines.get(i));
                    } else {
                        lines2.add(hunk.deletedLines.get(i));
                    }
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

            if (getContext() != null) {
                Toast.makeText(getContext(), successMsg, Toast.LENGTH_SHORT).show();
            }

            loadDiffContent(mFile1Path, mFile2Path);

            File repoRoot = GitManager.getInstance().findGitRoot(file2);
            if (repoRoot != null && getContext() != null) {
                GitManager.getInstance().refreshRepositoryState(getContext().getApplicationContext(), repoRoot, null);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to revert diff changes", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Failed to revert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
                line2Index++;
            } else if (line.type == DiffLine.Type.DELETED) {
                if (currentHunk == null) {
                    currentHunk = new DiffHunk(line2Index, 0, editorLineIndex);
                }
                currentHunk.deletedLines.add(line.text != null ? line.text : "");
            } else if (line.type == DiffLine.Type.ADDED) {
                if (currentHunk == null) {
                    currentHunk = new DiffHunk(line2Index, 0, editorLineIndex);
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

            File repoRootSave = GitManager.getInstance().findGitRoot(file);
            if (repoRootSave != null && getContext() != null) {
                GitManager.getInstance().refreshRepositoryState(getContext().getApplicationContext(), repoRootSave, null);
            }
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
    }

    public List<DiffLine> getCurrentDiffLines() {
        return mCurrentDiffLines;
    }

    public boolean isModified() {
        return mIsModified;
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
