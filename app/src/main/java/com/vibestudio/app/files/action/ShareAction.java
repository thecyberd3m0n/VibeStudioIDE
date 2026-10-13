package com.vibestudio.app.files.action;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vibestudio.app.files.model.FileEntry;

import java.io.File;

public class ShareAction implements FileAction {

    private static final String TAG = "ShareAction";

    @Override
    public String getTitle() {
        return "Share";
    }

    @Override
    public boolean isAvailableFor(FileEntry entry) {
        return entry != null && !entry.isDirectory() && !entry.isUpDirectory();
    }

    @Override
    public void execute(Context context, FileEntry entry, FileActionListener listener) {
        if (entry == null || entry.getFile() == null) return;
        File file = entry.getFile();

        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File canonicalFile = file.getCanonicalFile();
            Log.d(TAG, "Sharing file raw path: " + file.getAbsolutePath() + ", canonical path: " + canonicalFile.getAbsolutePath());

            Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    canonicalFile
            );

            String mimeType = getMimeType(canonicalFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "Share file via");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Failed to share file", e);
            Toast.makeText(context, "Failed to share file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getMimeType(File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (extension != null && !extension.isEmpty()) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) {
                return mime;
            }
        }
        return "*/*";
    }
}
