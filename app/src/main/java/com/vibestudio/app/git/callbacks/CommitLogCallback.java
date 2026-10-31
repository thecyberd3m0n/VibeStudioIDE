package com.vibestudio.app.git.callbacks;

import com.vibestudio.app.git.models.GitCommit;
import java.util.List;

public interface CommitLogCallback {
    void onResult(boolean success, List<GitCommit> commits);
}
