package com.vibestudio.app.chat.provider;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.prompt.PromptAssets;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractAiProvider implements AiProvider {

    protected final List<String> mAvailableModels = Collections.synchronizedList(new ArrayList<>());
    protected final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public List<String> getAvailableModels() {
        synchronized (mAvailableModels) {
            if (mAvailableModels.isEmpty()) {
                String def = getDefaultModel();
                if (def != null && !def.isEmpty()) {
                    return Collections.singletonList(def);
                }
            }
            return new ArrayList<>(mAvailableModels);
        }
    }

    @Override
    public void setAvailableModels(List<String> models) {
        synchronized (mAvailableModels) {
            mAvailableModels.clear();
            if (models != null) {
                for (String m : models) {
                    if (m != null && !m.trim().isEmpty() && !mAvailableModels.contains(m.trim())) {
                        mAvailableModels.add(m.trim());
                    }
                }
            }
        }
    }

    @Override
    public void fetchAvailableModels(final String apiKey, final ModelsCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> models = fetchAvailableModelsSync(apiKey);
                    setAvailableModels(models);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onSuccess(models);
                            }
                        }
                    });
                } catch (final Exception e) {
                    final String err = (e.getMessage() != null && !e.getMessage().isEmpty())
                            ? e.getMessage()
                            : "Failed to fetch models for " + getName();
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError(err);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public BudgetInfo fetchBudgetInfoSync(String apiKey) throws Exception {
        return new BudgetInfo(0, 100000);
    }

    @Override
    public void fetchBudgetInfo(final String apiKey, final BudgetCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BudgetInfo info = fetchBudgetInfoSync(apiKey);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onSuccess(info);
                            }
                        }
                    });
                } catch (final Exception e) {
                    final String err = (e.getMessage() != null && !e.getMessage().isEmpty())
                            ? e.getMessage()
                            : "Failed to fetch budget for " + getName();
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError(err);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void validateKey(final String apiKey, final ValidationCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> models = fetchAvailableModelsSync(apiKey);
                    setAvailableModels(models);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onSuccess(models);
                            }
                        }
                    });
                } catch (final Exception e) {
                    final String err = (e.getMessage() != null && !e.getMessage().isEmpty())
                            ? e.getMessage()
                            : "Validation failed for " + getName();
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError(err);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public String getBillingInfo(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "No API Key configured.";
        }
        return getName() + " API Key Active";
    }

    @Override
    public String getSystemInstruction(Context context) {
        return PromptAssets.read(context, "system_instruction.md");
    }

    protected void logRequestDetails(String tag, String method, String url, Map<String, List<String>> headers, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HTTP ").append(method).append(" REQUEST ===\n");
        sb.append("URL: ").append(url).append("\n");

        if (body != null && !body.isEmpty()) {
            sb.append("Body:\n").append(body).append("\n");
        }
        sb.append("=========================");
        LogViewerService.getInstance().i(tag, sb.toString());
    }

    protected void logResponseDetails(String tag, int statusCode, Map<String, List<String>> headers, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HTTP RESPONSE (Status ").append(statusCode).append(") ===\n");
        if (headers != null) {
            sb.append("Headers:\n");
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null) {
                    sb.append("  ").append(entry.getKey()).append(": ");
                    if (entry.getValue() != null) {
                        sb.append(joinHeaderValues(entry.getValue()));
                    }
                    sb.append("\n");
                }
            }
        }
        if (body != null && !body.isEmpty()) {
            sb.append("Body:\n").append(body).append("\n");
        }
        sb.append("=========================");
        LogViewerService.getInstance().i(tag, sb.toString());
    }

    private String joinHeaderValues(List<String> values) {
        if (values == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    protected static int calculateTokenUsage(String systemInstruction, List<ChatMessage> history, String responseText) {
        int chars = 0;
        if (systemInstruction != null) chars += systemInstruction.length();
        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg != null && msg.getText() != null) {
                    chars += msg.getText().length();
                }
            }
        }
        if (responseText != null) chars += responseText.length();
        return Math.max(1, chars / 4);
    }

    /**
     * A native tool call in the {"planned_delay_ms", "tool", "args"} form that ToolExecutionManager parses. A
     * model-supplied planned_delay_ms (only declared on async tools) is lifted out of the args to the top level.
     */
    protected static JSONObject toolCallObject(String name, JSONObject args) {
        try {
            JSONObject toolArgs = args != null ? new JSONObject(args.toString()) : new JSONObject();
            long delayMs = Math.max(0L, toolArgs.optLong(McpClientManager.PLANNED_DELAY_FIELD, 0L));
            toolArgs.remove(McpClientManager.PLANNED_DELAY_FIELD);
            JSONObject call = new JSONObject();
            call.put(McpClientManager.PLANNED_DELAY_FIELD, delayMs);
            call.put("tool", name);
            call.put("args", toolArgs);
            return call;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Same call rendered as the JSON text that goes into a reply's content. */
    protected static String toolCallContent(String name, JSONObject args) {
        return toolCallObject(name, args).toString();
    }

    /** An id for a tool call whose API didn't issue one. */
    protected static String newCallId() {
        return "call_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    /** The tool's JSON-schema parameters, or null for argument-less tools (some APIs reject empty object schemas). */
    protected static JSONObject toolParameters(McpTool tool) {
        JSONObject schema = tool.toJsonSchema().optJSONObject("inputSchema");
        return schema != null && schema.has("properties") ? schema : null;
    }

    protected static String censorKey(String token) {
        if (token == null || token.length() <= 8) {
            return "***CENSORED***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
