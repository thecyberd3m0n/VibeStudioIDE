package com.vibestudio.app.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.util.FontUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

/**
 * Shared shell of the editor-like tabs: top bar, code editor, and a transparent overlay container that
 * subclasses fill with views positioned on top of editor rows (diff hunks, conflict checkboxes).
 */
public abstract class BaseEditorFragment extends Fragment {

    protected CodeEditor mCodeEditor;
    protected TextView mTvFilePath;
    protected Button mBtnSave;
    protected ImageButton mBtnUndo;
    protected ImageButton mBtnMore;
    protected FrameLayout mFlOverlayContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_editor, container, false);
        mCodeEditor = view.findViewById(R.id.code_editor);
        mTvFilePath = view.findViewById(R.id.tv_file_path);
        mBtnSave = view.findViewById(R.id.btn_save);
        mBtnUndo = view.findViewById(R.id.btn_undo);
        mBtnMore = view.findViewById(R.id.btn_more);
        mFlOverlayContainer = view.findViewById(R.id.overlay_hunks_container);

        if (mBtnMore != null) {
            mBtnMore.setOnClickListener(this::onMoreClicked);
        }
        if (mBtnSave != null) {
            mBtnSave.setOnClickListener(v -> onPrimaryActionClicked());
        }

        if (mCodeEditor != null) {
            SchemeDarcula darkScheme = new SchemeDarcula();
            darkScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E2E);
            darkScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF181825);
            mCodeEditor.setColorScheme(darkScheme);

            Typeface monoTypeface = FontUtils.getMonospaceTypeface(getContext());
            mCodeEditor.setTypefaceText(monoTypeface);
            mCodeEditor.setTypefaceLineNumber(monoTypeface);
        }

        onEditorViewCreated();
        return view;
    }

    /** Called once the views are bound and the editor is themed; subclasses load their content here. */
    protected abstract void onEditorViewCreated();

    /** The top bar button that is "Save" in the file editor and "Resolve" in the conflict editor; hidden for diffs. */
    protected void onPrimaryActionClicked() {}

    protected abstract void onMoreClicked(View anchor);

    /** Re-aligns overlay views with the editor rows (called on scroll, layout and text changes). */
    protected void updateOverlayPositions() {}

    protected void setupOverlaySync() {
        if (mCodeEditor == null) return;

        mCodeEditor.subscribeEvent(ScrollEvent.class, (event, unsubscribe) -> updateOverlayPositions());
        mCodeEditor.getViewTreeObserver().addOnScrollChangedListener(this::updateOverlayPositions);
        mCodeEditor.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (mCodeEditor != null) {
                updateOverlayPositions();
            }
            return true;
        });
    }

    public FrameLayout getOverlayContainer() {
        return mFlOverlayContainer;
    }

    protected int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    protected void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else if (getActivity() != null) {
            getActivity().runOnUiThread(runnable);
        }
    }

    public static List<String> readLinesFromFile(String filePath) {
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
            Log.e("BaseEditorFragment", "Error reading lines from file: " + filePath, e);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCodeEditor = null;
        mTvFilePath = null;
        mBtnSave = null;
        mBtnUndo = null;
        mBtnMore = null;
        mFlOverlayContainer = null;
    }
}
