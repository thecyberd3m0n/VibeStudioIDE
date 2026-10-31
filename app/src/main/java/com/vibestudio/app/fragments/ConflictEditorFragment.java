package com.vibestudio.app.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.vibestudio.app.editor.TextMateManager;
import com.vibestudio.app.git.ConflictParser;
import com.vibestudio.app.git.GitManager;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.event.ContentChangeEvent;

/**
 * Editor for a file with git conflict markers. Every conflict region gets two checkboxes (ours / theirs) drawn on
 * top of the editor like the hunk buttons of the diff view. The text stays editable; the toolbar button
 * "Resolve" becomes active once every region has at least one side accepted, then writes the merged text,
 * stages the file and closes the tab.
 */
public class ConflictEditorFragment extends BaseEditorFragment {

    private static final String TAG = "ConflictEditorFragment";
    private static final String ARG_REPO_DIR = "repo_dir";
    private static final String ARG_REL_PATH = "rel_path";

    private static final int COLOR_OURS = 0x2E7D32;
    private static final int COLOR_THEIRS = 0x1565C0;
    private static final int TINT_OURS = 0xFF81C784;
    private static final int TINT_THEIRS = 0xFF64B5F6;
    private static final int COLOR_BASE = 0x9E9E9E;
    private static final int ALPHA_ACCEPTED = 0x80;
    private static final int ALPHA_IDLE = 0x38;

    private File mRepoDir;
    private String mRelPath;
    private File mFile;

    private boolean mLoading = false;
    private boolean mResolving = false;
    private boolean mHadTrailingNewline = true;
    private String mEol = "\n";

    private List<ConflictParser.Region> mRegions = new ArrayList<>();
    private final List<RegionViews> mRegionViews = new ArrayList<>();

    /** Overlay views that belong to a single conflict region. */
    private static final class RegionViews {
        ConflictParser.Region region;
        View oursBg;
        View baseBg;
        View theirsBg;
        CheckBox cbOurs;
        CheckBox cbTheirs;
    }

    public static ConflictEditorFragment newInstance(File repoDir, String relativePath) {
        ConflictEditorFragment fragment = new ConflictEditorFragment();
        fragment.mRepoDir = repoDir;
        fragment.mRelPath = relativePath;
        Bundle args = new Bundle();
        args.putString(ARG_REPO_DIR, repoDir.getAbsolutePath());
        args.putString(ARG_REL_PATH, relativePath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String repo = getArguments().getString(ARG_REPO_DIR);
            if (repo != null) mRepoDir = new File(repo);
            mRelPath = getArguments().getString(ARG_REL_PATH);
        }
        if (mRepoDir != null && mRelPath != null) {
            mFile = new File(mRepoDir, mRelPath);
        }
    }

    public File getFile() {
        return mFile;
    }

    @Override
    protected void onEditorViewCreated() {
        if (mBtnUndo != null) mBtnUndo.setVisibility(View.GONE);
        if (mBtnSave != null) {
            mBtnSave.setText("Resolve");
            mBtnSave.setVisibility(View.VISIBLE);
        }
        if (mCodeEditor == null) return;

        mCodeEditor.setTextSize(14f);
        setupOverlaySync();
        loadFile();

        mCodeEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (!mLoading) runOnMainThread(this::reparse);
        });
    }

    private void loadFile() {
        if (mFile == null || mCodeEditor == null) return;
        if (getContext() != null) {
            TextMateManager.getInstance().applyLanguageAndTheme(getContext(), mCodeEditor, mFile.getAbsolutePath());
        }
        mHadTrailingNewline = checkTrailingNewline(mFile);

        String content = "";
        try {
            content = new String(Files.readAllBytes(mFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LogViewerService.getInstance().e(TAG, "Failed to read " + mFile, e);
            toast("Cannot read " + mRelPath);
        }
        mEol = content.contains("\r\n") ? "\r\n" : "\n";

        mLoading = true;
        try {
            mCodeEditor.setText(ConflictParser.join(ConflictParser.splitLines(content), "\n", false));
        } finally {
            mLoading = false;
        }
        reparse();
    }

    private List<String> currentLines() {
        return mCodeEditor == null ? new ArrayList<>() : ConflictParser.splitLines(mCodeEditor.getText().toString());
    }

    /** Re-reads the regions from the current text, keeping the sides the user already ticked. */
    private void reparse() {
        if (mCodeEditor == null) return;
        List<ConflictParser.Region> parsed = ConflictParser.parse(currentLines());
        ConflictParser.carryOverChoices(mRegions, parsed);
        mRegions = parsed;
        rebuildOverlayViews();
        updateResolveState();
    }

    private void rebuildOverlayViews() {
        if (mFlOverlayContainer == null || getContext() == null) return;
        mFlOverlayContainer.removeAllViews();
        mRegionViews.clear();

        for (ConflictParser.Region region : mRegions) {
            RegionViews rv = new RegionViews();
            rv.region = region;
            rv.oursBg = createBlockBackground();
            rv.theirsBg = createBlockBackground();
            if (region.baseLine >= 0) rv.baseBg = createBlockBackground();
            rv.cbOurs = createSideCheckBox("Ours", region.oursLabel, TINT_OURS, region.acceptOurs);
            rv.cbTheirs = createSideCheckBox("Theirs", region.theirsLabel, TINT_THEIRS, region.acceptTheirs);

            rv.cbOurs.setOnCheckedChangeListener((b, checked) -> {
                region.acceptOurs = checked;
                onChoiceChanged(rv);
            });
            rv.cbTheirs.setOnCheckedChangeListener((b, checked) -> {
                region.acceptTheirs = checked;
                onChoiceChanged(rv);
            });

            // Backgrounds first so the checkboxes stay clickable on top of them.
            mFlOverlayContainer.addView(rv.oursBg);
            if (rv.baseBg != null) mFlOverlayContainer.addView(rv.baseBg);
            mFlOverlayContainer.addView(rv.theirsBg);
            mFlOverlayContainer.addView(rv.cbOurs);
            mFlOverlayContainer.addView(rv.cbTheirs);
            mRegionViews.add(rv);
            applyBlockColors(rv);
        }
        updateOverlayPositions();
    }

    private View createBlockBackground() {
        View v = new View(getContext());
        v.setClickable(false);
        v.setFocusable(false);
        v.setVisibility(View.GONE);
        v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(20)));
        return v;
    }

    private CheckBox createSideCheckBox(String text, String label, int tint, boolean checked) {
        CheckBox cb = new CheckBox(getContext());
        cb.setText(text);
        cb.setTextSize(11f);
        cb.setTextColor(Color.parseColor("#E0E0E0"));
        cb.setChecked(checked);
        cb.setButtonTintList(ColorStateList.valueOf(tint));
        cb.setMinHeight(0);
        cb.setMinimumHeight(0);
        cb.setPadding(dpToPx(2), 0, dpToPx(8), 0);
        cb.setVisibility(View.GONE);
        if (label != null && !label.isEmpty()) {
            cb.setContentDescription(text + " (" + label + ")");
            cb.setTooltipText(label);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(14));
        bg.setColor(Color.parseColor("#2A2D3E"));
        bg.setStroke(dpToPx(1), Color.parseColor("#4C4D68"));
        cb.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(32));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.rightMargin = dpToPx(16);
        cb.setLayoutParams(lp);
        return cb;
    }

    private void onChoiceChanged(RegionViews rv) {
        applyBlockColors(rv);
        updateResolveState();
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private void applyBlockColors(RegionViews rv) {
        rv.oursBg.setBackgroundColor(withAlpha(COLOR_OURS, rv.region.acceptOurs ? ALPHA_ACCEPTED : ALPHA_IDLE));
        rv.theirsBg.setBackgroundColor(withAlpha(COLOR_THEIRS, rv.region.acceptTheirs ? ALPHA_ACCEPTED : ALPHA_IDLE));
        if (rv.baseBg != null) rv.baseBg.setBackgroundColor(withAlpha(COLOR_BASE, ALPHA_IDLE));
    }

    private void updateResolveState() {
        boolean ready = !mResolving && ConflictParser.allResolved(mRegions);
        if (mBtnSave != null) {
            mBtnSave.setEnabled(ready);
            mBtnSave.setAlpha(ready ? 1.0f : 0.4f);
        }
        if (mTvFilePath != null) {
            int resolved = 0;
            for (ConflictParser.Region r : mRegions) {
                if (r.isResolved()) resolved++;
            }
            String name = mFile != null ? mFile.getName() : "";
            mTvFilePath.setText(mRegions.isEmpty()
                    ? "Conflict: " + name + " (no markers left)"
                    : "Conflict: " + name + " (" + resolved + "/" + mRegions.size() + " resolved)");
        }
    }

    @Override
    protected void updateOverlayPositions() {
        if (mFlOverlayContainer == null || mCodeEditor == null || getContext() == null) return;

        int lineHeight = mCodeEditor.getRowHeight();
        if (lineHeight <= 0) lineHeight = dpToPx(20);
        int offsetY = mCodeEditor.getOffsetY();
        int containerHeight = mFlOverlayContainer.getHeight();
        if (containerHeight <= 0) containerHeight = mCodeEditor.getHeight();

        for (RegionViews rv : mRegionViews) {
            ConflictParser.Region r = rv.region;
            // "ours" block: its start marker plus its lines; "theirs": separator, its lines and end marker.
            placeBlock(rv.oursBg, r.startLine, r.oursEndExclusive() - r.startLine, lineHeight, offsetY, containerHeight);
            if (rv.baseBg != null) {
                placeBlock(rv.baseBg, r.baseLine, r.separatorLine - r.baseLine, lineHeight, offsetY, containerHeight);
            }
            placeBlock(rv.theirsBg, r.separatorLine, r.endLine - r.separatorLine + 1, lineHeight, offsetY, containerHeight);

            placeCheckBox(rv.cbOurs, r.startLine, r.oursEndExclusive() - r.startLine, lineHeight, offsetY, containerHeight);
            placeCheckBox(rv.cbTheirs, r.separatorLine, r.endLine - r.separatorLine + 1, lineHeight, offsetY, containerHeight);
        }
    }

    private void placeBlock(View bg, int firstLine, int lineCount, int lineHeight, int offsetY, int containerHeight) {
        int height = Math.max(1, lineCount) * lineHeight;
        ViewGroup.LayoutParams lp = bg.getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            bg.setLayoutParams(lp);
        }
        int top = mCodeEditor.getRowTop(firstLine) - offsetY;
        if (containerHeight > 0 && (top + height < 0 || top > containerHeight)) {
            bg.setVisibility(View.GONE);
        } else {
            bg.setVisibility(View.VISIBLE);
            bg.setTranslationY(top);
        }
    }

    /** Centers the checkbox on the part of the block that is currently visible. */
    private void placeCheckBox(View cb, int firstLine, int lineCount, int lineHeight, int offsetY, int containerHeight) {
        int height = Math.max(1, lineCount) * lineHeight;
        int top = mCodeEditor.getRowTop(firstLine) - offsetY;
        int bottom = top + height;
        if (containerHeight > 0 && (bottom < 0 || top > containerHeight)) {
            cb.setVisibility(View.GONE);
            return;
        }
        int visibleTop = Math.max(top, 0);
        int visibleBottom = containerHeight > 0 ? Math.min(bottom, containerHeight) : bottom;
        float y = (visibleTop + visibleBottom - dpToPx(32)) / 2f;
        cb.setVisibility(View.VISIBLE);
        cb.setTranslationY(y);
    }

    @Override
    protected void onPrimaryActionClicked() {
        if (mCodeEditor == null || mFile == null || mRepoDir == null || mRelPath == null || mResolving) return;

        List<String> lines = currentLines();
        List<ConflictParser.Region> regions = ConflictParser.parse(lines);
        ConflictParser.carryOverChoices(mRegions, regions);
        if (!ConflictParser.allResolved(regions)) {
            toast("Accept ours or theirs for every conflict first");
            return;
        }

        String merged = ConflictParser.join(ConflictParser.resolve(lines, regions), mEol, mHadTrailingNewline);
        try (FileOutputStream out = new FileOutputStream(mFile)) {
            out.write(merged.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LogViewerService.getInstance().e(TAG, "Failed to write " + mFile, e);
            toast("Failed to save " + mRelPath + ": " + e.getMessage());
            return;
        }

        mResolving = true;
        updateResolveState();
        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        GitManager.getInstance().stageFile(appContext, mRepoDir, mRelPath, (success, output) -> {
            mResolving = false;
            if (success) {
                toast("Resolved " + mFile.getName());
                GitManager.getInstance().refreshRepositoryState(appContext, mRepoDir, null);
                TabItem tab = TabManager.getInstance().findTabByFragment(this);
                if (tab != null) TabManager.getInstance().closeTab(tab);
            } else {
                toast("Saved, but staging failed: " + output);
                updateResolveState();
            }
        });
    }

    private void acceptAll(boolean ours, boolean theirs) {
        for (ConflictParser.Region r : mRegions) {
            r.acceptOurs = ours;
            r.acceptTheirs = theirs;
        }
        for (RegionViews rv : mRegionViews) {
            rv.cbOurs.setChecked(ours);
            rv.cbTheirs.setChecked(theirs);
        }
        updateResolveState();
    }

    @Override
    protected void onMoreClicked(View anchor) {
        if (getContext() == null) return;
        Context themed = new android.view.ContextThemeWrapper(getContext(), android.R.style.Theme_DeviceDefault);
        PopupMenu popup = new PopupMenu(themed, anchor);
        popup.getMenu().add(0, 1, 0, "Accept all ours");
        popup.getMenu().add(0, 2, 1, "Accept all theirs");
        popup.getMenu().add(0, 3, 2, "Accept all both");
        popup.getMenu().add(0, 4, 3, "Reload from disk");
        MenuItem wrap = popup.getMenu().add(0, 101, 4, "Word Wrap");
        wrap.setCheckable(true);
        wrap.setChecked(mCodeEditor != null && mCodeEditor.isWordwrap());

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: acceptAll(true, false); return true;
                case 2: acceptAll(false, true); return true;
                case 3: acceptAll(true, true); return true;
                case 4:
                    mRegions = new ArrayList<>();
                    loadFile();
                    return true;
                case 101:
                    boolean on = !item.isChecked();
                    item.setChecked(on);
                    if (mCodeEditor != null) mCodeEditor.setWordwrap(on);
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void toast(String message) {
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
    }
}
