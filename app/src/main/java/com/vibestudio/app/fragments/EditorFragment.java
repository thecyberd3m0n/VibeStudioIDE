package com.vibestudio.app.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.util.FontUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.event.ContentChangeEvent;

public class EditorFragment extends Fragment {

    private static final String TAG = "EditorFragment";
    private static final String ARG_FILE_PATH = "file_path";

    private CodeEditor mCodeEditor;
    private String mFilePath;
    private boolean mIsLoading = false;
    private boolean mIsSaving = false;

    public static EditorFragment newInstance() {
        return new EditorFragment();
    }

    public static EditorFragment newInstance(String filePath) {
        EditorFragment fragment = new EditorFragment();
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
        if (mCodeEditor != null) {
            Typeface monoTypeface = FontUtils.getMonospaceTypeface(getContext());
            mCodeEditor.setTypefaceText(monoTypeface);
            mCodeEditor.setTypefaceLineNumber(monoTypeface);

            mCodeEditor.setText("");
            mCodeEditor.setTextSize(14f);

            if (mFilePath != null && !mFilePath.isEmpty()) {
                loadFileContent(mFilePath);
            }

            mCodeEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
                if (!mIsLoading && !mIsSaving && mFilePath != null && mCodeEditor != null) {
                    saveFileContent(mFilePath, mCodeEditor.getText().toString());
                }
            });
        }
        return view;
    }

    public void loadFileContent(String filePath) {
        this.mFilePath = filePath;
        File file = new File(filePath);
        if (!file.exists()) {
            LogViewerService.getInstance().w(TAG, "File does not exist: " + filePath);
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
            if (mCodeEditor != null) {
                mCodeEditor.setText(content.toString());
            }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCodeEditor = null;
    }
}
