package com.vibestudio.app.git.callbacks;

import com.vibestudio.app.git.models.GitCommit;

public interface GitCommitCallback {
    void onResult(GitCommit commit);
}
