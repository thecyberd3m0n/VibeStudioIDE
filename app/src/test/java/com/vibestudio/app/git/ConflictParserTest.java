package com.vibestudio.app.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ConflictParserTest {

    private static final String SINGLE = "shared top\n<<<<<<< HEAD\nline from A\n=======\nline from B\n>>>>>>> conflict-b\nshared bottom\n";

    private static List<ConflictParser.Region> parse(String text) {
        return ConflictParser.parse(ConflictParser.splitLines(text));
    }

    @Test
    public void parsesSingleRegion() {
        List<ConflictParser.Region> regions = parse(SINGLE);
        assertEquals(1, regions.size());
        ConflictParser.Region r = regions.get(0);
        assertEquals(1, r.startLine);
        assertEquals(3, r.separatorLine);
        assertEquals(5, r.endLine);
        assertEquals(-1, r.baseLine);
        assertEquals("HEAD", r.oursLabel);
        assertEquals("conflict-b", r.theirsLabel);
        assertEquals(Arrays.asList("line from A"), r.oursLines);
        assertEquals(Arrays.asList("line from B"), r.theirsLines);
        assertEquals(3, r.oursEndExclusive());
    }

    @Test
    public void parsesDiff3Region() {
        List<ConflictParser.Region> regions = parse("<<<<<<< HEAD\na\n||||||| base\nb\n=======\nc\n>>>>>>> other\n");
        assertEquals(1, regions.size());
        ConflictParser.Region r = regions.get(0);
        assertEquals(2, r.baseLine);
        assertEquals(Arrays.asList("a"), r.oursLines);
        assertEquals(Arrays.asList("b"), r.baseLines);
        assertEquals(Arrays.asList("c"), r.theirsLines);
        assertEquals(2, r.oursEndExclusive());
    }

    @Test
    public void parsesSeveralRegionsAndEmptySides() {
        String text = "<<<<<<< HEAD\n=======\nadded\n>>>>>>> x\nmid\n<<<<<<< HEAD\nkept\n=======\n>>>>>>> x\n";
        List<ConflictParser.Region> regions = parse(text);
        assertEquals(2, regions.size());
        assertTrue(regions.get(0).oursLines.isEmpty());
        assertEquals(Arrays.asList("added"), regions.get(0).theirsLines);
        assertEquals(Arrays.asList("kept"), regions.get(1).oursLines);
        assertTrue(regions.get(1).theirsLines.isEmpty());
    }

    @Test
    public void ignoresMalformedAndLookalikeMarkers() {
        assertFalse(ConflictParser.hasConflictMarkers("<<<<<<< HEAD\nno separator or end\n"));
        assertFalse(ConflictParser.hasConflictMarkers("a\n=======\nb\n"));
        assertFalse(ConflictParser.hasConflictMarkers("<<<<<<<<< not a marker\n=======\n>>>>>>> x\n"));
        assertFalse(ConflictParser.hasConflictMarkers("Title\n=======\n"));
        assertTrue(ConflictParser.hasConflictMarkers(SINGLE));
    }

    @Test
    public void nestedStartMarkerDropsTheBrokenRegion() {
        String text = "<<<<<<< a\nx\n<<<<<<< b\ny\n=======\nz\n>>>>>>> b\n";
        List<ConflictParser.Region> regions = parse(text);
        assertEquals(1, regions.size());
        assertEquals(2, regions.get(0).startLine);
    }

    @Test
    public void resolveAppliesChosenSides() {
        List<String> lines = ConflictParser.splitLines(SINGLE);

        List<ConflictParser.Region> ours = ConflictParser.parse(lines);
        ours.get(0).acceptOurs = true;
        assertEquals(Arrays.asList("shared top", "line from A", "shared bottom"), ConflictParser.resolve(lines, ours));

        List<ConflictParser.Region> theirs = ConflictParser.parse(lines);
        theirs.get(0).acceptTheirs = true;
        assertEquals(Arrays.asList("shared top", "line from B", "shared bottom"), ConflictParser.resolve(lines, theirs));

        List<ConflictParser.Region> both = ConflictParser.parse(lines);
        both.get(0).acceptOurs = true;
        both.get(0).acceptTheirs = true;
        assertEquals(Arrays.asList("shared top", "line from A", "line from B", "shared bottom"),
                ConflictParser.resolve(lines, both));
    }

    @Test
    public void resolveKeepsManualEditsOutsideTheMarkers() {
        String edited = "EDITED top\n<<<<<<< HEAD\nline from A\n=======\nline from B\n>>>>>>> conflict-b\nshared bottom\nextra\n";
        List<String> lines = ConflictParser.splitLines(edited);
        List<ConflictParser.Region> regions = ConflictParser.parse(lines);
        regions.get(0).acceptTheirs = true;
        assertEquals(Arrays.asList("EDITED top", "line from B", "shared bottom", "extra"),
                ConflictParser.resolve(lines, regions));
    }

    @Test
    public void resolveWithManuallyEditedRegionBody() {
        String edited = "<<<<<<< HEAD\nline from A (edited)\n=======\nline from B\n>>>>>>> conflict-b\n";
        List<String> lines = ConflictParser.splitLines(edited);
        List<ConflictParser.Region> regions = ConflictParser.parse(lines);
        regions.get(0).acceptOurs = true;
        assertEquals(Arrays.asList("line from A (edited)"), ConflictParser.resolve(lines, regions));
    }

    @Test
    public void removingMarkersByHandLeavesNothingToResolve() {
        List<ConflictParser.Region> regions = parse("shared top\nmanual merge\nshared bottom\n");
        assertTrue(regions.isEmpty());
        assertTrue(ConflictParser.allResolved(regions));
    }

    @Test
    public void allResolvedNeedsEveryRegion() {
        List<ConflictParser.Region> regions = parse("<<<<<<< a\n1\n=======\n2\n>>>>>>> b\n<<<<<<< a\n3\n=======\n4\n>>>>>>> b\n");
        assertFalse(ConflictParser.allResolved(regions));
        regions.get(0).acceptOurs = true;
        assertFalse(ConflictParser.allResolved(regions));
        regions.get(1).acceptTheirs = true;
        assertTrue(ConflictParser.allResolved(regions));
    }

    @Test
    public void choicesSurviveEditsThatKeepTheRegionCount() {
        List<ConflictParser.Region> before = parse(SINGLE);
        before.get(0).acceptTheirs = true;
        List<ConflictParser.Region> after = parse("one more line\n" + SINGLE);
        ConflictParser.carryOverChoices(before, after);
        assertTrue(after.get(0).acceptTheirs);
        assertFalse(after.get(0).acceptOurs);
    }

    @Test
    public void choicesAreMatchedByContentWhenRegionCountChanges() {
        String two = "<<<<<<< a\n1\n=======\n2\n>>>>>>> b\n<<<<<<< a\n3\n=======\n4\n>>>>>>> b\n";
        List<ConflictParser.Region> before = parse(two);
        before.get(1).acceptOurs = true;

        List<ConflictParser.Region> after = parse("<<<<<<< a\n3\n=======\n4\n>>>>>>> b\n");
        ConflictParser.carryOverChoices(before, after);
        assertTrue(after.get(0).acceptOurs);
    }

    @Test
    public void splitAndJoinRoundTrip() {
        assertEquals(Arrays.asList("a", "b"), ConflictParser.splitLines("a\r\nb\r\n"));
        assertEquals("a\r\nb\r\n", ConflictParser.join(Arrays.asList("a", "b"), "\r\n", true));
        assertEquals("a\nb", ConflictParser.join(Arrays.asList("a", "b"), "\n", false));
        assertEquals("", ConflictParser.join(Arrays.asList(), "\n", true));
        assertTrue(ConflictParser.splitLines("").isEmpty());
    }
}
