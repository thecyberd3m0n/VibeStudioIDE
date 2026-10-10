package com.vibestudio.app.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
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

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.event.ContentChangeEvent;

public class EditorFragment extends Fragment {

    private static final String TAG = "EditorFragment";
    private static final String ARG_FILE_PATH = "file_path";

    private CodeEditor mCodeEditor;
    private TextView mTvFilePath;
    private Button mBtnSave;
    private String mFilePath;
    private String mSavedContent = "";
    private boolean mIsLoading = false;
    private boolean mIsSaving = false;
    private boolean mIsModified = false;

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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mFilePath = getArguments().getString(ARG_FILE_PATH);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_editor, container, false);
        mCodeEditor = view.findViewById(R.id.code_editor);
        mTvFilePath = view.findViewById(R.id.tv_file_path);
        mBtnSave = view.findViewById(R.id.btn_save);

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
        return view;
    }

    private void runOnMainThread(Runnable runnable) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(runnable);
        } else if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        }
    }

    private void updateFilePathUI() {
        if (mTvFilePath != null) {
            if (mFilePath != null && !mFilePath.isEmpty()) {
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

    public void loadFileContent(String filePath) {
        this.mFilePath = filePath;
        updateFilePathUI();

        File file = new File(filePath);
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

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
            mSavedContent = content;
            updateSaveButtonState(false);
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

    public boolean isModified() {
        return mIsModified;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCodeEditor = null;
        mTvFilePath = null;
        mBtnSave = null;
    }
}
