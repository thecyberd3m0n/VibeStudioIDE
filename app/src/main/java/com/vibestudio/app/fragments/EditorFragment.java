package com.vibestudio.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.vibestudio.app.editor.TextMateManager;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import io.github.rosemoe.sora.event.ContentChangeEvent;

/** Plain text file editor with a Save button. */
public class EditorFragment extends BaseEditorFragment {

    private static final String TAG = "EditorFragment";
    private static final String ARG_FILE_PATH = "file_path";

    private String mFilePath;

    private String mSavedContent = "";
    private boolean mIsLoading = false;
    private boolean mIsSaving = false;
    private boolean mIsModified = false;
    private boolean mHasTrailingNewline = false;

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

    @Override
    protected void onEditorViewCreated() {
        updateFilePathUI();
        updateSaveButtonState(false);
        if (mBtnUndo != null) mBtnUndo.setVisibility(View.GONE);

        if (mCodeEditor != null) {
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

    @Override
    protected void onPrimaryActionClicked() {
        performSave();
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

    public boolean isModified() {
        return mIsModified;
    }

    @Override
    protected void onMoreClicked(View anchor) {
        showContextMenu(anchor);
    }

    private void showContextMenu(View anchor) {
        if (getContext() == null) return;
        Context themedContext = new android.view.ContextThemeWrapper(getContext(), android.R.style.Theme_DeviceDefault_Settings);
        PopupMenu popup = new PopupMenu(themedContext, anchor);

        popup.getMenu().add("Save");
        popup.getMenu().add("Reload File");

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
            }
            return false;
        });
        popup.show();
    }
}
