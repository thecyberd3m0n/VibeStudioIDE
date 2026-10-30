package com.vibestudio.app.fragments;

import com.vibestudio.app.git.GitStatusView;
import com.vibestudio.app.git.GitManager;
import com.vibestudio.app.git.GitRepositoryState;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FilesFragment extends Fragment implements FileAction.FileActionListener, GitManager.GitStateChangeListener {

    private static final String ARG_CURRENT_PATH = "current_path";
    private static final String TERMUX_FILES_ROOT = "/data/data/com.termux/files";

    private File mRootDir;
    private File mHomeDir;
    private File mCurrentDir;
    private GitRepositoryState mCurrentGitState;

    private TextView mTvCurrentPath;
    private ImageButton mBtnNewFolder;
    private FrameLayout mFlContentContainer;
    private TextView mTvEmptyState;

    private View mLayoutBottomBar;
    private TextView mTvBottomBarText;
    private Button mBtnBottomBarCancel;
    private Button mBtnBottomBarOk;

    private List<Uri> mPendingSharedUris = new ArrayList<>();

    private View mLayoutGitChangesBar;
    private TextView mTvGitChangesLabel;
    private TextView mTvGitChangesChevron;
    private FrameLayout mFlGitViewContainer;
    private GitStatusView mGitStatusView;
    private boolean mIsGitViewOpen = false;

    public interface FileSelectionListener {
        void onFileSelected(File file);
    }

    private FileSelectionListener mSelectionListener;
    private FileSelectionListener mPickerSelectionListener;

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
    public void onResume() {
        super.onResume();
        checkAndUpdateGitStatus();
        if (mIsGitViewOpen && mGitStatusView != null) {
            mGitStatusView.refreshStatus();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            checkAndUpdateGitStatus();
            if (mIsGitViewOpen && mGitStatusView != null) {
                mGitStatusView.refreshStatus();
            }
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            checkAndUpdateGitStatus();
            if (mIsGitViewOpen && mGitStatusView != null) {
                mGitStatusView.refreshStatus();
            }
        }
    }

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

        mLayoutBottomBar = view.findViewById(R.id.layout_bottom_bar);
        mTvBottomBarText = view.findViewById(R.id.tv_bottom_bar_text);
        mBtnBottomBarCancel = view.findViewById(R.id.btn_bottom_bar_cancel);
        mBtnBottomBarOk = view.findViewById(R.id.btn_bottom_bar_ok);

        if (mBtnBottomBarCancel != null) {
            mBtnBottomBarCancel.setOnClickListener(v -> cancelPendingSharedFiles());
        }
        if (mBtnBottomBarOk != null) {
            mBtnBottomBarOk.setOnClickListener(v -> confirmCopyPendingSharedFiles());
        }

        mBtnNewFolder.setOnClickListener(v -> showAddMenu(v));
        GitManager.getInstance().addStateChangeListener(this);

        mLayoutGitChangesBar = view.findViewById(R.id.layout_git_changes_bar);
        mTvGitChangesLabel = view.findViewById(R.id.tv_git_changes_label);
        mTvGitChangesChevron = view.findViewById(R.id.tv_git_changes_chevron);
        mFlGitViewContainer = view.findViewById(R.id.fl_git_view_container);

        View btnGitToggle = view.findViewById(R.id.btn_git_changes_toggle);
        if (btnGitToggle != null) {
            btnGitToggle.setOnClickListener(v -> toggleGitView());

            final float[] startY = new float[1];
            final float[] initialTranslationY = new float[1];
            final boolean[] isDragging = new boolean[1];

            btnGitToggle.setOnTouchListener((v, event) -> {
                if (mFlGitViewContainer == null) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY[0] = event.getRawY();
                        preloadGitView();
                        int height = getGitContainerHeight();

                        if (!mIsGitViewOpen) {
                            mFlGitViewContainer.setTranslationY(height);
                        }
                        if (mGitStatusView != null) {
                            mGitStatusView.setTranslationY(0);
                        }
                        initialTranslationY[0] = mFlGitViewContainer.getTranslationY();
                        isDragging[0] = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - startY[0];
                        if (Math.abs(dy) > 10) {
                            isDragging[0] = true;
                        }
                        int containerHeight = getGitContainerHeight();
                        float targetTrans = initialTranslationY[0] + dy;
                        if (targetTrans < 0) targetTrans = 0;
                        if (targetTrans > containerHeight) targetTrans = containerHeight;
                        mFlGitViewContainer.setTranslationY(targetTrans);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float totalDy = event.getRawY() - startY[0];
                        if (!isDragging[0] && Math.abs(totalDy) < 15) {
                            v.performClick();
                            return true;
                        }
                        int viewHeight = getGitContainerHeight();
                        float threshold = Math.max(30f, viewHeight * 0.15f);
                        if (totalDy < -threshold) {
                            openGitView();
                        } else if (totalDy > threshold) {
                            closeGitView();
                        } else {
                            if (mIsGitViewOpen) openGitView(); else closeGitView();
                        }
                        return true;
                }
                return false;
            });
        }

        loadDirectory(mCurrentDir);

        if (mPickerSelectionListener != null) {
            setBottomBarText("Select file to upload");
            setBottomBarVisible(true);
            setOnBottomBarCancelClickListener(v -> cancelFilePickerMode());
            if (mBtnBottomBarOk != null) {
                mBtnBottomBarOk.setVisibility(View.GONE);
            }
        } else if (!mPendingSharedUris.isEmpty()) {
            setBottomBarText("Select destination");
            setBottomBarVisible(true);
        }

        return view;
    }

    public void setBottomBarVisible(boolean visible) {
        if (mLayoutBottomBar != null) {
            mLayoutBottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setBottomBarText(String text) {
        if (mTvBottomBarText != null) {
            mTvBottomBarText.setText(text);
        }
    }

    public void setOnBottomBarCancelClickListener(View.OnClickListener listener) {
        if (mBtnBottomBarCancel != null) {
            mBtnBottomBarCancel.setOnClickListener(listener);
        }
    }

    public void startFilePickerMode(String text, FileSelectionListener listener) {
        if (mPickerSelectionListener != null) {
            FileSelectionListener prevListener = mPickerSelectionListener;
            mPickerSelectionListener = null;
            prevListener.onFileSelected(null);
        }
        mPickerSelectionListener = listener;
        setBottomBarText(text != null ? text : "Select file");
        setBottomBarVisible(true);
        setOnBottomBarCancelClickListener(v -> cancelFilePickerMode());
        if (mBtnBottomBarOk != null) {
            mBtnBottomBarOk.setVisibility(View.GONE);
        }
    }

    public void cancelFilePickerMode() {
        if (mPickerSelectionListener != null) {
            FileSelectionListener listener = mPickerSelectionListener;
            mPickerSelectionListener = null;
            listener.onFileSelected(null);
        }
        setBottomBarVisible(false);
        if (mBtnBottomBarOk != null) {
            mBtnBottomBarOk.setVisibility(View.VISIBLE);
        }
    }

    public void startPendingSharedFiles(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        mPendingSharedUris = new ArrayList<>(uris);
        setBottomBarText("Select destination");
        setBottomBarVisible(true);
    }

    public void cancelPendingSharedFiles() {
        mPendingSharedUris.clear();
        setBottomBarVisible(false);
    }

    public void confirmCopyPendingSharedFiles() {
        if (mPendingSharedUris.isEmpty()) {
            setBottomBarVisible(false);
            return;
        }

        Context context = getContext();
        if (context == null || mCurrentDir == null) return;

        int successCount = 0;
        int failCount = 0;

        for (Uri uri : mPendingSharedUris) {
            String fileName = getFileNameFromUri(context, uri);
            File destFile = getUniqueDestFile(mCurrentDir, fileName);
            if (copyUriToFile(context, uri, destFile)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        if (successCount > 0) {
            Toast.makeText(context, "Copied " + successCount + " file(s) to " + mCurrentDir.getName(), Toast.LENGTH_SHORT).show();
            loadDirectory(mCurrentDir);
        } else if (failCount > 0) {
            Toast.makeText(context, "Failed to copy file(s)", Toast.LENGTH_SHORT).show();
        }

        mPendingSharedUris.clear();
        setBottomBarVisible(false);
    }

    private File getUniqueDestFile(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) return file;

        String nameWithoutExt = fileName;
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            ext = fileName.substring(dotIndex);
        }

        int count = 1;
        while (file.exists()) {
            file = new File(dir, nameWithoutExt + "_" + count + ext);
            count++;
        }
        return file;
    }

    private String getFileNameFromUri(Context context, Uri uri) {
        String fileName = null;
        if (uri != null && "content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (fileName == null && uri != null) {
            fileName = uri.getLastPathSegment();
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "shared_file_" + System.currentTimeMillis();
        }
        return fileName;
    }

    private boolean copyUriToFile(Context context, Uri uri, File destFile) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return false;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
            ImageView imgIcon = itemView.findViewById(R.id.img_file_icon);
            TextView tvName = itemView.findViewById(R.id.tv_file_name);
            TextView tvDetails = itemView.findViewById(R.id.tv_file_details);
            TextView btnMenu = itemView.findViewById(R.id.btn_file_menu);

            tvName.setText(entry.getName());
            if (entry.getIconResId() != null) {
                tvIcon.setVisibility(View.GONE);
                imgIcon.setVisibility(View.VISIBLE);
                imgIcon.setImageResource(entry.getIconResId());
            } else {
                imgIcon.setVisibility(View.GONE);
                tvIcon.setVisibility(View.VISIBLE);
                tvIcon.setText(entry.getIconSymbol());
            }
            String details = entry.getDetailsText(getContext());
            File repoRoot = GitManager.getInstance().findGitRoot(mCurrentDir);
            if (repoRoot != null && mCurrentGitState != null) {
                String gitStatusText = getGitStatusForFile(repoRoot, entry.getFile(), mCurrentGitState);
                if (gitStatusText != null && !gitStatusText.isEmpty()) {
                    details = details + " • " + gitStatusText;
                }
            }
            tvDetails.setText(details);

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
                    if (mPickerSelectionListener != null) {
                        FileSelectionListener pickerListener = mPickerSelectionListener;
                        mPickerSelectionListener = null;
                        setBottomBarVisible(false);
                        if (mBtnBottomBarOk != null) {
                            mBtnBottomBarOk.setVisibility(View.VISIBLE);
                        }
                        pickerListener.onFileSelected(entry.getFile());
                    } else if (mSelectionListener != null) {
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
        checkAndUpdateGitStatus();

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

    private void updateGitChangesBar(GitRepositoryState state) {
        mCurrentGitState = state;
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (state == null) {
                if (mTvGitChangesLabel != null) {
                    mTvGitChangesLabel.setText("Changes | +0, -0");
                }
                return;
            }
            if (mTvGitChangesLabel != null) {
                mTvGitChangesLabel.setText("Changes | +" + state.totalAdditions + ", -" + state.totalDeletions);
            }
        });
    }

    @Override
    public void onGitStateChanged(GitRepositoryState state) {
        if (mCurrentDir == null || state == null || state.repoRoot == null) return;
        File repoRoot = GitManager.getInstance().findGitRoot(mCurrentDir);
        if (repoRoot != null && repoRoot.getAbsolutePath().equals(state.repoRoot.getAbsolutePath())) {
            updateGitChangesBar(state);
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::refreshDirectoryViews);
            }
        }
    }

    private void refreshDirectoryViews() {
        if (mCurrentDir != null && getContext() != null) {
            List<FileEntry> items = fetchDirectoryItems(mCurrentDir);
            View newView = buildDirectoryView(items);
            if (newView != null) {
                mFlContentContainer.removeAllViews();
                mFlContentContainer.addView(newView);
            }
        }
    }

    private String getGitStatusForFile(File repoRoot, File itemFile, GitRepositoryState state) {
        if (repoRoot == null || itemFile == null || state == null) return null;
        try {
            String relPath = repoRoot.toPath().relativize(itemFile.toPath()).toString().replace('\\', '/');
            if (relPath.isEmpty()) return null;

            if (itemFile.isFile()) {
                for (GitStatusView.GitFileStatus f : state.stagedFiles) {
                    if (relPath.equals(f.filePath)) {
                        return "Staged (+" + f.additions + ", -" + f.deletions + ")";
                    }
                }
                for (GitStatusView.GitFileStatus f : state.unstagedFiles) {
                    if (relPath.equals(f.filePath)) {
                        if ("??".equals(f.status)) {
                            return "Untracked (+" + f.additions + ")";
                        }
                        return "Modified (+" + f.additions + ", -" + f.deletions + ")";
                    }
                }
            } else if (itemFile.isDirectory()) {
                String dirPrefix = relPath + "/";
                int totalAdd = 0;
                int totalDel = 0;
                boolean hasChanges = false;

                for (GitStatusView.GitFileStatus f : state.stagedFiles) {
                    if (f.filePath != null && f.filePath.startsWith(dirPrefix)) {
                        totalAdd += f.additions;
                        totalDel += f.deletions;
                        hasChanges = true;
                    }
                }
                for (GitStatusView.GitFileStatus f : state.unstagedFiles) {
                    if (f.filePath != null && f.filePath.startsWith(dirPrefix)) {
                        totalAdd += f.additions;
                        totalDel += f.deletions;
                        hasChanges = true;
                    }
                }
                if (hasChanges) {
                    return "Modified (+" + totalAdd + ", -" + totalDel + ")";
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void onDestroyView() {
        cancelFilePickerMode();
        super.onDestroyView();
        GitManager.getInstance().removeStateChangeListener(this);
    }

    private void preloadGitView() {
        if (mFlGitViewContainer == null || mCurrentDir == null || getContext() == null) return;
        File repoRoot = GitManager.getInstance().findGitRoot(mCurrentDir);
        if (repoRoot == null) return;

        if (mGitStatusView == null) {
            mGitStatusView = new GitStatusView(requireContext());
            mGitStatusView.setOnCloseListener(this::closeGitView);
            mGitStatusView.setOnStatusUpdatedListener(() -> {
                if (mGitStatusView.getLastState() != null) {
                    updateGitChangesBar(mGitStatusView.getLastState());
                }
            });
            mFlGitViewContainer.addView(mGitStatusView);
        }
        mGitStatusView.setRepoDir(repoRoot);
    }

    private void checkAndUpdateGitStatus() {
        if (mCurrentDir == null || getContext() == null) return;

        File repoRoot = GitManager.getInstance().findGitRoot(mCurrentDir);
        if (repoRoot == null) {
            if (mLayoutGitChangesBar != null) mLayoutGitChangesBar.setVisibility(View.GONE);
            closeGitView();
            return;
        }

        if (mLayoutGitChangesBar != null) {
            mLayoutGitChangesBar.setVisibility(View.VISIBLE);
        }

        preloadGitView();

        mCurrentGitState = null;
        updateGitChangesBar(null);

        GitManager.getInstance().refreshRepositoryState(getContext(), repoRoot, this::updateGitChangesBar);
    }

    private int getGitContainerHeight() {
        if (mFlGitViewContainer == null) return 1000;
        int h = mFlGitViewContainer.getHeight();
        if (h > 0) return h;
        if (mFlGitViewContainer.getParent() instanceof View) {
            h = ((View) mFlGitViewContainer.getParent()).getHeight();
            if (h > 0) return h;
        }
        h = mFlGitViewContainer.getMeasuredHeight();
        if (h > 0) return h;
        return 1000;
    }

    private void toggleGitView() {
        if (mIsGitViewOpen) {
            closeGitView();
        } else {
            openGitView();
        }
    }

    private void openGitView() {
        if (mFlGitViewContainer == null || mCurrentDir == null) return;
        mIsGitViewOpen = true;
        preloadGitView();
        mFlGitViewContainer.setVisibility(View.VISIBLE);

        mFlGitViewContainer.animate().cancel();
        if (mGitStatusView != null) {
            mGitStatusView.setTranslationY(0);
        }
        
        int height = getGitContainerHeight();

        if (mFlGitViewContainer.getTranslationY() >= height) {
            mFlGitViewContainer.setTranslationY(height);
        }

        mFlGitViewContainer.animate()
                .translationY(0)
                .setDuration(200)
                .start();

        if (mTvGitChangesChevron != null) {
            mTvGitChangesChevron.setText("▼");
        }
    }

    private void closeGitView() {
        mIsGitViewOpen = false;
        if (mFlGitViewContainer != null) {
            mFlGitViewContainer.animate().cancel();
            if (mGitStatusView != null) {
                mGitStatusView.setTranslationY(0);
            }
            int height = getGitContainerHeight();

            mFlGitViewContainer.animate()
                    .translationY(height)
                    .setDuration(200)
                    .withEndAction(() -> {
                        if (!mIsGitViewOpen && mFlGitViewContainer != null) {
                            mFlGitViewContainer.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
        if (mTvGitChangesChevron != null) {
            mTvGitChangesChevron.setText("▲");
        }
    }

    public void loadDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory() || !isWithinRootDir(dir)) {
            Toast.makeText(getContext(), "Cannot access directory outside Termux root", Toast.LENGTH_SHORT).show();
            return;
        }

        mCurrentDir = dir;
        updatePathHeader();
        checkAndUpdateGitStatus();

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

    private void showAddMenu(View anchor) {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault);
        PopupMenu popup = new PopupMenu(themedContext, anchor);

        popup.getMenu().add(0, 0, 0, "New File");
        popup.getMenu().add(0, 1, 1, "New Directory");
        popup.getMenu().add(0, 2, 2, "New repository");
        popup.getMenu().add(0, 3, 3, "Clone Git Repo...");

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 0) {
                showCreateFileDialog();
                return true;
            } else if (menuItem.getItemId() == 1) {
                showCreateFolderDialog();
                return true;
            } else if (menuItem.getItemId() == 2) {
                showCreateRepositoryDialog();
                return true;
            } else if (menuItem.getItemId() == 3) {
                showCloneRepositoryDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showCloneRepositoryDialog() {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Clone Git Repository");

        final EditText input = new EditText(themedContext);
        input.setHint("Repository URL (https://... or git@...)");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x88FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        int paddingPx = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(themedContext);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Clone", (dialog, which) -> {
            String repoUrl = input.getText().toString().trim();
            if (repoUrl.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a repository URL", Toast.LENGTH_SHORT).show();
                return;
            }
            performCloneRepository(repoUrl);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void performCloneRepository(String repoUrl) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), "Cloning repository...", Toast.LENGTH_SHORT).show();

        boolean isHttps = repoUrl.toLowerCase().startsWith("http://") || repoUrl.toLowerCase().startsWith("https://");

        GitManager.getInstance().cloneRepository(getContext(), repoUrl, mCurrentDir, (success, output) -> {
            if (getActivity() == null || !isAdded()) return;
            getActivity().runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(getContext(), "Repository cloned successfully", Toast.LENGTH_SHORT).show();
                    loadDirectory(mCurrentDir);
                } else {
                    if (isHttps && GitManager.isGitAuthRequiredError(output)) {
                        showGitAuthDialog(repoUrl);
                    } else {
                        String errorMsg = (output != null && !output.trim().isEmpty()) ? output.trim() : "Unknown error";
                        Toast.makeText(getContext(), "Clone failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    private void showGitAuthDialog(String repoUrl) {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Git Authentication Required");

        String host = extractHostFromUrl(repoUrl);
        builder.setMessage("Authentication required for " + host + ". Please enter your credentials.");

        LinearLayout layout = new LinearLayout(themedContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        layout.setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2);

        final EditText inputUsername = new EditText(themedContext);
        inputUsername.setHint("Username");
        inputUsername.setTextColor(0xFFFFFFFF);
        inputUsername.setHintTextColor(0x88FFFFFF);
        inputUsername.setInputType(InputType.TYPE_CLASS_TEXT);

        final EditText inputPassword = new EditText(themedContext);
        inputPassword.setHint("Password or Personal Access Token");
        inputPassword.setTextColor(0xFFFFFFFF);
        inputPassword.setHintTextColor(0x88FFFFFF);
        inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(inputUsername);
        layout.addView(inputPassword);
        builder.setView(layout);

        builder.setPositiveButton("Clone", (dialog, which) -> {
            String username = inputUsername.getText().toString().trim();
            String password = inputPassword.getText().toString().trim();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "Username and Password/Token are required", Toast.LENGTH_SHORT).show();
                return;
            }

            String authUrl = buildAuthenticatedUrl(repoUrl, username, password);
            Toast.makeText(getContext(), "Cloning repository...", Toast.LENGTH_SHORT).show();

            GitManager.getInstance().cloneRepository(getContext(), authUrl, mCurrentDir, (success, output) -> {
                if (getActivity() == null || !isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(getContext(), "Repository cloned successfully", Toast.LENGTH_SHORT).show();
                        loadDirectory(mCurrentDir);
                    } else {
                        String errorMsg = (output != null && !output.trim().isEmpty()) ? output.trim() : "Authentication failed";
                        Toast.makeText(getContext(), "Clone failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private String buildAuthenticatedUrl(String repoUrl, String username, String password) {
        try {
            String encodedUser = Uri.encode(username);
            String encodedPass = Uri.encode(password);

            String lower = repoUrl.toLowerCase();
            if (lower.startsWith("https://")) {
                String rest = repoUrl.substring("https://".length());
                if (rest.contains("@")) {
                    rest = rest.substring(rest.indexOf('@') + 1);
                }
                return "https://" + encodedUser + ":" + encodedPass + "@" + rest;
            } else if (lower.startsWith("http://")) {
                String rest = repoUrl.substring("http://".length());
                if (rest.contains("@")) {
                    rest = rest.substring(rest.indexOf('@') + 1);
                }
                return "http://" + encodedUser + ":" + encodedPass + "@" + rest;
            }
        } catch (Exception ignored) {}
        return repoUrl;
    }

    private String extractHostFromUrl(String repoUrl) {
        if (repoUrl == null) return "repository";
        try {
            Uri uri = Uri.parse(repoUrl);
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri.getHost();
            }
        } catch (Exception ignored) {}
        return "repository";
    }

    private void showCreateFileDialog() {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Create New File");

        final EditText input = new EditText(themedContext);
        input.setHint("File name");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x88FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                File newFile = new File(mCurrentDir, fileName);
                if (newFile.exists()) {
                    Toast.makeText(getContext(), "File already exists", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        if (newFile.createNewFile()) {
                            Toast.makeText(getContext(), "File created", Toast.LENGTH_SHORT).show();
                            loadDirectory(mCurrentDir);
                            if (mPickerSelectionListener != null) {
                                FileSelectionListener pickerListener = mPickerSelectionListener;
                                mPickerSelectionListener = null;
                                setBottomBarVisible(false);
                                if (mBtnBottomBarOk != null) {
                                    mBtnBottomBarOk.setVisibility(View.VISIBLE);
                                }
                                pickerListener.onFileSelected(newFile);
                            } else if (mSelectionListener != null) {
                                mSelectionListener.onFileSelected(newFile);
                            }
                        } else {
                            Toast.makeText(getContext(), "Failed to create file", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        Toast.makeText(getContext(), "Error creating file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showCreateRepositoryDialog() {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Create New Repository");

        final EditText input = new EditText(themedContext);
        input.setHint("Repository name");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x88FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String repoName = input.getText().toString().trim();
            if (!repoName.isEmpty()) {
                File newDir = new File(mCurrentDir, repoName);
                if (newDir.exists()) {
                    Toast.makeText(getContext(), "Directory already exists", Toast.LENGTH_SHORT).show();
                } else {
                    if (newDir.mkdirs()) {
                        Context ctx = getContext();
                        if (ctx != null) {
                            GitManager.getInstance().initRepository(ctx, newDir, (success, output) -> {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (success) {
                                            Toast.makeText(getContext(), "Repository initialized", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(getContext(), "Failed to initialize git repository", Toast.LENGTH_SHORT).show();
                                        }
                                        loadDirectory(mCurrentDir);
                                    });
                                }
                            });
                        }
                        Toast.makeText(getContext(), "Creating repository...", Toast.LENGTH_SHORT).show();
                        loadDirectory(mCurrentDir);
                    } else {
                        Toast.makeText(getContext(), "Failed to create repository", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showCreateFolderDialog() {
        if (getContext() == null) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
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
                        navigateToDirectory(newDir, true);
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
