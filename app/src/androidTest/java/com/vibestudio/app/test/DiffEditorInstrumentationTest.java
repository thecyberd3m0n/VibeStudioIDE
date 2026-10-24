package com.vibestudio.app.test;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.fragments.EditorFragment;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DiffEditorInstrumentationTest {

    @Test
    public void testE2EDiffViewAndSingleHunkRollback() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        // Skip if environment not initialized
        Assume.assumeTrue("Skipping test because onboarding environment is not done", dbHelper.isEnvInitialized());

        // 1. Create temporary directory representing a git repo state
        File repoDir = new File(context.getFilesDir(), "test_diff_repo_" + System.currentTimeMillis());
        if (!repoDir.exists()) {
            repoDir.mkdirs();
        }

        File file1 = new File(repoDir, "base_file.txt");
        File file2 = new File(repoDir, "worktree_file.txt");

        // Base content and modified content (single hunk difference)
        try (BufferedWriter w1 = new BufferedWriter(new FileWriter(file1));
             BufferedWriter w2 = new BufferedWriter(new FileWriter(file2))) {
            w1.write("line 1\nline 2 original\nline 3\n");
            w2.write("line 1\nline 2 modified\nline 3\n");
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                Assert.assertNotNull("MainActivity should launch", activity);

                // Open Diff Editor tab using newDiffInstance
                EditorFragment diffFragment = EditorFragment.newDiffInstance(file1.getAbsolutePath(), file2.getAbsolutePath());
                TabManager.getInstance().openTab(TabType.EDITOR, "Diff: worktree_file.txt", "📝", diffFragment);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                TabItem activeTab = TabManager.getInstance().getActiveTab();
                Assert.assertNotNull("Active tab should exist for Diff Editor", activeTab);
                Assert.assertTrue("Fragment should be EditorFragment", activeTab.getFragment() instanceof EditorFragment);

                EditorFragment fragment = (EditorFragment) activeTab.getFragment();

                // 2. Verify overlay container & overlays
                FrameLayout container = fragment.getOverlayContainer();
                Assert.assertNotNull("Overlay container should exist", container);

                // Trigger layout/overlay calculation
                fragment.updateHunkOverlayPositions();

                int childCount = container.getChildCount();
                Assert.assertTrue("Overlay container should have rendered child overlays", childCount > 0);

                ImageButton revertBtn = null;
                int bgOverlayCount = 0;

                for (int i = 0; i < childCount; i++) {
                    View child = container.getChildAt(i);
                    if (child instanceof ImageButton) {
                        revertBtn = (ImageButton) child;
                    } else if (child != null) {
                        bgOverlayCount++;
                    }
                }

                Assert.assertTrue("Background color overlay views should be rendered", bgOverlayCount > 0);
                Assert.assertNotNull("Single hunk right-side overlay undo button should exist", revertBtn);
                Assert.assertEquals("Right-side overlay undo button should be visible", View.VISIBLE, revertBtn.getVisibility());

                // Verify line text does not have green '+ text' prefix
                List<EditorFragment.DiffLine> currentLines = fragment.getCurrentDiffLines();
                for (EditorFragment.DiffLine line : currentLines) {
                    Assert.assertFalse("Diff line text should not start with '+' symbol", line.text.startsWith("+"));
                }

                // 3. Rollback the change via single hunk overlay button (NOT undo all button)
                revertBtn.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // 4. Expect that worktree_file.txt content was rolled back to base_file.txt
            scenario.onActivity(activity -> {
                try {
                    List<String> lines2 = Files.readAllLines(file2.toPath(), StandardCharsets.UTF_8);
                    List<String> lines1 = Files.readAllLines(file1.toPath(), StandardCharsets.UTF_8);
                    Assert.assertEquals("Worktree file content should be rolled back to base content after hunk revert", lines1, lines2);
                } catch (IOException e) {
                    Assert.fail("Failed to read file after rollback: " + e.getMessage());
                }
            });
        } finally {
            // Clean up temporary repo directory
            if (file1.exists()) file1.delete();
            if (file2.exists()) file2.delete();
            if (repoDir.exists()) repoDir.delete();
        }
    }
}
