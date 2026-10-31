package com.vibestudio.app.git.models;

/** A commit as listed by `git log`, with the origin remote it was read from (used to look up avatars). */
public class GitCommit {
    public final String hash;
    public final String shortHash;
    public final String author;
    public final String email;
    public final String relativeTime;
    public final String subject;
    public final String remoteUrl;

    public GitCommit(String hash, String shortHash, String author, String email, String relativeTime, String subject, String remoteUrl) {
        this.hash = hash;
        this.shortHash = shortHash;
        this.author = author;
        this.email = email;
        this.relativeTime = relativeTime;
        this.subject = subject;
        this.remoteUrl = remoteUrl;
    }
}
