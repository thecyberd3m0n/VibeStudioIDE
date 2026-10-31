package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vibestudio.app.R;
import com.vibestudio.app.util.FontUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Log lines with a per-line selection, so whole lines can be picked and copied. */
public class LogLineAdapter extends RecyclerView.Adapter<LogLineAdapter.LogViewHolder> {

    public interface SelectionListener {
        void onSelectionChanged(int selectedCount);
    }

    private static final int SELECTED_COLOR = Color.parseColor("#3303DAC6");

    private final List<String> mLogLines = new ArrayList<>();
    /** Sorted, so the selected lines are copied in log order. */
    private final Set<Integer> mSelected = new TreeSet<>();
    private final Context mContext;
    private SelectionListener mSelectionListener;

    public LogLineAdapter(Context context) {
        mContext = context;
    }

    public void setSelectionListener(SelectionListener listener) {
        mSelectionListener = listener;
    }

    public void setLines(List<String> lines) {
        mLogLines.clear();
        if (lines != null) mLogLines.addAll(lines);
        mSelected.clear();
        notifyDataSetChanged();
        notifySelection();
    }

    public void appendLine(String line) {
        mLogLines.add(line);
        notifyItemInserted(mLogLines.size() - 1);
    }

    public void clear() {
        mLogLines.clear();
        mSelected.clear();
        notifyDataSetChanged();
        notifySelection();
    }

    public String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (String line : mLogLines) sb.append(line).append("\n");
        return sb.toString();
    }

    public boolean hasSelection() {
        return !mSelected.isEmpty();
    }

    public int getSelectedCount() {
        return mSelected.size();
    }

    /** @return the first selected line, or -1 */
    public int getFirstSelected() {
        return mSelected.isEmpty() ? -1 : mSelected.iterator().next();
    }

    public String getSelectedText() {
        StringBuilder sb = new StringBuilder();
        for (int position : mSelected) {
            if (position < mLogLines.size()) sb.append(mLogLines.get(position)).append("\n");
        }
        return sb.toString();
    }

    /** @return whether the line is selected afterwards */
    public boolean toggle(int position) {
        boolean selected = !mSelected.contains(position);
        setSelected(position, selected);
        return selected;
    }

    public void setSelected(int position, boolean selected) {
        if (position < 0 || position >= mLogLines.size()) return;
        if (selected ? mSelected.add(position) : mSelected.remove(position)) {
            notifyItemChanged(position);
            notifySelection();
        }
    }

    public void selectAll() {
        for (int i = 0; i < mLogLines.size(); i++) mSelected.add(i);
        notifyDataSetChanged();
        notifySelection();
    }

    public void clearSelection() {
        if (mSelected.isEmpty()) return;
        mSelected.clear();
        notifyDataSetChanged();
        notifySelection();
    }

    private void notifySelection() {
        if (mSelectionListener != null) mSelectionListener.onSelectionChanged(mSelected.size());
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_line, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.mTvLine.setText(mLogLines.get(position));
        holder.itemView.setBackgroundColor(mSelected.contains(position) ? SELECTED_COLOR : Color.TRANSPARENT);
        if (mContext != null) {
            holder.mTvLine.setTypeface(FontUtils.getMonospaceTypeface(mContext));
        }
    }

    @Override
    public int getItemCount() {
        return mLogLines.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        final TextView mTvLine;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            mTvLine = itemView.findViewById(R.id.tv_log_line);
        }
    }
}
