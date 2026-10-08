package com.vibestudio.app.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.service.LogViewerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelsFragment extends Fragment {

    private static final String TAG = "ModelsFragment";
    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private DatabaseHelper mDbHelper;
    private LinearLayout mCardsContainer;

    // Track collapse state per provider
    private final Map<String, Boolean> mExpandedState = new HashMap<>();

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

            if (!mExpandedState.containsKey(pName)) {
                mExpandedState.put(pName, isProviderActive || hasApiKey);
            }
            final boolean isExpanded = Boolean.TRUE.equals(mExpandedState.get(pName));

            // Main Card Container with Rounded Background
            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 20, 20, 20);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.parseColor("#1E1E2E"));
            cardBg.setCornerRadius(16f);
            cardBg.setStroke(2, Color.parseColor(isProviderActive ? "#03DAC6" : "#2E2E3E"));
            card.setBackground(cardBg);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 24);
            card.setLayoutParams(cardParams);

            // Header Row (Expander, Title, Status Indicator, Key Icon Button)
            LinearLayout headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            // Collapsible Chevron Indicator (Borderless, vertically centered at left)
            final TextView btnToggle = new TextView(context);
            btnToggle.setText("❯");
            btnToggle.setRotation(isExpanded ? 90 : 0);
            btnToggle.setTextSize(16);
            btnToggle.setTextColor(Color.parseColor("#BB86FC"));
            btnToggle.setGravity(Gravity.CENTER);
            btnToggle.setPadding(12, 8, 16, 8);

            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            toggleParams.setMargins(8, 0, 16, 0);
            toggleParams.gravity = Gravity.CENTER_VERTICAL;
            btnToggle.setLayoutParams(toggleParams);

            TextView providerTitle = new TextView(context);
            providerTitle.setText(pName);
            providerTitle.setTextColor(Color.parseColor("#BB86FC"));
            providerTitle.setTextSize(18);
            providerTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
            providerTitle.setLayoutParams(titleParams);

            TextView apiKeyStatus = new TextView(context);
            if (hasApiKey) {
                apiKeyStatus.setText("● Active");
                apiKeyStatus.setTextColor(Color.parseColor("#03DAC6"));
            } else {
                apiKeyStatus.setText("● Key Required");
                apiKeyStatus.setTextColor(Color.parseColor("#CF6679"));
            }
            apiKeyStatus.setTextSize(12);
            apiKeyStatus.setPadding(8, 0, 12, 0);

            // 🔑 Icon Button for setting/editing API key
            Button btnKey = new Button(context);
            btnKey.setText("🔑");
            btnKey.setTextSize(14);
            btnKey.setPadding(16, 8, 16, 8);

            GradientDrawable keyBtnBg = new GradientDrawable();
            keyBtnBg.setColor(Color.parseColor("#2A2D3E"));
            keyBtnBg.setCornerRadius(12f);
            btnKey.setBackground(keyBtnBg);
            btnKey.setTextColor(Color.parseColor("#FFFFFF"));

            btnKey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showApiKeyDialog(context, pName);
                }
            });

            LinearLayout.LayoutParams keyBtnParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            keyBtnParams.setMargins(8, 0, 0, 0);
            btnKey.setLayoutParams(keyBtnParams);

            headerRow.addView(btnToggle);
            headerRow.addView(providerTitle);
            headerRow.addView(apiKeyStatus);
            headerRow.addView(btnKey);

            card.addView(headerRow);

            // Billing Info Row if Key is Present
            if (hasApiKey) {
                TextView billingView = new TextView(context);
                billingView.setText("💳 " + provider.getBillingInfo(apiKey));
                billingView.setTextColor(Color.parseColor("#8E8EA0"));
                billingView.setTextSize(11);
                billingView.setPadding(0, 4, 0, 4);
                card.addView(billingView);
            }

            // Budget Bar Section - query budget if server budget info or local usage is available
            long usedTokens = router.getUsedTokens(context, pName);
            long totalBudget = router.getTokenBudget(context, pName);

            // Fetch live budget info from server if key is set
            if (hasApiKey) {
                provider.fetchBudgetInfo(apiKey, new AiProvider.BudgetCallback() {
                    @Override
                    public void onSuccess(AiProvider.BudgetInfo budgetInfo) {
                        if (budgetInfo != null && getContext() != null) {
                            if (budgetInfo.getTotalBudget() > 0) {
                                router.setTokenBudget(getContext(), pName, (int) budgetInfo.getTotalBudget());
                            }
                            if (budgetInfo.getUsedTokens() > 0) {
                                int storedUsed = router.getUsedTokens(getContext(), pName);
                                if (budgetInfo.getUsedTokens() > storedUsed) {
                                    router.addUsedTokens(getContext(), pName, (int) (budgetInfo.getUsedTokens() - storedUsed));
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // Ignore error, fallback to stored budget
                    }
                });
            }

            // Only show budget bar if totalBudget > 0
            if (hasApiKey && totalBudget > 0) {
                long tokensLeft = Math.max(0, totalBudget - usedTokens);
                float usedRatio = Math.max(0f, Math.min(1f, (float) usedTokens / (float) totalBudget));

                LinearLayout budgetContainer = new LinearLayout(context);
                budgetContainer.setOrientation(LinearLayout.VERTICAL);
                budgetContainer.setPadding(0, 8, 0, 8);

                TextView budgetLabel = new TextView(context);
                budgetLabel.setText(String.format("📊 %,d/%,d tokens used", usedTokens, totalBudget));
                budgetLabel.setTextColor(Color.parseColor("#A0A0B0"));
                budgetLabel.setTextSize(12);
                budgetLabel.setPadding(0, 0, 0, 6);

                FrameLayout barTrack = new FrameLayout(context);
                GradientDrawable trackBg = new GradientDrawable();
                trackBg.setColor(Color.parseColor("#2A2D3E"));
                trackBg.setCornerRadius(8f);
                barTrack.setBackground(trackBg);

                LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(MATCH_PARENT, 16);
                trackParams.setMargins(0, 2, 0, 4);
                barTrack.setLayoutParams(trackParams);

                View barFill = new View(context);
                String barColor;
                if (usedRatio < 0.60f) {
                    barColor = "#03DAC6"; // Green: Low usage (< 60%)
                } else if (usedRatio < 0.85f) {
                    barColor = "#FFC107"; // Yellow: Moderate usage (60% - 85%)
                } else {
                    barColor = "#CF6679"; // Red: High usage (> 85%)
                }

                GradientDrawable fillBg = new GradientDrawable();
                fillBg.setColor(Color.parseColor(barColor));
                fillBg.setCornerRadius(8f);
                barFill.setBackground(fillBg);

                LinearLayout fillContainer = new LinearLayout(context);
                fillContainer.setOrientation(LinearLayout.HORIZONTAL);
                fillContainer.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

                float safeRatio = Math.max(0.01f, usedRatio);
                barFill.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, safeRatio));
                fillContainer.addView(barFill);

                if (usedRatio < 0.99f) {
                    View emptySpace = new View(context);
                    emptySpace.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1.0f - usedRatio));
                    fillContainer.addView(emptySpace);
                }

                barTrack.addView(fillContainer);

                budgetContainer.addView(budgetLabel);
                budgetContainer.addView(barTrack);
                card.addView(budgetContainer);
            }

            // Models Container
            final LinearLayout modelsContainer = new LinearLayout(context);
            modelsContainer.setOrientation(LinearLayout.VERTICAL);
            modelsContainer.setPadding(8, 16, 8, 8);
            modelsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            // Toggle Expand / Collapse Action or Request API Key if Missing
            View.OnClickListener toggleClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!hasApiKey) {
                        showApiKeyDialog(context, pName);
                    } else {
                        boolean newExpandedState = modelsContainer.getVisibility() != View.VISIBLE;
                        mExpandedState.put(pName, newExpandedState);
                        modelsContainer.setVisibility(newExpandedState ? View.VISIBLE : View.GONE);
                        btnToggle.setRotation(newExpandedState ? 90 : 0);
                    }
                }
            };

            btnToggle.setOnClickListener(toggleClickListener);
            providerTitle.setOnClickListener(toggleClickListener);

            if (!hasApiKey) {
                // Requirement: Do NOT display models without API key
                TextView noKeyWarning = new TextView(context);
                noKeyWarning.setText("⚠️ API key required to view and select models for " + pName + ".");
                noKeyWarning.setTextColor(Color.parseColor("#CF6679"));
                noKeyWarning.setTextSize(13);
                noKeyWarning.setPadding(12, 12, 12, 12);
                noKeyWarning.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showApiKeyDialog(context, pName);
                    }
                });
                modelsContainer.addView(noKeyWarning);
            } else {
                List<String> availableModels = provider.getAvailableModels();
                if (availableModels.isEmpty()) {
                    TextView noModelsNotice = new TextView(context);
                    noModelsNotice.setText("ℹ️ No models configured yet for " + pName + ".");
                    noModelsNotice.setTextColor(Color.parseColor("#A0A0B0"));
                    noModelsNotice.setTextSize(13);
                    noModelsNotice.setPadding(12, 12, 12, 12);
                    modelsContainer.addView(noModelsNotice);
                } else {
                    String currentActiveModel = router.getActiveModelName(pName);

                    for (final String modelName : availableModels) {
                        final boolean isSelectedModel = isProviderActive && modelName.equalsIgnoreCase(currentActiveModel);

                        final LinearLayout modelRow = new LinearLayout(context);
                        modelRow.setOrientation(LinearLayout.HORIZONTAL);
                        modelRow.setGravity(Gravity.CENTER_VERTICAL);
                        modelRow.setPadding(20, 16, 20, 16);

                        GradientDrawable modelBg = new GradientDrawable();
                        if (isSelectedModel) {
                            modelBg.setColor(Color.parseColor("#25383C"));
                            modelBg.setStroke(2, Color.parseColor("#03DAC6"));
                        } else {
                            modelBg.setColor(Color.parseColor("#181824"));
                            modelBg.setStroke(1, Color.parseColor("#2E2E3E"));
                        }
                        modelBg.setCornerRadius(10f);
                        modelRow.setBackground(modelBg);

                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                        rowParams.setMargins(0, 8, 0, 8);
                        modelRow.setLayoutParams(rowParams);

                        // Custom Radio Icon (Checkmark / Circle chip)
                        TextView selectionBadge = new TextView(context);
                        if (isSelectedModel) {
                            selectionBadge.setText("✔");
                            selectionBadge.setTextColor(Color.parseColor("#03DAC6"));
                        } else {
                            selectionBadge.setText("○");
                            selectionBadge.setTextColor(Color.parseColor("#6E6E80"));
                        }
                        selectionBadge.setTextSize(16);
                        selectionBadge.setTypeface(null, Typeface.BOLD);
                        selectionBadge.setPadding(0, 0, 16, 0);

                        // Model Name
                        TextView modelTitle = new TextView(context);
                        modelTitle.setText(modelName);
                        modelTitle.setTextSize(14);
                        modelTitle.setTextColor(Color.parseColor(isSelectedModel ? "#03DAC6" : "#E0E0E0"));
                        if (isSelectedModel) {
                            modelTitle.setTypeface(null, Typeface.BOLD);
                        }
                        LinearLayout.LayoutParams titleTextParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
                        modelTitle.setLayoutParams(titleTextParams);

                        // Active Label Chip
                        if (isSelectedModel) {
                            TextView activeChip = new TextView(context);
                            activeChip.setText("ACTIVE");
                            activeChip.setTextSize(10);
                            activeChip.setTypeface(null, Typeface.BOLD);
                            activeChip.setTextColor(Color.parseColor("#03DAC6"));
                            activeChip.setPadding(12, 4, 12, 4);

                            GradientDrawable chipBg = new GradientDrawable();
                            chipBg.setColor(Color.parseColor("#102A29"));
                            chipBg.setCornerRadius(8f);
                            activeChip.setBackground(chipBg);

                            modelRow.addView(selectionBadge);
                            modelRow.addView(modelTitle);
                            modelRow.addView(activeChip);
                        } else {
                            modelRow.addView(selectionBadge);
                            modelRow.addView(modelTitle);
                        }

                        modelRow.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AiRouter.getInstance().saveActiveSelection(context, pName, modelName);
                                Toast.makeText(context, "Active model set: " + pName + " - " + modelName, Toast.LENGTH_SHORT).show();
                                renderProviderCards(context);
                            }
                        });

                        modelsContainer.addView(modelRow);
                    }
                }
            }

            card.addView(modelsContainer);
            mCardsContainer.addView(card);
        }
    }

    private void showApiKeyDialog(final Context context, final String providerName) {
        final AiProvider providerObj = AiRouter.getInstance().getProvider(providerName);
        if (providerObj == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        final EditText input = new EditText(context);
        input.setHint("Enter " + providerName + " API Key");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        String currentKey = mDbHelper != null ? mDbHelper.getApiKey(providerName) : "";
        if (!TextUtils.isEmpty(currentKey)) {
            input.setText(currentKey);
        }

        layout.addView(input);

        final ProgressBar progressBar = new ProgressBar(context);
        progressBar.setVisibility(View.GONE);
        layout.addView(progressBar);

        final TextView errorTextView = new TextView(context);
        errorTextView.setTextColor(Color.parseColor("#CF6679"));
        errorTextView.setTextSize(12);
        errorTextView.setPadding(0, 12, 0, 0);
        errorTextView.setVisibility(View.GONE);
        layout.addView(errorTextView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Configure " + providerName + " API Key 🔑");
        builder.setView(layout);

        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
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
                    if (mDbHelper != null) {
                        mDbHelper.saveApiKey(providerName, "");
                    }
                    renderProviderCards(context);
                    Toast.makeText(context, providerName + " API Key removed", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }

                progressBar.setVisibility(View.VISIBLE);
                errorTextView.setVisibility(View.GONE);
                input.setEnabled(false);
                positiveButton.setEnabled(false);

                LogViewerService.getInstance().i(TAG, "Starting validation for " + providerName + " API Key...");

                providerObj.validateKey(key, new AiProvider.ValidationCallback() {
                    @Override
                    public void onSuccess(List<String> models) {
                        if (mDbHelper != null) {
                            mDbHelper.saveApiKey(providerName, key);
                            AiRouter.getInstance().saveCachedModels(context, providerName, models);
                            LogViewerService.getInstance().i(TAG, providerName + " API Key validated and saved to SQLite DB.");
                        }
                        renderProviderCards(context);
                        Toast.makeText(context, providerName + " API Key validated & saved!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progressBar.setVisibility(View.GONE);
                        input.setEnabled(true);
                        positiveButton.setEnabled(true);
                        errorTextView.setText(errorMessage);
                        errorTextView.setVisibility(View.VISIBLE);
                        LogViewerService.getInstance().w(TAG, providerName + " API Key validation error: " + errorMessage);
                    }
                });
            }
        });
    }
}
