package com.vibestudio.app.git;

import com.vibestudio.app.git.callbacks.OnCommitClickListener;
import com.vibestudio.app.git.models.GitCommit;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Rows of the History view: avatar, subject and "author · hash · time". */
public class CommitHistoryAdapter extends RecyclerView.Adapter<CommitHistoryAdapter.Holder> {
    private final List<GitCommit> mCommits = new ArrayList<>();
    private String mRemoteUrl = "";
    private OnCommitClickListener mClickListener;

    public void setOnCommitClickListener(OnCommitClickListener listener) {
        mClickListener = listener;
    }

    public void setRemoteUrl(String remoteUrl) {
        mRemoteUrl = remoteUrl != null ? remoteUrl : "";
    }

    public void clear() {
        mCommits.clear();
        notifyDataSetChanged();
    }

    public void append(List<GitCommit> commits) {
        int start = mCommits.size();
        mCommits.addAll(commits);
        notifyItemRangeInserted(start, commits.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        float d = context.getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            row.setBackgroundResource(ripple.resourceId);
        }
        row.setPadding((int) (16 * d), (int) (10 * d), (int) (16 * d), (int) (10 * d));

        ImageView avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams((int) (28 * d), (int) (28 * d));
        avatarParams.setMarginEnd((int) (10 * d));
        row.addView(avatar, avatarParams);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView subject = new TextView(context);
        subject.setTextColor(Color.parseColor("#E0E0E0"));
        subject.setTextSize(13);
        subject.setMaxLines(2);
        subject.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(subject);

        TextView meta = new TextView(context);
        meta.setTextColor(Color.parseColor("#808090"));
        meta.setTextSize(11);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(meta);

        return new Holder(row, avatar, subject, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GitCommit commit = mCommits.get(position);
        holder.itemView.setOnClickListener(v -> {
            if (mClickListener != null) mClickListener.onCommitClick(commit);
        });
        holder.subject.setText(commit.subject);
        holder.meta.setText(commit.author + " · " + commit.shortHash + " · " + commit.relativeTime);

        int size = holder.avatar.getLayoutParams().width;
        holder.avatar.setImageBitmap(AvatarLoader.initialsBitmap(commit.author, size));
        holder.boundHash = commit.hash;
        AvatarLoader.load(commit.email, mRemoteUrl, commit.hash, bitmap -> {
            if (bitmap == null || !commit.hash.equals(holder.boundHash)) return;
            RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(holder.avatar.getResources(), bitmap);
            drawable.setCircular(true);
            holder.avatar.setImageDrawable(drawable);
        });
    }

    @Override
    public int getItemCount() {
        return mCommits.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView subject;
        final TextView meta;
        String boundHash;

        Holder(View itemView, ImageView avatar, TextView subject, TextView meta) {
            super(itemView);
            this.avatar = avatar;
            this.subject = subject;
            this.meta = meta;
        }
    }
}
