package com.vibestudio.app.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.R;

import java.io.File;

public class ImageViewerFragment extends Fragment {

    private static final String ARG_FILE_PATH = "file_path";

    private String mFilePath;
    private ImageView mIvPreview;
    private TextView mTvTitle;
    private TextView mTvInfo;
    private TextView mTvError;

    public static ImageViewerFragment newInstance(String filePath) {
        ImageViewerFragment fragment = new ImageViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        fragment.setArguments(args);
        return fragment;
    }

    public String getFilePath() {
        return mFilePath;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mFilePath = getArguments().getString(ARG_FILE_PATH);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_image_viewer, container, false);

        mIvPreview = view.findViewById(R.id.iv_image_preview);
        mTvTitle = view.findViewById(R.id.tv_image_title);
        mTvInfo = view.findViewById(R.id.tv_image_info);
        mTvError = view.findViewById(R.id.tv_image_error);

        if (mFilePath != null) {
            loadImage();
        } else {
            showError("No image path provided");
        }

        return view;
    }

    private void loadImage() {
        File file = new File(mFilePath);
        if (!file.exists() || !file.isFile()) {
            showError("File not found");
            return;
        }

        mTvTitle.setText(file.getName());

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mFilePath, options);

            int width = options.outWidth;
            int height = options.outHeight;
            String mimeType = options.outMimeType;

            mTvInfo.setText(width + "x" + height + (mimeType != null ? " (" + mimeType.replace("image/", "") + ")" : ""));

            options.inJustDecodeBounds = false;
            options.inSampleSize = calculateInSampleSize(options, 2048, 2048);

            Bitmap bitmap = BitmapFactory.decodeFile(mFilePath, options);
            if (bitmap != null) {
                mIvPreview.setImageBitmap(bitmap);
                mIvPreview.setVisibility(View.VISIBLE);
                mTvError.setVisibility(View.GONE);
            } else {
                showError("Failed to decode image");
            }
        } catch (Exception e) {
            showError("Error loading image: " + e.getMessage());
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void showError(String msg) {
        if (mIvPreview != null) mIvPreview.setVisibility(View.GONE);
        if (mTvError != null) {
            mTvError.setText(msg);
            mTvError.setVisibility(View.VISIBLE);
        }
    }
}
