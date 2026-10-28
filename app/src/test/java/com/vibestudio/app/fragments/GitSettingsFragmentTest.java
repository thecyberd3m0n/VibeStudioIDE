package com.vibestudio.app.fragments;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.R;
import com.vibestudio.app.test.ShadowDatabaseHelper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml", shadows = {ShadowDatabaseHelper.class})
public class GitSettingsFragmentTest {

    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        ShadowDatabaseHelper.reset();
        mActivity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    }

    @Test
    public void testSettingsFragmentSwitchesToGitTab() {
        SettingsFragment settingsFragment = new SettingsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, settingsFragment)
                .commitNow();

        View root = settingsFragment.getView();
        Assert.assertNotNull("SettingsFragment view should not be null", root);

        TextView btnGit = root.findViewById(R.id.btn_settings_git);
        Assert.assertNotNull("Git tab button in settings header should exist", btnGit);
        Assert.assertEquals("🌿 Git", btnGit.getText().toString());

        btnGit.performClick();
        ShadowLooper.idleMainLooper();
        settingsFragment.getChildFragmentManager().executePendingTransactions();

        View gitRoot = settingsFragment.getChildFragmentManager().findFragmentById(R.id.settings_content_frame).getView();
        Assert.assertNotNull("GitSettingsFragment view should be loaded in settings frame", gitRoot);

        EditText editName = gitRoot.findViewById(R.id.edit_git_user_name);
        EditText editEmail = gitRoot.findViewById(R.id.edit_git_user_email);
        Button btnSave = gitRoot.findViewById(R.id.btn_save_git_identity);

        Assert.assertNotNull("Git user name edit text should exist", editName);
        Assert.assertNotNull("Git user email edit text should exist", editEmail);
        Assert.assertNotNull("Save Git identity button should exist", btnSave);
    }

    @Test
    public void testGitSettingsFragmentControlsAndCopy() {
        GitSettingsFragment fragment = new GitSettingsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View root = fragment.getView();
        Assert.assertNotNull("GitSettingsFragment view should not be null", root);

        EditText txtSshKey = root.findViewById(R.id.txt_ssh_public_key);
        Button btnCopySsh = root.findViewById(R.id.btn_copy_ssh_key);
        Button btnGenerateSsh = root.findViewById(R.id.btn_generate_ssh_key);

        Assert.assertNotNull("SSH Key edit text should exist", txtSshKey);
        Assert.assertNotNull("Copy SSH Key button should exist", btnCopySsh);
        Assert.assertNotNull("Generate SSH Key button should exist", btnGenerateSsh);

        btnCopySsh.performClick();
    }
}
