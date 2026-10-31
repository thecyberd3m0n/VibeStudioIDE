package com.vibestudio.app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.R;
import com.vibestudio.app.git.GitManager;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.logging.SessionLogger;
import com.vibestudio.app.util.FontUtils;

import java.util.ArrayList;
import java.util.List;

public class LogViewerFragment extends Fragment implements LogViewerService.OnLogListener, SessionLogger.Listener {

    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

    private enum Mode {
        PROCESS_LIST,
        GLOBAL_LOGCAT,
        SESSION_CHANNEL
    }

    private View mContainerProcessList;
    private View mContainerLogDetail;
    private LinearLayout mLlProcessContainer;

    private TextView mTvLogDetailTitle;
    private RecyclerView mRvLogContent;
    private LogAdapter mLogAdapter;
    private LinearLayoutManager mLayoutManager;
    private Button mBtnBack;
    private Button mBtnCopy;
    private Button mBtnClear;

    private Mode mCurrentMode = Mode.PROCESS_LIST;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private LogViewerService mLogService;
    private SessionLogger mCurrentChannel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log_viewer, container, false);

        mContainerProcessList = view.findViewById(R.id.container_process_list);
        mContainerLogDetail = view.findViewById(R.id.container_log_detail);
        mLlProcessContainer = view.findViewById(R.id.ll_process_container);

        mTvLogDetailTitle = view.findViewById(R.id.tv_log_detail_title);
        mRvLogContent = view.findViewById(R.id.rv_log_content);
        if (mRvLogContent != null) {
            mLayoutManager = new LinearLayoutManager(getContext());
            mRvLogContent.setLayoutManager(mLayoutManager);
            mLogAdapter = new LogAdapter(getContext());
            mRvLogContent.setAdapter(mLogAdapter);
        }

        mBtnBack = view.findViewById(R.id.btn_back_to_processes);
        mBtnCopy = view.findViewById(R.id.btn_copy_logs);
        mBtnClear = view.findViewById(R.id.btn_clear_logs);

        mLogService = LogViewerService.getInstance();

        if (mBtnBack != null) {
            mBtnBack.setOnClickListener(v -> showProcessList());
        }

        if (mBtnCopy != null) {
            mBtnCopy.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null && mLogAdapter != null) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    String label = mCurrentMode == Mode.GLOBAL_LOGCAT || mCurrentChannel == null
                            ? "VibeStudio Logcat" : "VibeStudio " + mCurrentChannel.getLabel();
                    ClipData clip = ClipData.newPlainText(label, mLogAdapter.getAllText());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (mBtnClear != null) {
            mBtnClear.setOnClickListener(v -> {
                if (mCurrentMode == Mode.GLOBAL_LOGCAT) {
                    mLogService.clear();
                } else if (mCurrentMode == Mode.SESSION_CHANNEL && mCurrentChannel != null) {
                    mCurrentChannel.clear();
                }
            });
        }

        showProcessList();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mCurrentMode == Mode.GLOBAL_LOGCAT || mCurrentMode == Mode.SESSION_CHANNEL) {
            reloadCurrentLogDetail();
            attachListeners();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        detachListeners();
    }

    private void showProcessList() {
        mCurrentMode = Mode.PROCESS_LIST;
        detachListeners();
        mCurrentChannel = null;
        if (mContainerProcessList != null) mContainerProcessList.setVisibility(View.VISIBLE);
        if (mContainerLogDetail != null) mContainerLogDetail.setVisibility(View.GONE);

        renderProcessList();
    }

    private void renderProcessList() {
        if (mLlProcessContainer == null || getContext() == null) return;
        mLlProcessContainer.removeAllViews();

        Context context = getContext();

        // 1. Global Logcat Process Entry
        int globalPid = Process.myPid();
        String globalPath = context.getFilesDir() != null ? context.getFilesDir().getAbsolutePath() : "App Process";
        addProcessCard(context, "Global Logcat", "System & app stdout/stderr (~5KB max)",
                globalPid, globalPath, true, () -> openLogDetail(Mode.GLOBAL_LOGCAT));

        // 2. One entry per managed PTY session; GitManager registers its channel on first use.
        GitManager.getInstance().getGitLogger();
        for (SessionLogger channel : mLogService.getChannels()) {
            boolean isGit = GitManager.GIT_SESSION_LABEL.equals(channel.getLabel());
            TerminalSession session = isGit ? GitManager.getInstance().getGitSession() : null;
            boolean running = session != null && session.isRunning();
            int pid = running ? session.getPid() : -1;
            String cwd = session != null && session.getCwd() != null ? session.getCwd() : "N/A";
            addProcessCard(context, channel.getLabel(), "PTY stdin/stdout and command log",
                    pid, cwd, running, () -> openChannelDetail(channel));
        }
    }

    private void addProcessCard(Context context, String label, String description,
                                int pid, String path, boolean isActive, Runnable onClick) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#1E1E2E"));
        cardBg.setCornerRadius(16f);
        cardBg.setStroke(2, Color.parseColor("#2E2E3E"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cardParams);

        // Header Row: Label & Status Badge
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#BB86FC"));
        tvLabel.setTextSize(16);
        tvLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvStatus = new TextView(context);
        if (isActive) {
            tvStatus.setText("● Active");
            tvStatus.setTextColor(Color.parseColor("#03DAC6"));
        } else {
            tvStatus.setText("● Idle");
            tvStatus.setTextColor(Color.parseColor("#8E8EA0"));
        }
        tvStatus.setTextSize(12);
        tvStatus.setPadding(8, 0, 0, 0);

        headerRow.addView(tvLabel);
        headerRow.addView(tvStatus);
        card.addView(headerRow);

        // Description
        TextView tvDesc = new TextView(context);
        tvDesc.setText(description);
        tvDesc.setTextColor(Color.parseColor("#CDD6F4"));
        tvDesc.setTextSize(12);
        tvDesc.setPadding(0, 8, 0, 4);
        card.addView(tvDesc);

        // Details Row: PID & Path
        LinearLayout detailsRow = new LinearLayout(context);
        detailsRow.setOrientation(LinearLayout.HORIZONTAL);
        detailsRow.setPadding(0, 6, 0, 0);

        TextView tvPid = new TextView(context);
        tvPid.setText("PID: " + (pid > 0 ? pid : "N/A"));
        tvPid.setTextColor(Color.parseColor("#8E8EA0"));
        tvPid.setTextSize(11);
        tvPid.setTypeface(FontUtils.getMonospaceTypeface(context));

        TextView tvPath = new TextView(context);
        tvPath.setText("  •  Path: " + path);
        tvPath.setTextColor(Color.parseColor("#8E8EA0"));
        tvPath.setTextSize(11);
        tvPath.setSingleLine(true);
        tvPath.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        tvPath.setLayoutParams(pathParams);

        detailsRow.addView(tvPid);
        detailsRow.addView(tvPath);
        card.addView(detailsRow);

        card.setOnClickListener(v -> onClick.run());
        mLlProcessContainer.addView(card);
    }

    private void openLogDetail(Mode mode) {
        mCurrentMode = mode;
        showDetailContainer();
        reloadCurrentLogDetail();
        attachListeners();
    }

    private void openChannelDetail(SessionLogger channel) {
        mCurrentChannel = channel;
        openLogDetail(Mode.SESSION_CHANNEL);
    }

    private void showDetailContainer() {
        if (mContainerProcessList != null) mContainerProcessList.setVisibility(View.GONE);
        if (mContainerLogDetail != null) mContainerLogDetail.setVisibility(View.VISIBLE);
    }

    private void attachListeners() {
        if (mCurrentMode == Mode.GLOBAL_LOGCAT && mLogService != null) {
            mLogService.addListener(this);
        } else if (mCurrentMode == Mode.SESSION_CHANNEL && mCurrentChannel != null) {
            mCurrentChannel.addListener(this);
        }
    }

    private void detachListeners() {
        if (mLogService != null) mLogService.removeListener(this);
        if (mCurrentChannel != null) mCurrentChannel.removeListener(this);
    }

    private void reloadCurrentLogDetail() {
        if (mCurrentMode == Mode.GLOBAL_LOGCAT) {
            if (mTvLogDetailTitle != null) {
                mTvLogDetailTitle.setText("Global Logcat (~5KB Max)");
            }
            if (mLogAdapter != null && mLogService != null) {
                mLogAdapter.setLines(mLogService.getLogList());
            }
            scrollToBottom();
        } else if (mCurrentMode == Mode.SESSION_CHANNEL && mCurrentChannel != null) {
            if (mTvLogDetailTitle != null) {
                mTvLogDetailTitle.setText(mCurrentChannel.getLabel());
            }
            mCurrentChannel.flush();
            if (mLogAdapter != null) {
                mLogAdapter.setLines(mCurrentChannel.getEntries());
            }
            scrollToBottom();
        }
    }

    @Override
    public void onLogAdded(String logEntry) {
        appendEntry(logEntry);
    }

    @Override
    public void onEntry(String entry) {
        appendEntry(entry);
    }

    private void appendEntry(String entry) {
        mHandler.post(() -> {
            if (mLogAdapter != null) {
                mLogAdapter.appendLine(entry);
                scrollToBottom();
            }
        });
    }

    @Override
    public void onLogsCleared() {
        mHandler.post(() -> {
            if (mLogAdapter != null) {
                mLogAdapter.clear();
            }
        });
    }

    @Override
    public void onCleared() {
        onLogsCleared();
    }

    private void scrollToBottom() {
        if (mRvLogContent != null && mLogAdapter != null && mLogAdapter.getItemCount() > 0) {
            mRvLogContent.post(() -> mRvLogContent.smoothScrollToPosition(mLogAdapter.getItemCount() - 1));
        }
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

        private final List<String> mLogLines = new ArrayList<>();
        private final Context mContext;

        public LogAdapter(Context context) {
            mContext = context;
        }

        public void setLines(List<String> lines) {
            mLogLines.clear();
            if (lines != null) {
                mLogLines.addAll(lines);
            }
            notifyDataSetChanged();
        }

        public void appendLine(String line) {
            mLogLines.add(line);
            notifyItemInserted(mLogLines.size() - 1);
        }

        public void clear() {
            mLogLines.clear();
            notifyDataSetChanged();
        }

        public String getAllText() {
            StringBuilder sb = new StringBuilder();
            for (String line : mLogLines) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_line, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            String text = mLogLines.get(position);
            holder.mTvLine.setText(text);
            if (mContext != null) {
                holder.mTvLine.setTypeface(FontUtils.getMonospaceTypeface(mContext));
            }
        }

        @Override
        public int getItemCount() {
            return mLogLines.size();
        }

        static class LogViewHolder extends RecyclerView.ViewHolder {
            TextView mTvLine;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                mTvLine = itemView.findViewById(R.id.tv_log_line);
            }
        }
    }
}
