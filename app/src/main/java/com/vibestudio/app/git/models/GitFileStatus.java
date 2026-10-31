package com.vibestudio.app.git.models;

public class GitFileStatus {
    public String status;
    public String filePath;
    public boolean isStaged;
    public int additions;
    public int deletions;

    public GitFileStatus(String status, String filePath, boolean isStaged) {
        this.status = status;
        this.filePath = filePath;
        this.isStaged = isStaged;
    }
}
