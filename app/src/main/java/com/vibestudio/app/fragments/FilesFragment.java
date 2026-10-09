package com.vibestudio.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.files.action.FileAction;
import com.vibestudio.app.files.action.FileActionRegistry;
import com.vibestudio.app.files.model.DirectoryEntry;
import com.vibestudio.app.files.model.FileEntry;
import com.vibestudio.app.files.model.RegularFileEntry;
import com.vibestudio.app.files.model.UpDirEntry;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FilesFragment extends Fragment implements FileAction.FileActionListener {

    private static final String ARG_CURRENT_PATH = "current_path";
    private static final String TERMUX_FILES_ROOT = "/data/data/com.termux/files";

    private File mRootDir;
    private File mHomeDir;
    private File mCurrentDir;

    private TextView mTvCurrentPath;
    private ImageButton mBtnNewFolder;
    private FrameLayout mFlContentContainer;
    private TextView mTvEmptyState;

    public interface FileSelectionListener {
        void onFileSelected(File file);
    }

    private FileSelectionListener mSelectionListener;

    public static FilesFragment newInstance(String path) {
        FilesFragment fragment = new FilesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof FileSelectionListener) {
            mSelectionListener = (FileSelectionListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File appFilesDir = requireContext().getApplicationContext().getFilesDir();
        File termuxFilesRoot = new File(TERMUX_FILES_ROOT);

        if (termuxFilesRoot.exists()) {
            mRootDir = termuxFilesRoot;
            mHomeDir = new File(termuxFilesRoot, "home");
        } else {
            mRootDir = appFilesDir;
            mHomeDir = new File(appFilesDir, "home");
        }

        if (!mHomeDir.exists()) {
            mHomeDir.mkdirs();
        }

        String pathArg = null;
        if (getArguments() != null) {
            pathArg = getArguments().getString(ARG_CURRENT_PATH);
        }

        if (pathArg != null) {
            File target = new File(pathArg);
            if (target.exists() && isWithinRootDir(target)) {
                mCurrentDir = target;
            } else {
                mCurrentDir = mHomeDir;
            }
        } else {
            mCurrentDir = mHomeDir;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);

        mTvCurrentPath = view.findViewById(R.id.tv_current_path);
        mBtnNewFolder = view.findViewById(R.id.btn_new_folder);
        mFlContentContainer = view.findViewById(R.id.fl_content_container);
        mTvEmptyState = view.findViewById(R.id.tv_empty_state);

        mBtnNewFolder.setOnClickListener(v -> showCreateFolderDialog());

        loadDirectory(mCurrentDir);

        return view;
    }

    private boolean isWithinRootDir(File file) {
        try {
            String rootCanonical = mRootDir.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            return fileCanonical.startsWith(rootCanonical);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isAtRootDir(File file) {
        try {
            return file.getCanonicalPath().equals(mRootDir.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private List<FileEntry> fetchDirectoryItems(File dir) {
        List<FileEntry> items = new ArrayList<>();

        // 1. Add ".." item if NOT at Termux Root
        if (!isAtRootDir(dir)) {
            File parent = dir.getParentFile();
            if (parent != null && isWithinRootDir(parent)) {
                items.add(new UpDirEntry(parent));
            }
        }

        // 2. Fetch children files and dirs
        File[] files = dir.listFiles();
        List<FileEntry> dirsList = new ArrayList<>();
        List<FileEntry> filesList = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    dirsList.add(new DirectoryEntry(f));
                } else {
                    filesList.add(new RegularFileEntry(f));
                }
            }

            Comparator<FileEntry> comp = (a, b) -> a.getName().compareToIgnoreCase(b.getName());
            Collections.sort(dirsList, comp);
            Collections.sort(filesList, comp);

            items.addAll(dirsList);
            items.addAll(filesList);
        }

        return items;
    }

    private View buildDirectoryView(List<FileEntry> items) {
        if (getContext() == null) return null;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View listView = inflater.inflate(R.layout.view_file_list, mFlContentContainer, false);
        ViewGroup listContainer = listView.findViewById(R.id.rv_file_list);

        for (FileEntry entry : items) {
            View itemView = inflater.inflate(R.layout.item_file_entry, listContainer, false);

            TextView tvIcon = itemView.findViewById(R.id.tv_file_icon);
            TextView tvName = itemView.findViewById(R.id.tv_file_name);
            TextView tvDetails = itemView.findViewById(R.id.tv_file_details);
            TextView btnMenu = itemView.findViewById(R.id.btn_file_menu);

            tvName.setText(entry.getName());
            tvIcon.setText(entry.getIconSymbol());
            tvDetails.setText(entry.getDetailsText(getContext()));

            List<FileAction> actions = FileActionRegistry.getAvailableActionsFor(entry);
            if (actions.isEmpty()) {
                btnMenu.setVisibility(View.GONE);
            } else {
                btnMenu.setVisibility(View.VISIBLE);
            }

            if (entry.isUpDirectory()) {
                itemView.setOnClickListener(v -> navigateToDirectory(entry.getFile(), false));
                itemView.setOnLongClickListener(null);
            } else if (entry.isDirectory()) {
                itemView.setOnClickListener(v -> navigateToDirectory(entry.getFile(), true));
                itemView.setOnLongClickListener(v -> {
                    showItemContextMenu(btnMenu, entry);
                    return true;
                });
            } else {
                itemView.setOnClickListener(v -> {
                    if (mSelectionListener != null) {
                        mSelectionListener.onFileSelected(entry.getFile());
                    } else {
                        Toast.makeText(getContext(), "Selected: " + entry.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
                itemView.setOnLongClickListener(v -> {
                    showItemContextMenu(btnMenu, entry);
                    return true;
                });
            }

            btnMenu.setOnClickListener(v -> showItemContextMenu(v, entry));

            listContainer.addView(itemView);
        }

        return listView;
    }

    private void navigateToDirectory(File dir, boolean isGoingDeeper) {
        if (!dir.exists() || !dir.isDirectory() || !isWithinRootDir(dir)) {
            Toast.makeText(getContext(), "Cannot access directory outside Termux root", Toast.LENGTH_SHORT).show();
            return;
        }

        if (getContext() == null || mFlContentContainer == null) {
            loadDirectory(dir);
            return;
        }

        mCurrentDir = dir;
        updatePathHeader();

        List<FileEntry> items = fetchDirectoryItems(mCurrentDir);

        if (items.isEmpty() || (items.size() == 1 && items.get(0).isUpDirectory())) {
            mTvEmptyState.setVisibility(View.VISIBLE);
        } else {
            mTvEmptyState.setVisibility(View.GONE);
        }

        View newView = buildDirectoryView(items);
        if (newView == null) return;

        // Clean up any stale views from previous rapid clicks before animating
        while (mFlContentContainer.getChildCount() > 1) {
            View child = mFlContentContainer.getChildAt(0);
            child.clearAnimation();
            mFlContentContainer.removeViewAt(0);
        }

        if (mFlContentContainer.getChildCount() == 0) {
            mFlContentContainer.addView(newView);
            return;
        }

        final View oldView = mFlContentContainer.getChildAt(0);

        int inAnimRes = isGoingDeeper ? R.anim.slide_in_right : R.anim.slide_in_left;
        int outAnimRes = isGoingDeeper ? R.anim.slide_out_left : R.anim.slide_out_right;

        Animation inAnim = AnimationUtils.loadAnimation(getContext(), inAnimRes);
        Animation outAnim = AnimationUtils.loadAnimation(getContext(), outAnimRes);

        outAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                mFlContentContainer.post(() -> mFlContentContainer.removeView(oldView));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        mFlContentContainer.addView(newView);

        oldView.startAnimation(outAnim);
        newView.startAnimation(inAnim);
    }

    public void loadDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory() || !isWithinRootDir(dir)) {
            Toast.makeText(getContext(), "Cannot access directory outside Termux root", Toast.LENGTH_SHORT).show();
            return;
        }

        mCurrentDir = dir;
        updatePathHeader();

        List<FileEntry> items = fetchDirectoryItems(mCurrentDir);

        if (items.isEmpty() || (items.size() == 1 && items.get(0).isUpDirectory())) {
            mTvEmptyState.setVisibility(View.VISIBLE);
        } else {
            mTvEmptyState.setVisibility(View.GONE);
        }

        if (mFlContentContainer != null) {
            mFlContentContainer.removeAllViews();
            View listView = buildDirectoryView(items);
            if (listView != null) {
                mFlContentContainer.addView(listView);
            }
        }
    }

    private void updatePathHeader() {
        String pathTitle;
        try {
            String homePath = mHomeDir.getCanonicalPath();
            String rootPath = mRootDir.getCanonicalPath();
            String currentPath = mCurrentDir.getCanonicalPath();
            if (currentPath.equals(homePath)) {
                pathTitle = "~";
            } else if (currentPath.startsWith(homePath + "/")) {
                pathTitle = "~" + currentPath.substring(homePath.length());
            } else if (currentPath.equals(rootPath)) {
                pathTitle = "/";
            } else {
                pathTitle = currentPath;
            }
        } catch (IOException e) {
            pathTitle = mCurrentDir.getAbsolutePath();
        }

        mTvCurrentPath.setText(pathTitle);

        TabItem tab = TabManager.getInstance().findTabByFragment(this);
        if (tab != null) {
            TabManager.getInstance().updateTabTitle(tab, pathTitle);
        }
    }

    private void showCreateFolderDialog() {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("Create New Directory");

        final EditText input = new EditText(themedContext);
        input.setHint("Directory name");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x88FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                File newDir = new File(mCurrentDir, folderName);
                if (newDir.exists()) {
                    Toast.makeText(getContext(), "Directory already exists", Toast.LENGTH_SHORT).show();
                } else {
                    if (newDir.mkdirs()) {
                        Toast.makeText(getContext(), "Directory created", Toast.LENGTH_SHORT).show();
                        loadDirectory(mCurrentDir);
                    } else {
                        Toast.makeText(getContext(), "Failed to create directory", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showItemContextMenu(View anchor, FileEntry entry) {
        if (getContext() == null) return;
        List<FileAction> actions = FileActionRegistry.getAvailableActionsFor(entry);
        if (actions.isEmpty()) return;

        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault);
        PopupMenu popup = new PopupMenu(themedContext, anchor);

        for (int i = 0; i < actions.size(); i++) {
            popup.getMenu().add(0, i, i, actions.get(i).getTitle());
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            int index = menuItem.getItemId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).execute(requireContext(), entry, this);
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onDirectoryChanged(File newDir) {
        loadDirectory(newDir);
    }

    @Override
    public void onReloadRequested() {
        loadDirectory(mCurrentDir);
    }
}
