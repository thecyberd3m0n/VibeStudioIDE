package com.vibestudio.app.chat.skills;

import java.util.Objects;

public class Skill {
    private final String mName;
    private final String mDescription;
    private final String mContent;
    private final String mSourcePath;

    public Skill(String name, String description, String content, String sourcePath) {
        this.mName = name != null ? name.trim() : "";
        this.mDescription = description != null ? description.trim() : "";
        this.mContent = content != null ? content.trim() : "";
        this.mSourcePath = sourcePath != null ? sourcePath.trim() : "";
    }

    public String getName() {
        return mName;
    }

    public String getSlashCommand() {
        return "/" + mName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getContent() {
        return mContent;
    }

    public String getSourcePath() {
        return mSourcePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Skill skill = (Skill) o;
        return Objects.equals(mName, skill.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName);
    }

    @Override
    public String toString() {
        return "Skill{" +
                "name='" + mName + '\'' +
                ", description='" + mDescription + '\'' +
                ", sourcePath='" + mSourcePath + '\'' +
                '}';
    }
}
