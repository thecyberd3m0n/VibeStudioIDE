package com.vibestudio.app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;
import com.vibestudio.app.git.GitManager;

public class GitSettingsFragment extends Fragment {

    private EditText mEditGitUserName;
    private EditText mEditGitUserEmail;
    private Button mBtnSaveGitIdentity;

    private EditText mTxtSshPublicKey;
    private Button mBtnCopySshKey;
    private Button mBtnGenerateSshKey;

    private TextView mTvStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_git_settings, container, false);

        mEditGitUserName = view.findViewById(R.id.edit_git_user_name);
        mEditGitUserEmail = view.findViewById(R.id.edit_git_user_email);
        mBtnSaveGitIdentity = view.findViewById(R.id.btn_save_git_identity);

        mTxtSshPublicKey = view.findViewById(R.id.txt_ssh_public_key);
        mBtnCopySshKey = view.findViewById(R.id.btn_copy_ssh_key);
        mBtnGenerateSshKey = view.findViewById(R.id.btn_generate_ssh_key);

        mTvStatus = view.findViewById(R.id.tv_git_settings_status);

        setupListeners();
        loadGitSettings();

        return view;
    }

    private void loadGitSettings() {
        Context context = getContext();
        if (context == null) return;

        String name = GitManager.getInstance().getGitUserName(context);
        String email = GitManager.getInstance().getGitUserEmail(context);

        if (mEditGitUserName != null) mEditGitUserName.setText(name);
        if (mEditGitUserEmail != null) mEditGitUserEmail.setText(email);

        updateSshKeyUi();
    }

    private void updateSshKeyUi() {
        Context context = getContext();
        if (context == null) return;

        String pubKey = GitManager.getInstance().getSshPublicKey(context);
        if (mTxtSshPublicKey != null) {
            if (!TextUtils.isEmpty(pubKey)) {
                mTxtSshPublicKey.setText(pubKey);
            } else {
                mTxtSshPublicKey.setText("not configured");
            }
        }
    }

    private void setupListeners() {
        if (mBtnSaveGitIdentity != null) {
            mBtnSaveGitIdentity.setOnClickListener(v -> saveGitIdentity());
        }

        if (mBtnCopySshKey != null) {
            mBtnCopySshKey.setOnClickListener(v -> copySshPublicKey());
        }

        if (mBtnGenerateSshKey != null) {
            mBtnGenerateSshKey.setOnClickListener(v -> generateSshKey());
        }
    }

    private void saveGitIdentity() {
        Context context = getContext();
        if (context == null) return;

        String name = mEditGitUserName != null ? mEditGitUserName.getText().toString().trim() : "";
        String email = mEditGitUserEmail != null ? mEditGitUserEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
            Toast.makeText(context, "Please enter both Git user name and email", Toast.LENGTH_SHORT).show();
            return;
        }

        showStatus("Saving Git credentials...");
        GitManager.getInstance().setGitCredentials(context, name, email, null, (success, output) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("Git credentials saved successfully!");
                        Toast.makeText(context, "Git identity updated", Toast.LENGTH_SHORT).show();
                    } else {
                        showStatus("Failed to save Git credentials.");
                        Toast.makeText(context, "Error saving Git identity", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void copySshPublicKey() {
        Context context = getContext();
        if (context == null) return;

        String pubKey = GitManager.getInstance().getSshPublicKey(context);
        if (!TextUtils.isEmpty(pubKey)) {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("SSH Public Key", pubKey);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "SSH public key copied to clipboard", Toast.LENGTH_SHORT).show();
                    showStatus("SSH public key copied!");
                }
            } catch (Exception e) {
                Toast.makeText(context, "Failed to copy SSH key", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "No SSH key configured to copy", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateSshKey() {
        Context context = getContext();
        if (context == null) return;

        showStatus("Generating new SSH ED25519 key pair...");
        GitManager.getInstance().generateSshKey(context, (success, output) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    updateSshKeyUi();
                    if (success || GitManager.getInstance().isSshKeyConfigured(context)) {
                        showStatus("SSH key pair generated successfully!");
                        Toast.makeText(context, "SSH key generated", Toast.LENGTH_SHORT).show();
                    } else {
                        showStatus("SSH key generation completed.");
                        Toast.makeText(context, "SSH key generated", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showStatus(String message) {
        if (mTvStatus != null) {
            mTvStatus.setText(message);
            mTvStatus.setVisibility(View.VISIBLE);
        }
    }
}
