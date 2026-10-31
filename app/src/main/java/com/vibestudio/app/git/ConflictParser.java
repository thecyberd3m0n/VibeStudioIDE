package com.vibestudio.app.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds git conflict-marker regions in text and composes the resolved text from per-side choices. */
public final class ConflictParser {

    private ConflictParser() {}

    public static final class Region {
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

        Region(int startLine, int baseLine, int separatorLine, int endLine, String oursLabel, String theirsLabel,
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

        String signature() {
            return String.join("\n", oursLines) + "\u0000" + String.join("\n", theirsLines);
        }
    }

    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(stripCr(text.substring(start, i)));
                start = i + 1;
            }
        }
        if (start < text.length()) lines.add(stripCr(text.substring(start)));
        return lines;
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    public static boolean hasConflictMarkers(String text) {
        return !parse(splitLines(text)).isEmpty();
    }

    private static boolean isStart(String l) {
        return l.equals("<<<<<<<") || l.startsWith("<<<<<<< ");
    }

    private static boolean isBase(String l) {
        return l.equals("|||||||") || l.startsWith("||||||| ");
    }

    private static boolean isSeparator(String l) {
        return l.equals("=======");
    }

    private static boolean isEnd(String l) {
        return l.equals(">>>>>>>") || l.startsWith(">>>>>>> ");
    }

    private static String labelOf(String marker) {
        return marker.length() > 8 ? marker.substring(8).trim() : "";
    }

    /** Well-formed regions only; a start marker without its separator and end marker is ignored. */
    public static List<Region> parse(List<String> lines) {
        List<Region> regions = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            if (!isStart(lines.get(i))) {
                i++;
                continue;
            }
            int start = i;
            int base = -1;
            int separator = -1;
            int end = -1;
            int j = i + 1;
            boolean malformed = false;
            for (; j < lines.size(); j++) {
                String l = lines.get(j);
                if (isStart(l)) {
                    malformed = true;
                    break;
                }
                if (isBase(l) && separator < 0 && base < 0) {
                    base = j;
                } else if (isSeparator(l) && separator < 0) {
                    separator = j;
                } else if (isEnd(l)) {
                    if (separator >= 0) end = j;
                    else malformed = true;
                    break;
                }
            }
            if (malformed || end < 0) {
                i = malformed ? j : start + 1;
                continue;
            }
            int oursEnd = base >= 0 ? base : separator;
            List<String> ours = new ArrayList<>(lines.subList(start + 1, oursEnd));
            List<String> baseLines = base >= 0 ? new ArrayList<>(lines.subList(base + 1, separator)) : new ArrayList<>();
            List<String> theirs = new ArrayList<>(lines.subList(separator + 1, end));
            regions.add(new Region(start, base, separator, end, labelOf(lines.get(start)), labelOf(lines.get(end)),
                    ours, baseLines, theirs));
            i = end + 1;
        }
        return regions;
    }

    /**
     * Carries accepted sides over from the previous parse after the user edited the text. Regions with the same
     * content keep their choice; when the count is unchanged the choice follows the position.
     */
    public static void carryOverChoices(List<Region> previous, List<Region> current) {
        if (previous == null || previous.isEmpty() || current.isEmpty()) return;
        if (previous.size() == current.size()) {
            for (int i = 0; i < current.size(); i++) {
                current.get(i).acceptOurs = previous.get(i).acceptOurs;
                current.get(i).acceptTheirs = previous.get(i).acceptTheirs;
            }
            return;
        }
        boolean[] used = new boolean[previous.size()];
        for (Region region : current) {
            for (int p = 0; p < previous.size(); p++) {
                if (!used[p] && previous.get(p).signature().equals(region.signature())) {
                    region.acceptOurs = previous.get(p).acceptOurs;
                    region.acceptTheirs = previous.get(p).acceptTheirs;
                    used[p] = true;
                    break;
                }
            }
        }
    }

    public static String join(List<String> lines, String eol, boolean trailingNewline) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(eol);
            sb.append(lines.get(i));
        }
        if (trailingNewline && !lines.isEmpty()) sb.append(eol);
        return sb.toString();
    }

    public static boolean allResolved(List<Region> regions) {
        for (Region r : regions) {
            if (!r.isResolved()) return false;
        }
        return true;
    }

    /**
     * Replaces every region by the accepted side(s), ours first, and keeps everything else (including manual edits
     * made outside the markers) untouched. Returns the lines of the resolved file.
     */
    public static List<String> resolve(List<String> lines, List<Region> regions) {
        List<Region> ordered = new ArrayList<>(regions);
        Collections.sort(ordered, (a, b) -> Integer.compare(a.startLine, b.startLine));
        List<String> out = new ArrayList<>();
        int cursor = 0;
        for (Region r : ordered) {
            out.addAll(lines.subList(cursor, r.startLine));
            if (r.acceptOurs) out.addAll(r.oursLines);
            if (r.acceptTheirs) out.addAll(r.theirsLines);
            cursor = r.endLine + 1;
        }
        out.addAll(lines.subList(cursor, lines.size()));
        return out;
    }
}
