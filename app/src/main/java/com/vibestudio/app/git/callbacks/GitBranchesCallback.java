package com.vibestudio.app.git.callbacks;

import java.util.List;

public interface GitBranchesCallback {
    void onResult(boolean success, List<String> branches);
}
