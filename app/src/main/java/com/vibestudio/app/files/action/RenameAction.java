package com.vibestudio.app.files.action;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import android.widget.Toast;

import com.vibestudio.app.files.model.FileEntry;

import java.io.File;

public class RenameAction implements FileAction {

    @Override
    public String getTitle() {
        return "Rename";
    }

    @Override
    public boolean isAvailableFor(FileEntry entry) {
        return entry != null && !entry.isUpDirectory();
    }

    @Override
    public void execute(Context context, FileEntry entry, FileActionListener listener) {
        Context themedContext = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Rename " + (entry.isDirectory() ? "Folder" : "File"));

        final EditText input = new EditText(themedContext);
        input.setText(entry.getName());
        input.setTextColor(0xFFFFFFFF);
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(entry.getName())) {
                File parentDir = entry.getFile().getParentFile();
                File target = new File(parentDir, newName);
                if (entry.getFile().renameTo(target)) {
                    Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onReloadRequested();
                } else {
                    Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
