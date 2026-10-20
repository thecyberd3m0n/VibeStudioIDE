package com.vibestudio.app.git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GitRepositoryState {
    public File repoRoot;
    public String branchName = "";
    public int ahead = 0;
    public int behind = 0;
    public List<GitStatusView.GitFileStatus> stagedFiles = new ArrayList<>();
    public List<GitStatusView.GitFileStatus> unstagedFiles = new ArrayList<>();
    public int totalAdditions = 0;
    public int totalDeletions = 0;
    public boolean isValid = true;
    public long timestamp = System.currentTimeMillis();

    public GitRepositoryState(File repoRoot) {
        this.repoRoot = repoRoot;
    }

    public boolean hasChanges() {
        return !stagedFiles.isEmpty() || !unstagedFiles.isEmpty();
    }

    public boolean needsSync() {
        return ahead > 0 || behind > 0;
    }
}
