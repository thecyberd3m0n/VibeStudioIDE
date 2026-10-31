package com.vibestudio.app.git.callbacks;

import com.vibestudio.app.git.models.GitCommit;

public interface OnCommitClickListener {
    void onCommitClick(GitCommit commit);
}
