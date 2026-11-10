package com.vibestudio.app.chat.provider;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.model.McpTool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Request building and response parsing for Copilot's /responses endpoint (OpenAI Responses API shape). */
final class CopilotResponsesApi {

    /** What the rest of the provider needs from a response; {@code error} is set when the model run failed. */
    static final class Result {
        final String text;
        final String reasoning;
        final int tokens;
        final String error;
        final List<AiResponse.ToolCall> toolCalls;

        Result(String text, String reasoning, int tokens, String error, List<AiResponse.ToolCall> toolCalls) {
            this.text = text;
            this.reasoning = reasoning;
            this.tokens = tokens;
            this.error = error;
            this.toolCalls = toolCalls;
        }
    }

    private CopilotResponsesApi() {
    }

    /**
     * Same conversation as the chat-completions body: the system prompt becomes {@code instructions}, history goes
     * out as plain user/assistant messages, and tools use the flat function format. Mirrors VS Code's request
     * (stream, store=false, strict=false); the stream is read to the end by the caller.
     */
    static JSONObject buildRequest(String model, String systemInstruction, List<ChatMessage> history, List<McpTool> tools) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
            payload.put("instructions", systemInstruction);
        }

        JSONArray input = new JSONArray();
        boolean nativeTools = tools != null && !tools.isEmpty();
        for (NativeHistory.Item item : NativeHistory.project(history, nativeTools, null)) {
            switch (item.kind) {
                case USER:
                    if (item.text != null && !item.text.isEmpty()) {
                        input.put(new JSONObject().put("type", "message").put("role", "user")
                                .put("content", new JSONArray().put(new JSONObject().put("type", "input_text").put("text", item.text))));
                    }
                    break;
                case ASSISTANT:
                    if (item.text != null && !item.text.trim().isEmpty()) {
                        input.put(new JSONObject().put("type", "message").put("role", "assistant")
                                .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", item.text))));
                    }
                    break;
                case ASSISTANT_CALLS:
                    // Reasoning items go in front of the calls they led to, and only to the model that produced them.
                    for (JSONObject reasoning : reasoningItemsFor(item.calls.get(0), model)) {
                        input.put(reasoning);
                    }
                    for (NativeHistory.Call call : item.calls) {
                        input.put(new JSONObject().put("type", "function_call").put("name", call.name)
                                .put("arguments", call.nativeArgs().toString()).put("call_id", call.id));
                    }
                    break;
                default:
                    input.put(new JSONObject().put("type", "function_call_output").put("call_id", item.call.id).put("output", item.text));
                    break;
            }
        }
        payload.put("input", input);

        if (tools != null && !tools.isEmpty()) {
            JSONArray toolsArray = new JSONArray();
            for (McpTool tool : tools) {
                JSONObject parameters = AbstractAiProvider.toolParameters(tool);
                toolsArray.put(new JSONObject()
                        .put("type", "function")
                        .put("name", tool.getName())
                        .put("description", tool.getDescription())
                        .put("parameters", parameters != null ? parameters : new JSONObject().put("type", "object").put("properties", new JSONObject()))
                        .put("strict", false));
            }
            payload.put("tools", toolsArray);
            payload.put("tool_choice", "auto");
        }

        if (supportsReasoning(model)) {
            // Summaries are only returned on request; the encrypted items let reasoning be replayed with store=false.
            payload.put("reasoning", new JSONObject().put("effort", "medium").put("summary", "auto"));
            payload.put("include", new JSONArray().put("reasoning.encrypted_content"));
        }

        payload.put("stream", true);
        payload.put("store", false);
        return payload;
    }

    /** Models known to accept the {@code reasoning} parameter; others may answer HTTP 400. */
    static boolean supportsReasoning(String model) {
        if (model == null) return false;
        String name = model.toLowerCase(java.util.Locale.ROOT);
        return name.matches("gpt-([5-9]|\\d{2,}).*") || name.matches("o\\d.*");
    }

    static final String STATE_KIND = "responses";

    private static List<JSONObject> reasoningItemsFor(NativeHistory.Call firstCall, String model) {
        List<JSONObject> items = new ArrayList<>();
        if (firstCall.state == null || !STATE_KIND.equals(firstCall.stateKind)) return items;
        try {
            JSONObject state = new JSONObject(firstCall.state);
            if (!model.equals(state.optString("model"))) return items;
            JSONArray raw = state.optJSONArray("reasoning");
            for (int i = 0; raw != null && i < raw.length(); i++) {
                items.add(raw.getJSONObject(i));
            }
        } catch (JSONException ignored) {
            // unreadable state: the call is still sent, just without its reasoning
        }
        return items;
    }

    /** Parses either a plain JSON response or an SSE stream that ends with a response.completed event. */
    static Result parse(String body, String model) throws JSONException {
        JSONObject response = extractResponse(body);

        JSONObject error = response.optJSONObject("error");
        if (error != null || "failed".equals(response.optString("status"))) {
            String message = error != null ? error.optString("message", error.toString()) : "response failed";
            return new Result("", null, 0, message, new ArrayList<AiResponse.ToolCall>());
        }

        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder toolCalls = new StringBuilder();
        List<AiResponse.ToolCall> nativeCalls = new ArrayList<>();
        JSONArray reasoningItems = new JSONArray();

        JSONArray output = response.optJSONArray("output");
        for (int i = 0; output != null && i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            switch (item.optString("type")) {
                case "message": {
                    JSONArray content = item.optJSONArray("content");
                    for (int j = 0; content != null && j < content.length(); j++) {
                        JSONObject part = content.optJSONObject(j);
                        if (part == null) continue;
                        if ("output_text".equals(part.optString("type"))) {
                            text.append(part.optString("text"));
                        } else if ("refusal".equals(part.optString("type"))) {
                            text.append(part.optString("refusal"));
                        }
                    }
                    break;
                }
                case "reasoning": {
                    if (item.has("encrypted_content")) {
                        // Only the fields the API accepts back on input.
                        reasoningItems.put(new JSONObject().put("type", "reasoning").put("id", item.optString("id"))
                                .put("summary", new JSONArray()).put("encrypted_content", item.optString("encrypted_content")));
                    }
                    JSONArray summary = item.optJSONArray("summary");
                    for (int j = 0; summary != null && j < summary.length(); j++) {
                        JSONObject part = summary.optJSONObject(j);
                        if (part == null) continue;
                        if (reasoning.length() > 0) reasoning.append('\n');
                        reasoning.append(part.optString("text"));
                    }
                    break;
                }
                case "function_call": {
                    String arguments = item.optString("arguments", "{}").trim();
                    JSONObject call = AbstractAiProvider.toolCallObject(item.optString("name"),
                            new JSONObject(arguments.isEmpty() ? "{}" : arguments));
                    String callId = item.optString("call_id", "");
                    // The reasoning of the whole reply is replayed with its first call.
                    String state = nativeCalls.isEmpty() && reasoningItems.length() > 0
                            ? new JSONObject().put("model", model).put("reasoning", reasoningItems).toString() : null;
                    nativeCalls.add(new AiResponse.ToolCall(callId.isEmpty() ? AbstractAiProvider.newCallId() : callId, call, state, STATE_KIND));
                    if (toolCalls.length() > 0) toolCalls.append('\n');
                    toolCalls.append(call);
                    break;
                }
                default:
                    break;
            }
        }

        String content = text.toString().trim();
        if (toolCalls.length() > 0) {
            content = content.isEmpty() ? toolCalls.toString() : content + "\n" + toolCalls;
        }
        String reasoningText = reasoning.length() > 0 ? reasoning.toString() : null;
        if (content.isEmpty() && reasoningText != null) {
            content = reasoningText;
            reasoningText = null;
        }
        if (content.isEmpty()) {
            content = "No content returned.";
        }

        JSONObject usage = response.optJSONObject("usage");
        int tokens = 0;
        if (usage != null) {
            tokens = usage.optInt("total_tokens", usage.optInt("input_tokens", 0) + usage.optInt("output_tokens", 0));
        }
        return new Result(content, reasoningText, tokens, null, nativeCalls);
    }

    private static JSONObject extractResponse(String body) throws JSONException {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("{")) {
            JSONObject json = new JSONObject(trimmed);
            JSONObject nested = json.optJSONObject("response");
            return nested != null ? nested : json;
        }

        String streamError = null;
        for (String line : trimmed.split("\n")) {
            line = line.trim();
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            JSONObject event;
            try {
                event = new JSONObject(data);
            } catch (JSONException ignored) {
                continue;
            }
            String type = event.optString("type");
            if (("response.completed".equals(type) || "response.incomplete".equals(type) || "response.failed".equals(type))
                    && event.optJSONObject("response") != null) {
                return event.getJSONObject("response");
            }
            if ("error".equals(type)) {
                streamError = event.optString("message", data);
            }
        }
        if (streamError != null) {
            return new JSONObject().put("error", new JSONObject().put("message", streamError));
        }
        throw new JSONException("No terminal response event found in /responses stream");
    }
}
