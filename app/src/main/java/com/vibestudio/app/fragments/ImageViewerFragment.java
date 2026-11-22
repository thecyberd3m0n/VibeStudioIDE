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
    private static final String ARG_BASE64_DATA = "base64_data";
    private static final String ARG_IMAGE_TITLE = "image_title";

    private String mFilePath;
    private String mBase64Data;
    private String mImageTitle;
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

    public static ImageViewerFragment newInstanceWithBase64(String title, String base64Data) {
        ImageViewerFragment fragment = new ImageViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IMAGE_TITLE, title);
        args.putString(ARG_BASE64_DATA, base64Data);
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
            mBase64Data = getArguments().getString(ARG_BASE64_DATA);
            mImageTitle = getArguments().getString(ARG_IMAGE_TITLE);
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

        if (mFilePath != null || mBase64Data != null) {
            loadImage();
        } else {
            showError("No image path or data provided");
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFilePath != null || mBase64Data != null) {
            loadImage();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && (mFilePath != null || mBase64Data != null)) {
            loadImage();
        }
    }

    public void loadImage() {
        if (mFilePath != null) {
            File file = new File(mFilePath);
            if (!file.exists() || !file.isFile()) {
                showError("File not found");
                return;
            }

            if (mTvTitle != null) {
                mTvTitle.setText(file.getName());
            }

            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(mFilePath, options);

                int width = options.outWidth;
                int height = options.outHeight;
                String mimeType = options.outMimeType;

                if (mTvInfo != null) {
                    mTvInfo.setText(width + "x" + height + (mimeType != null ? " (" + mimeType.replace("image/", "") + ")" : ""));
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = calculateInSampleSize(options, 2048, 2048);

                Bitmap bitmap = BitmapFactory.decodeFile(mFilePath, options);
                if (bitmap != null && mIvPreview != null) {
                    mIvPreview.setImageBitmap(bitmap);
                    mIvPreview.setVisibility(View.VISIBLE);
                    if (mTvError != null) {
                        mTvError.setVisibility(View.GONE);
                    }
                } else {
                    showError("Failed to decode image");
                }
            } catch (Exception e) {
                showError("Error loading image: " + e.getMessage());
            }
        } else if (mBase64Data != null) {
            if (mTvTitle != null && mImageTitle != null) {
                mTvTitle.setText(mImageTitle);
            }
            try {
                byte[] bytes = android.util.Base64.decode(mBase64Data, android.util.Base64.DEFAULT);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

                int width = options.outWidth;
                int height = options.outHeight;
                String mimeType = options.outMimeType;

                if (mTvInfo != null) {
                    mTvInfo.setText(width + "x" + height + (mimeType != null ? " (" + mimeType.replace("image/", "") + ")" : ""));
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = calculateInSampleSize(options, 2048, 2048);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

                if (bitmap != null && mIvPreview != null) {
                    mIvPreview.setImageBitmap(bitmap);
                    mIvPreview.setVisibility(View.VISIBLE);
                    if (mTvError != null) {
                        mTvError.setVisibility(View.GONE);
                    }
                } else {
                    showError("Failed to decode base64 image");
                }
            } catch (Exception e) {
                showError("Error decoding base64 image: " + e.getMessage());
            }
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
