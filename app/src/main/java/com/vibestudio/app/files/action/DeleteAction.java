package com.vibestudio.app.files.action;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

import com.vibestudio.app.files.model.FileEntry;

import java.io.File;

public class DeleteAction implements FileAction {

    @Override
    public String getTitle() {
        return "Delete";
    }

    @Override
    public boolean isAvailableFor(FileEntry entry) {
        return entry != null && !entry.isUpDirectory();
    }

    @Override
    public void execute(Context context, FileEntry entry, FileActionListener listener) {
        Context themedContext = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        new AlertDialog.Builder(themedContext)
                .setTitle("Delete")
                .setMessage("Are you sure you want to delete '" + entry.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (deleteRecursive(entry.getFile())) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onReloadRequested();
                    } else {
                        Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }
}
