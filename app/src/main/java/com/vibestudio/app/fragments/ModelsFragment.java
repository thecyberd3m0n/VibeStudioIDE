package com.vibestudio.app.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.mcp.GeminiValidator;
import com.vibestudio.app.service.LogViewerService;

public class ModelsFragment extends Fragment {

    private static final String TAG = "ModelsFragment";
    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private DatabaseHelper mDbHelper;
    private TextView mStatusTextView;
    private RadioGroup mProviderRadioGroup;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() != null) {
            mDbHelper = new DatabaseHelper(getActivity().getApplicationContext());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        if (context == null) return null;

        ScrollView scrollView = new ScrollView(context);
        final LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        AiRouter.getInstance().loadActiveProvider(context);
        String activeProvider = AiRouter.getInstance().getActiveProviderName();

        TextView selectorTitle = new TextView(context);
        selectorTitle.setText("Active AI Provider");
        selectorTitle.setTextColor(Color.parseColor("#FFFFFF"));
        selectorTitle.setTextSize(16);
        selectorTitle.setTypeface(null, Typeface.BOLD);
        selectorTitle.setPadding(16, 16, 16, 8);
        mainContainer.addView(selectorTitle);

        mProviderRadioGroup = new RadioGroup(context);
        mProviderRadioGroup.setOrientation(RadioGroup.VERTICAL);
        mProviderRadioGroup.setPadding(16, 0, 16, 16);

        final RadioButton geminiRadio = new RadioButton(context);
        geminiRadio.setText("Google Gemini");
        geminiRadio.setTextColor(Color.parseColor("#BB86FC"));
        geminiRadio.setId(View.generateViewId());
        if ("Gemini".equalsIgnoreCase(activeProvider)) {
            geminiRadio.setChecked(true);
        }

        mProviderRadioGroup.addView(geminiRadio);
        mainContainer.addView(mProviderRadioGroup);

        mProviderRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == geminiRadio.getId()) {
                    AiRouter.getInstance().saveActiveProvider(context, "Gemini");
                    updateStatusView("Gemini");
                    Toast.makeText(context, "Active provider set to Gemini", Toast.LENGTH_SHORT).show();
                }
            }
        });

        final String provider = "Gemini";

        LinearLayout card = createCard(context);

        TextView name = new TextView(context);
        name.setText("Google Gemini 1.5 Pro");
        name.setTextColor(Color.parseColor("#BB86FC"));
        name.setTextSize(18);
        name.setTypeface(null, Typeface.BOLD);

        TextView desc = new TextView(context);
        desc.setText("Google • High performance multimodal reasoning and long-context AI");
        desc.setTextColor(Color.parseColor("#B0B0B0"));
        desc.setTextSize(14);
        desc.setPadding(0, 8, 0, 8);

        mStatusTextView = new TextView(context);
        mStatusTextView.setTextSize(12);

        card.addView(name);
        card.addView(desc);
        card.addView(mStatusTextView);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showApiKeyDialog(context, provider);
            }
        });

        mainContainer.addView(card);
        scrollView.addView(mainContainer);

        updateStatusView(provider);

        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            AiRouter.getInstance().loadActiveProvider(getContext());
        }
        updateStatusView("Gemini");
    }

    private void updateStatusView(String provider) {
        if (mStatusTextView == null) return;

        String savedKey = mDbHelper != null ? mDbHelper.getApiKey(provider) : null;
        if (!TextUtils.isEmpty(savedKey)) {
            mStatusTextView.setText("Status: Configured (API Key set) • Click to edit");
            mStatusTextView.setTextColor(Color.parseColor("#03DAC6"));
            LogViewerService.getInstance().d(TAG, provider + " API Key loaded from SQLite db");
        } else {
            mStatusTextView.setText("Status: Not configured (Click to set Gemini API Key)");
            mStatusTextView.setTextColor(Color.parseColor("#FFB74D"));
            LogViewerService.getInstance().d(TAG, provider + " API Key is not set in SQLite db");
        }
    }

    private LinearLayout createCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1E1E"));
        card.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(16, 16, 16, 0);
        card.setLayoutParams(params);
        return card;
    }

    private void showApiKeyDialog(final Context context, final String provider) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        final EditText input = new EditText(context);
        input.setHint("Enter " + provider + " API Key");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        String currentKey = mDbHelper != null ? mDbHelper.getApiKey(provider) : "";
        if (!TextUtils.isEmpty(currentKey)) {
            input.setText(currentKey);
        }

        final TextView errorTextView = new TextView(context);
        errorTextView.setTextColor(Color.parseColor("#CF6679"));
        errorTextView.setTextSize(13);
        errorTextView.setPadding(0, 12, 0, 0);
        errorTextView.setVisibility(View.GONE);

        final ProgressBar progressBar = new ProgressBar(context);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        progressParams.topMargin = 16;
        progressBar.setLayoutParams(progressParams);

        layout.addView(input);
        layout.addView(progressBar);
        layout.addView(errorTextView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Configure " + provider + " API Key");
        builder.setView(layout);

        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builder.create();
        dialog.show();

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String key = input.getText().toString().trim();
                if (TextUtils.isEmpty(key)) {
                    errorTextView.setText("API Key cannot be empty");
                    errorTextView.setVisibility(View.VISIBLE);
                    return;
                }

                // Show loading indicator & disable inputs
                progressBar.setVisibility(View.VISIBLE);
                errorTextView.setVisibility(View.GONE);
                input.setEnabled(false);
                positiveButton.setEnabled(false);

                LogViewerService.getInstance().i(TAG, "Starting validation for " + provider + " API Key...");

                GeminiValidator.validateKey(key, new GeminiValidator.ValidationCallback() {
                    @Override
                    public void onSuccess() {
                        if (mDbHelper != null) {
                            mDbHelper.saveApiKey(provider, key);
                            LogViewerService.getInstance().i(TAG, provider + " API Key validated and saved to SQLite DB.");
                        }
                        updateStatusView(provider);
                        Toast.makeText(context, provider + " API Key validated & saved!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progressBar.setVisibility(View.GONE);
                        input.setEnabled(true);
                        positiveButton.setEnabled(true);
                        errorTextView.setText(errorMessage);
                        errorTextView.setVisibility(View.VISIBLE);
                        LogViewerService.getInstance().w(TAG, provider + " API Key validation error: " + errorMessage);
                    }
                });
            }
        });
    }
}
