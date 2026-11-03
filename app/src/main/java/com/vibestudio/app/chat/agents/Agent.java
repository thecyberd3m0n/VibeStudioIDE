package com.vibestudio.app.chat.agents;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A custom agent definition loaded from {@code .github/agents} or {@code .claude/agents}. */
public class Agent {
    private final String mName;
    private final String mDescription;
    private final String mInstructions;
    private final List<String> mTools;
    private final String mModel;
    private final String mSourcePath;
    private final boolean mUserInvocable;
    private final boolean mModelInvocable;

    public Agent(String name, String description, String instructions, List<String> tools, String model,
                 String sourcePath, boolean userInvocable, boolean modelInvocable) {
        this.mName = name != null ? name.trim() : "";
        this.mDescription = description != null ? description.trim() : "";
        this.mInstructions = instructions != null ? instructions.trim() : "";
        this.mTools = tools != null ? Collections.unmodifiableList(tools) : null;
        this.mModel = model;
        this.mSourcePath = sourcePath != null ? sourcePath : "";
        this.mUserInvocable = userInvocable;
        this.mModelInvocable = modelInvocable;
    }

    public String getName() {
        return mName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getInstructions() {
        return mInstructions;
    }

    /** Declared tool allow-list, or null when the agent does not restrict tools. */
    public List<String> getTools() {
        return mTools;
    }

    /** First model preference from frontmatter, or null. Informational until per-session models exist. */
    public String getModel() {
        return mModel;
    }

    public String getSourcePath() {
        return mSourcePath;
    }

    public boolean isUserInvocable() {
        return mUserInvocable;
    }

    public boolean isModelInvocable() {
        return mModelInvocable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(mName, ((Agent) o).mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName);
    }

    @Override
    public String toString() {
        return "Agent{name='" + mName + "', description='" + mDescription + "', sourcePath='" + mSourcePath + "'}";
    }
}
