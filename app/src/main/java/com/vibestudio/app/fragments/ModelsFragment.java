package com.vibestudio.app.fragments;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.provider.GithubCopilotAiProvider;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.service.LogViewerService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModelsFragment extends Fragment {

    private static final String TAG = "ModelsFragment";
    private static final int MATCH_PARENT = -1;
    private static final int WRAP_CONTENT = -2;

    private DatabaseHelper mDbHelper;
    private LinearLayout mCardsContainer;

    // Track collapse state per provider
    private final Map<String, Boolean> mExpandedState = new HashMap<>();
    // Providers whose model list has already been refreshed from the network this session.
    private final Set<String> mModelsRefreshed = new HashSet<>();

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

            if (hasApiKey) {
                TextView billingView = new TextView(context);
                billingView.setText("💳 " + provider.getBillingInfo(apiKey));
                billingView.setTextColor(Color.parseColor("#8E8EA0"));
                billingView.setTextSize(11);
                billingView.setPadding(0, 4, 0, 4);
                card.addView(billingView);
            }

            long usedTokens = router.getUsedTokens(context, pName);
            long totalBudget = router.getTokenBudget(context, pName);

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
                    }
                });
            }

            if (hasApiKey && totalBudget > 0) {
                long tokensLeft = Math.max(0, totalBudget - usedTokens);
                float usedRatio = Math.max(0f, Math.min(1f, (float) usedTokens / (float) totalBudget));

                LinearLayout budgetContainer = new LinearLayout(context);
                budgetContainer.setOrientation(LinearLayout.VERTICAL);
                budgetContainer.setPadding(0, 8, 0, 8);

                TextView budgetLabel = new TextView(context);
                budgetLabel.setText(String.format("📊 %,d/%,d tokens used", usedTokens, totalBudget));
                budgetLabel.setTextColor(Color.parseColor("#8E8EA0"));
                budgetLabel.setTextSize(11);

                LinearLayout barBg = new LinearLayout(context);
                barBg.setBackgroundColor(Color.parseColor("#2E2E3E"));
                LinearLayout.LayoutParams barBgParams = new LinearLayout.LayoutParams(MATCH_PARENT, 8);
                barBgParams.setMargins(0, 4, 0, 4);
                barBg.setLayoutParams(barBgParams);

                View barFill = new View(context);
                int fillColor = (usedRatio > 0.9f) ? Color.parseColor("#CF6679")
                        : (usedRatio > 0.7f) ? Color.parseColor("#FFB74D")
                        : Color.parseColor("#03DAC6");
                barFill.setBackgroundColor(fillColor);
                LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, MATCH_PARENT, usedRatio);
                barFill.setLayoutParams(fillParams);

                View barEmpty = new View(context);
                LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f - usedRatio);
                barEmpty.setLayoutParams(emptyParams);

                barBg.addView(barFill);
                barBg.addView(barEmpty);

                budgetContainer.addView(budgetLabel);
                budgetContainer.addView(barBg);

                card.addView(budgetContainer);
            }

            final LinearLayout modelsContainer = new LinearLayout(context);
            modelsContainer.setOrientation(LinearLayout.VERTICAL);
            modelsContainer.setPadding(0, 12, 0, 0);
            modelsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

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
                TextView noKeyWarning = new TextView(context);
                if (pName.equalsIgnoreCase("GitHub Copilot")) {
                    noKeyWarning.setText("⚠️ GitHub Copilot authentication required to view and select models.");
                } else {
                    noKeyWarning.setText("⚠️ API key required to view and select models for " + pName + ".");
                }
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
                    TextView loadingText = new TextView(context);
                    loadingText.setText("Loading available models...");
                    loadingText.setTextColor(Color.parseColor("#8E8EA0"));
                    loadingText.setTextSize(13);
                    loadingText.setPadding(12, 12, 12, 12);
                    modelsContainer.addView(loadingText);

                    mModelsRefreshed.add(pName);
                    provider.fetchAvailableModels(apiKey, new AiProvider.ModelsCallback() {
                        @Override
                        public void onSuccess(List<String> models) {
                            if (getContext() != null) {
                                AiRouter.getInstance().saveCachedModels(getContext(), pName, models);
                                renderProviderCards(getContext());
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (getContext() != null) {
                                renderProviderCards(getContext());
                            }
                        }
                    });
                } else {
                    // getAvailableModels() falls back to the default model when nothing has been
                    // fetched yet, so refresh from the network once per session to get the real list.
                    if (!mModelsRefreshed.contains(pName)) {
                        mModelsRefreshed.add(pName);
                        final List<String> shownModels = availableModels;
                        provider.fetchAvailableModels(apiKey, new AiProvider.ModelsCallback() {
                            @Override
                            public void onSuccess(List<String> models) {
                                if (getContext() != null && models != null && !models.equals(shownModels)) {
                                    AiRouter.getInstance().saveCachedModels(getContext(), pName, models);
                                    renderProviderCards(getContext());
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                LogViewerService.getInstance().w(TAG, "Model refresh failed for " + pName + ": " + errorMessage);
                            }
                        });
                    }

                    String currentActiveModel = router.getActiveModelName(pName);
                    boolean hasMatch = false;
                    for (String m : availableModels) {
                        if (m != null && m.equalsIgnoreCase(currentActiveModel)) {
                            hasMatch = true;
                            break;
                        }
                    }

                    if (!hasMatch && !availableModels.isEmpty()) {
                        String defModel = provider.getDefaultModel();
                        if (defModel != null) {
                            for (String m : availableModels) {
                                if (m.equalsIgnoreCase(defModel)) {
                                    currentActiveModel = m;
                                    hasMatch = true;
                                    break;
                                }
                            }
                        }
                        if (!hasMatch) {
                            currentActiveModel = availableModels.get(0);
                        }
                    }

                    for (final String modelName : availableModels) {
                        final boolean isSelectedModel = modelName.equalsIgnoreCase(currentActiveModel);
                        final boolean isGloballyActive = isProviderActive && isSelectedModel;

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

                        TextView modelTitle = new TextView(context);
                        modelTitle.setText(modelName);
                        modelTitle.setTextSize(14);
                        modelTitle.setTextColor(Color.parseColor(isSelectedModel ? "#03DAC6" : "#E0E0E0"));
                        if (isSelectedModel) {
                            modelTitle.setTypeface(null, Typeface.BOLD);
                        }
                        LinearLayout.LayoutParams titleTextParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
                        modelTitle.setLayoutParams(titleTextParams);

                        if (isGloballyActive) {
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
        input.setHint(providerName.equalsIgnoreCase("GitHub Copilot") ? "GitHub Token (gho_...)" : "Enter " + providerName + " API Key");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        String currentKey = mDbHelper != null ? mDbHelper.getApiKey(providerName) : "";
        if (!TextUtils.isEmpty(currentKey)) {
            input.setText(currentKey);
        }

        final ProgressBar progressBar = new ProgressBar(context);
        progressBar.setVisibility(View.GONE);

        final TextView errorTextView = new TextView(context);
        errorTextView.setTextColor(Color.parseColor("#CF6679"));
        errorTextView.setTextSize(12);
        errorTextView.setPadding(0, 12, 0, 0);
        errorTextView.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(providerName.equalsIgnoreCase("GitHub Copilot") ? "GitHub Copilot Login 🚀" : "Configure " + providerName + " API Key 🔑");

        if (providerObj instanceof GithubCopilotAiProvider) {
            final GithubCopilotAiProvider copilotProvider = (GithubCopilotAiProvider) providerObj;

            TextView instructions = new TextView(context);
            instructions.setText("Login via GitHub Browser flow (Device Code) or paste an existing GitHub OAuth Token below.");
            instructions.setTextColor(Color.parseColor("#BB86FC"));
            instructions.setTextSize(13);
            instructions.setPadding(0, 0, 0, 16);
            layout.addView(instructions);

            final Button btnLoginGithub = new Button(context);
            btnLoginGithub.setText("🔐 Login with GitHub");
            btnLoginGithub.setTextColor(Color.WHITE);
            btnLoginGithub.setTextSize(14);
            btnLoginGithub.setTypeface(null, Typeface.BOLD);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(Color.parseColor("#238636"));
            btnBg.setCornerRadius(12f);
            btnLoginGithub.setBackground(btnBg);
            btnLoginGithub.setPadding(24, 16, 24, 16);
            layout.addView(btnLoginGithub);

            final LinearLayout codeContainer = new LinearLayout(context);
            codeContainer.setOrientation(LinearLayout.VERTICAL);
            codeContainer.setPadding(16, 16, 16, 16);
            codeContainer.setVisibility(View.GONE);

            GradientDrawable codeBg = new GradientDrawable();
            codeBg.setColor(Color.parseColor("#161B22"));
            codeBg.setCornerRadius(12f);
            codeBg.setStroke(2, Color.parseColor("#30363D"));
            codeContainer.setBackground(codeBg);

            LinearLayout.LayoutParams codeBoxParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            codeBoxParams.setMargins(0, 16, 0, 16);
            codeContainer.setLayoutParams(codeBoxParams);

            final TextView userCodeText = new TextView(context);
            userCodeText.setTextSize(22);
            userCodeText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            userCodeText.setTextColor(Color.parseColor("#58A6FF"));
            userCodeText.setGravity(Gravity.CENTER);
            userCodeText.setPadding(8, 12, 8, 12);
            codeContainer.addView(userCodeText);

            final Button btnCopyOpenChrome = new Button(context);
            btnCopyOpenChrome.setText("📋 Copy Code & Open Chrome");
            btnCopyOpenChrome.setTextColor(Color.WHITE);
            btnCopyOpenChrome.setTextSize(13);

            GradientDrawable copyBtnBg = new GradientDrawable();
            copyBtnBg.setColor(Color.parseColor("#1F6FEB"));
            copyBtnBg.setCornerRadius(8f);
            btnCopyOpenChrome.setBackground(copyBtnBg);
            codeContainer.addView(btnCopyOpenChrome);

            final TextView statusText = new TextView(context);
            statusText.setText("Waiting for authorization on GitHub...");
            statusText.setTextColor(Color.parseColor("#8B949E"));
            statusText.setTextSize(12);
            statusText.setGravity(Gravity.CENTER);
            statusText.setPadding(0, 12, 0, 0);
            codeContainer.addView(statusText);

            layout.addView(codeContainer);

            TextView labelOr = new TextView(context);
            labelOr.setText("— OR PASTE TOKEN MANUALLY —");
            labelOr.setTextColor(Color.parseColor("#8B949E"));
            labelOr.setTextSize(11);
            labelOr.setGravity(Gravity.CENTER);
            labelOr.setPadding(0, 20, 0, 8);
            layout.addView(labelOr);

            layout.addView(input);
            layout.addView(progressBar);
            layout.addView(errorTextView);

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

            final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            btnLoginGithub.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btnLoginGithub.setEnabled(false);
                    progressBar.setVisibility(View.VISIBLE);
                    errorTextView.setVisibility(View.GONE);

                    copilotProvider.requestDeviceCode(new GithubCopilotAiProvider.DeviceCodeCallback() {
                        @Override
                        public void onSuccess(final GithubCopilotAiProvider.DeviceCodeResponse response) {
                            progressBar.setVisibility(View.GONE);
                            codeContainer.setVisibility(View.VISIBLE);
                            userCodeText.setText(response.userCode);

                            final String verificationUri = response.verificationUri;

                            View.OnClickListener openChromeAction = new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                    if (clipboard != null) {
                                        ClipData clip = ClipData.newPlainText("GitHub User Code", response.userCode);
                                        clipboard.setPrimaryClip(clip);
                                    }
                                    Toast.makeText(context, "Code " + response.userCode + " copied! Paste in Chrome.", Toast.LENGTH_LONG).show();

                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        context.startActivity(intent);
                                    } catch (Exception e) {
                                        Toast.makeText(context, "Unable to launch browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                };
                            };

                            btnCopyOpenChrome.setOnClickListener(openChromeAction);

                            openChromeAction.onClick(null);

                            statusText.setText("Code " + response.userCode + " copied! Authorizing on GitHub...");

                            copilotProvider.pollAccessToken(response.deviceCode, response.interval, new GithubCopilotAiProvider.TokenPollCallback() {
                                @Override
                                public void onSuccess(String accessToken) {
                                    if (mDbHelper != null) {
                                        mDbHelper.saveApiKey(providerName, accessToken);
                                    }
                                    input.setText(accessToken);
                                    Toast.makeText(context, "GitHub Copilot Authenticated Successfully! 🎉", Toast.LENGTH_LONG).show();
                                    renderProviderCards(context);
                                    dialog.dismiss();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    btnLoginGithub.setEnabled(true);
                                    errorTextView.setText(errorMessage);
                                    errorTextView.setVisibility(View.VISIBLE);
                                    statusText.setText("Authentication failed or expired.");
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            btnLoginGithub.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            errorTextView.setText(errorMessage);
                            errorTextView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });

            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String key = input.getText().toString().trim();
                    if (TextUtils.isEmpty(key)) {
                        if (mDbHelper != null) {
                            mDbHelper.saveApiKey(providerName, "");
                        }
                        renderProviderCards(context);
                        Toast.makeText(context, providerName + " Token removed", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return;
                    }

                    progressBar.setVisibility(View.VISIBLE);
                    errorTextView.setVisibility(View.GONE);
                    input.setEnabled(false);
                    positiveButton.setEnabled(false);

                    providerObj.validateKey(key, new AiProvider.ValidationCallback() {
                        @Override
                        public void onSuccess(List<String> models) {
                            if (mDbHelper != null) {
                                mDbHelper.saveApiKey(providerName, key);
                                AiRouter.getInstance().saveCachedModels(context, providerName, models);
                            }
                            renderProviderCards(context);
                            Toast.makeText(context, providerName + " Authenticated & Saved!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            input.setEnabled(true);
                            positiveButton.setEnabled(true);
                            errorTextView.setText(errorMessage);
                            errorTextView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });

        } else {
            layout.addView(input);
            layout.addView(progressBar);
            layout.addView(errorTextView);

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
}
