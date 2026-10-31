package com.vibestudio.app.git.models;

import java.util.List;

public final class ConflictRegion {
    /** 0-based line indexes of the marker lines. {@code baseLine} is -1 unless diff3 style is used. */
    public final int startLine;
    public final int baseLine;
    public final int separatorLine;
    public final int endLine;
    public final String oursLabel;
    public final String theirsLabel;
    public final List<String> oursLines;
    public final List<String> baseLines;
    public final List<String> theirsLines;

    /** Which sides are accepted. A region counts as resolved once at least one side is. */
    public boolean acceptOurs;
    public boolean acceptTheirs;

    public ConflictRegion(int startLine, int baseLine, int separatorLine, int endLine, String oursLabel, String theirsLabel,
                          List<String> oursLines, List<String> baseLines, List<String> theirsLines) {
        this.startLine = startLine;
        this.baseLine = baseLine;
        this.separatorLine = separatorLine;
        this.endLine = endLine;
        this.oursLabel = oursLabel;
        this.theirsLabel = theirsLabel;
        this.oursLines = oursLines;
        this.baseLines = baseLines;
        this.theirsLines = theirsLines;
    }

    public boolean isResolved() {
        return acceptOurs || acceptTheirs;
    }

    /** Last line (exclusive) of the "ours" block, which includes its start marker. */
    public int oursEndExclusive() {
        return baseLine >= 0 ? baseLine : separatorLine;
    }

    public String signature() {
        return String.join("\n", oursLines) + "\u0000" + String.join("\n", theirsLines);
    }
}
