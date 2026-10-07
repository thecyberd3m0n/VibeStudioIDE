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

import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.mcp.GeminiValidator;
import com.vibestudio.app.service.LogViewerService;

import java.util.List;

public class ModelsFragment extends Fragment {

    private static final String TAG = "ModelsFragment";
    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private DatabaseHelper mDbHelper;
    private LinearLayout mCardsContainer;

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
        mCardsContainer = new LinearLayout(context);
        mCardsContainer.setOrientation(LinearLayout.VERTICAL);
        mCardsContainer.setPadding(16, 16, 16, 16);

        scrollView.addView(mCardsContainer);

        renderProviderCards(context);

        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            renderProviderCards(getContext());
        }
    }

    private void renderProviderCards(final Context context) {
        if (mCardsContainer == null) return;
        mCardsContainer.removeAllViews();

        AiRouter router = AiRouter.getInstance();
        router.loadActiveState(context);

        List<AiProvider> providers = router.getProviders();
        String activeProviderName = router.getActiveProviderName();

        for (final AiProvider provider : providers) {
            final String pName = provider.getName();
            final String apiKey = mDbHelper != null ? mDbHelper.getApiKey(pName) : null;
            final boolean hasApiKey = !TextUtils.isEmpty(apiKey);
            final boolean isProviderActive = pName.equalsIgnoreCase(activeProviderName);

            // Collapsible Provider Card Container
            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.parseColor("#1E1E1E"));
            card.setPadding(24, 24, 24, 24);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 24);
            card.setLayoutParams(cardParams);

            // Provider Header Row (Title + Status Indicator + Expand/ApiKey button)
            LinearLayout headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            TextView providerTitle = new TextView(context);
            providerTitle.setText(pName);
            providerTitle.setTextColor(Color.parseColor("#BB86FC"));
            providerTitle.setTextSize(18);
            providerTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
            providerTitle.setLayoutParams(titleParams);

            TextView apiKeyStatus = new TextView(context);
            if (hasApiKey) {
                apiKeyStatus.setText("● API Key Configured");
                apiKeyStatus.setTextColor(Color.parseColor("#03DAC6"));
            } else {
                apiKeyStatus.setText("● Missing API Key (Non-working)");
                apiKeyStatus.setTextColor(Color.parseColor("#CF6679"));
            }
            apiKeyStatus.setTextSize(12);
            apiKeyStatus.setPadding(12, 0, 12, 0);

            Button btnKey = new Button(context);
            btnKey.setText(hasApiKey ? "Edit Key" : "Set Key");
            btnKey.setTextSize(11);
            btnKey.setBackgroundColor(Color.parseColor("#2D2D2D"));
            btnKey.setTextColor(Color.parseColor("#FFFFFF"));
            btnKey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showApiKeyDialog(context, pName);
                }
            });

            headerRow.addView(providerTitle);
            headerRow.addView(apiKeyStatus);
            headerRow.addView(btnKey);

            card.addView(headerRow);

            // Models Collapsible Section
            final LinearLayout modelsContainer = new LinearLayout(context);
            modelsContainer.setOrientation(LinearLayout.VERTICAL);
            modelsContainer.setPadding(16, 16, 16, 8);

            List<String> availableModels = provider.getAvailableModels();
            String currentActiveModel = router.getActiveModelName(pName);

            RadioGroup modelRadioGroup = new RadioGroup(context);
            modelRadioGroup.setOrientation(RadioGroup.VERTICAL);

            for (final String modelName : availableModels) {
                RadioButton rb = new RadioButton(context);
                boolean isSelectedModel = isProviderActive && modelName.equalsIgnoreCase(currentActiveModel);

                if (hasApiKey) {
                    rb.setText(modelName + (isSelectedModel ? " (Active)" : ""));
                    rb.setTextColor(Color.parseColor(isSelectedModel ? "#03DAC6" : "#E0E0E0"));
                    rb.setEnabled(true);
                } else {
                    rb.setText(modelName + " (Disabled - API Key required)");
                    rb.setTextColor(Color.parseColor("#CF6679"));
                    rb.setEnabled(false);
                }

                rb.setChecked(isSelectedModel);

                rb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (hasApiKey) {
                            AiRouter.getInstance().saveActiveSelection(context, pName, modelName);
                            Toast.makeText(context, "Active model set to: " + pName + " - " + modelName, Toast.LENGTH_SHORT).show();
                            renderProviderCards(context);
                        }
                    }
                });

                modelRadioGroup.addView(rb);
            }

            modelsContainer.addView(modelRadioGroup);
            card.addView(modelsContainer);

            mCardsContainer.addView(card);
        }
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
                        renderProviderCards(context);
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
