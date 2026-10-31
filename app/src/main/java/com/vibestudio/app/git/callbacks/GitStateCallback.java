package com.vibestudio.app.git.callbacks;

import com.vibestudio.app.git.models.GitRepositoryState;

public interface GitStateCallback {
    void onStateUpdated(GitRepositoryState state);
}
