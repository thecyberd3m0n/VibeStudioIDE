package com.vibestudio.app.fragments;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.fragment.app.FragmentActivity;
import org.robolectric.Robolectric;
import org.mockito.Mockito;
import io.github.rosemoe.sora.widget.CodeEditor;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml")
public class EditorFragmentDiffTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private EditorFragment editorFragment;

    @Before
    public void setUp() {
        editorFragment = EditorFragment.newInstance();
    }

    @Test
    public void testComputeDiffNoChanges() {
        List<String> lines1 = Arrays.asList("line1", "line2", "line3");
        List<String> lines2 = Arrays.asList("line1", "line2", "line3");

        List<EditorFragment.DiffLine> diff = editorFragment.computeDiff(lines1, lines2);

        Assert.assertEquals(3, diff.size());
        for (EditorFragment.DiffLine dl : diff) {
            Assert.assertEquals(EditorFragment.DiffLine.Type.UNCHANGED, dl.type);
        }
    }

    @Test
    public void testComputeDiffEmptyFiles() {
        List<String> lines1 = Arrays.asList();
        List<String> lines2 = Arrays.asList();

        List<EditorFragment.DiffLine> diff = editorFragment.computeDiff(lines1, lines2);

        Assert.assertEquals(0, diff.size());
    }

    @Test
    public void testComputeDiffAdditionsAndDeletions() {
        List<String> lines1 = Arrays.asList("line1", "oldLine", "line3");
        List<String> lines2 = Arrays.asList("line1", "newLine", "line3");

        List<EditorFragment.DiffLine> diff = editorFragment.computeDiff(lines1, lines2);

        int addedCount = 0;
        int deletedCount = 0;
        int unchangedCount = 0;

        for (EditorFragment.DiffLine dl : diff) {
            if (dl.type == EditorFragment.DiffLine.Type.ADDED) addedCount++;
            else if (dl.type == EditorFragment.DiffLine.Type.DELETED) deletedCount++;
            else if (dl.type == EditorFragment.DiffLine.Type.UNCHANGED) unchangedCount++;
        }

        Assert.assertEquals(2, unchangedCount);
        Assert.assertEquals(1, addedCount);
        Assert.assertEquals(1, deletedCount);
    }

    @Test
    public void testExtractDiffHunksNoChanges() {
        List<EditorFragment.DiffLine> diff = Arrays.asList(
                new EditorFragment.DiffLine(EditorFragment.DiffLine.Type.HEADER, "--- f1"),
                new EditorFragment.DiffLine(EditorFragment.DiffLine.Type.HEADER, "+++ f2"),
                new EditorFragment.DiffLine(EditorFragment.DiffLine.Type.UNCHANGED, "  line1"),
                new EditorFragment.DiffLine(EditorFragment.DiffLine.Type.UNCHANGED, "  line2")
        );

        editorFragment.extractDiffHunks(diff);

        Assert.assertTrue(editorFragment.getDiffHunks().isEmpty());
    }

    @Test
    public void testReadLinesFromFileHandlesWindowsLineEndings() throws IOException {
        File f = temporaryFolder.newFile("crlf.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write("line1\r\nline2\r\n");
        }

        List<String> lines = editorFragment.readLinesFromFile(f.getAbsolutePath());

        Assert.assertEquals(2, lines.size());
        Assert.assertEquals("line1", lines.get(0));
        Assert.assertEquals("line2", lines.get(1));
    }

    @Test
    public void testCheckTrailingNewline() throws IOException {
        File fWithNewline = temporaryFolder.newFile("with_nl.txt");
        try (FileOutputStream fos = new FileOutputStream(fWithNewline)) {
            fos.write("hello\n".getBytes(StandardCharsets.UTF_8));
        }

        File fNoNewline = temporaryFolder.newFile("no_nl.txt");
        try (FileOutputStream fos = new FileOutputStream(fNoNewline)) {
            fos.write("hello".getBytes(StandardCharsets.UTF_8));
        }

        Assert.assertTrue(EditorFragment.checkTrailingNewline(fWithNewline));
        Assert.assertFalse(EditorFragment.checkTrailingNewline(fNoNewline));
    }

    @Test
    public void testSaveFileContentPreservesTrailingNewline() throws IOException {
        File f = temporaryFolder.newFile("test_preserve.txt");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write("line1\nline2\n".getBytes(StandardCharsets.UTF_8));
        }

        editorFragment.loadFileContent(f.getAbsolutePath());
        editorFragment.saveFileContent(f.getAbsolutePath(), "line1\nline2\nline3");

        Assert.assertTrue(EditorFragment.checkTrailingNewline(f));
        List<String> lines = editorFragment.readLinesFromFile(f.getAbsolutePath());
        Assert.assertEquals(3, lines.size());
        Assert.assertEquals("line3", lines.get(2));
    }

    @Test
    public void testUpdateHunkOverlayPositionsSingleLineChange() throws IOException {
        File file1 = temporaryFolder.newFile("f1.txt");
        File file2 = temporaryFolder.newFile("f2.txt");

        try (BufferedWriter w1 = new BufferedWriter(new FileWriter(file1));
             BufferedWriter w2 = new BufferedWriter(new FileWriter(file2))) {
            w1.write("line 1\nline 2\nline 3\n");
            w2.write("line 1\nline 2 changed\nline 3\n");
        }

        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();

        EditorFragment diffFragment = new EditorFragment() {
            @Override
            public Context getContext() {
                return activity;
            }
        };

        List<String> lines1 = diffFragment.readLinesFromFile(file1.getAbsolutePath());
        List<String> lines2 = diffFragment.readLinesFromFile(file2.getAbsolutePath());

        List<EditorFragment.DiffLine> diff = diffFragment.computeDiff(lines1, lines2);
        diffFragment.extractDiffHunks(diff);

        Assert.assertEquals("Should have 1 diff hunk for single line change", 1, diffFragment.getDiffHunks().size());

        FrameLayout container = new FrameLayout(activity);
        CodeEditor mockEditor = Mockito.mock(CodeEditor.class);
        Mockito.when(mockEditor.getRowHeight()).thenReturn(40);
        Mockito.when(mockEditor.getOffsetY()).thenReturn(0);
        Mockito.when(mockEditor.getPaddingTop()).thenReturn(0);
        Mockito.when(mockEditor.getHeight()).thenReturn(800);
        Mockito.when(mockEditor.getRowTop(Mockito.anyInt())).thenAnswer(i -> (Integer) i.getArgument(0) * 40);

        diffFragment.setTestViews(container, mockEditor);
        diffFragment.setTestDiffLinesAndHunks(diff);

        // Directly invoke overlay calculation
        diffFragment.updateHunkOverlayPositions();

        Assert.assertNotNull("Overlay container should not be null", diffFragment.getOverlayContainer());

        int childCount = container.getChildCount();
        Assert.assertTrue("Overlay container should have views rendered", childCount > 0);

        boolean foundRevertButton = false;
        int bgViewCount = 0;
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            if (child instanceof ImageButton) {
                foundRevertButton = true;
                Assert.assertEquals("Revert button should be VISIBLE", View.VISIBLE, child.getVisibility());
            } else if (child != null) {
                bgViewCount++;
            }
        }

        Assert.assertTrue("Line background overlays should be added", bgViewCount > 0);
        Assert.assertTrue("Revert button on overlay should be visible even if single line was changed", foundRevertButton);
    }
}
