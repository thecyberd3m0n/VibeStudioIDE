package com.vibestudio.app.chat.skills;

import java.util.Objects;

public class Skill {
    private final String mName;
    private final String mDescription;
    private final String mContent;
    private final String mSourcePath;
    private final boolean mUserInvocable;
    private final boolean mDisableModelInvocation;

    public Skill(String name, String description, String content, String sourcePath) {
        this(name, description, content, sourcePath, true, false);
    }

    public Skill(String name, String description, String content, String sourcePath,
                 boolean userInvocable, boolean disableModelInvocation) {
        this.mName = name != null ? name.trim() : "";
        this.mDescription = description != null ? description.trim() : "";
        this.mContent = content != null ? content.trim() : "";
        this.mSourcePath = sourcePath != null ? sourcePath.trim() : "";
        this.mUserInvocable = userInvocable;
        this.mDisableModelInvocation = disableModelInvocation;
    }

    /** False when frontmatter has {@code user-invocable: false}: hidden from slash commands. */
    public boolean isUserInvocable() {
        return mUserInvocable;
    }

    /** True when frontmatter has {@code disable-model-invocation: true}: hidden from the model's skill catalog. */
    public boolean isModelInvocable() {
        return !mDisableModelInvocation;
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
