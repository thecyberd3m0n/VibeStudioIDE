package com.vibestudio.app.git.callbacks;

import com.vibestudio.app.git.models.GitFileStatus;
import java.util.List;

public interface CommitFilesCallback {
    void onResult(boolean success, List<GitFileStatus> files);
}
