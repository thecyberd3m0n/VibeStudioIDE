package com.vibestudio.app.chat.provider;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.McpClientManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the chat history into provider-neutral items. With native tools, linked tool calls become one assistant item
 * holding all calls of a model reply, followed by one result item per call (the structure function-calling APIs
 * expect). Everything else, and all of the history without native tools, stays plain user/assistant text.
 */
final class NativeHistory {

    static final String NO_RESULT_TEXT = "No result was recorded for this call (the run was interrupted).";

    static final class Call {
        final String id;
        final String name;
        final JSONObject args;
        final String state;
        final String stateKind;
        final long plannedDelayMs;

        Call(String id, String name, JSONObject args, long plannedDelayMs, String state, String stateKind) {
            this.id = id;
            this.name = name;
            this.args = args;
            this.plannedDelayMs = plannedDelayMs;
            this.state = state;
            this.stateKind = stateKind;
        }

        /** The arguments as the model produced them: a requested delay goes back in as the declared argument. */
        JSONObject nativeArgs() {
            JSONObject copy;
            try {
                copy = new JSONObject(args.toString());
                if (plannedDelayMs > 0) {
                    copy.put(McpClientManager.PLANNED_DELAY_FIELD, plannedDelayMs);
                }
            } catch (Exception e) {
                return args;
            }
            return copy;
        }
    }

    static final class Item {
        enum Kind { USER, ASSISTANT, ASSISTANT_CALLS, TOOL_RESULT }

        final Kind kind;
        final String text;
        final List<Call> calls;
        final Call call;
        final boolean error;

        private Item(Kind kind, String text, List<Call> calls, Call call, boolean error) {
            this.kind = kind;
            this.text = text;
            this.calls = calls;
            this.call = call;
            this.error = error;
        }

        static Item text(boolean user, String text) {
            return new Item(user ? Kind.USER : Kind.ASSISTANT, text, null, null, false);
        }

        static Item calls(List<Call> calls) {
            return new Item(Kind.ASSISTANT_CALLS, null, calls, null, false);
        }

        static Item result(Call call, String text, boolean error) {
            return new Item(Kind.TOOL_RESULT, text, null, call, error);
        }
    }

    private NativeHistory() {
    }

    /**
     * @param nativeTools  project linked tool calls natively
     * @param requiredKind when non-null, a group of calls is only projected natively if every call carries state of
     *                     this kind (the provider can't replay calls it didn't produce); other groups stay text
     */
    static List<Item> project(List<ChatMessage> history, boolean nativeTools, String requiredKind) {
        List<Item> items = new ArrayList<>();
        if (history == null) return items;

        int i = 0;
        while (i < history.size()) {
            ChatMessage msg = history.get(i);
            if (msg == null || msg.getType() == ChatMessage.MessageType.REASONING) {
                i++;
                continue;
            }
            if (nativeTools && isLinkedCall(msg)) {
                int end = i;
                while (end < history.size() && isLinkedToolMessage(history.get(end))
                        && msg.getTurnId().equals(history.get(end).getTurnId())) {
                    end++;
                }
                if (appendNativeGroup(items, history.subList(i, end), requiredKind)) {
                    i = end;
                    continue;
                }
            }
            items.add(Item.text(msg.isUser(), msg.getText()));
            i++;
        }
        return items;
    }

    private static boolean isLinkedCall(ChatMessage msg) {
        return msg.getType() == ChatMessage.MessageType.TOOL_CALL && msg.getCallId() != null && msg.getTurnId() != null;
    }

    private static boolean isLinkedToolMessage(ChatMessage msg) {
        if (msg == null || msg.getCallId() == null || msg.getTurnId() == null) return false;
        return msg.getType() == ChatMessage.MessageType.TOOL_CALL
                || msg.getType() == ChatMessage.MessageType.TOOL_RESULT
                || msg.getType() == ChatMessage.MessageType.ERROR;
    }

    /** Returns false (adding nothing) when the group can't be projected natively. */
    private static boolean appendNativeGroup(List<Item> items, List<ChatMessage> group, String requiredKind) {
        Map<String, Call> calls = new LinkedHashMap<>();
        Map<String, ChatMessage> results = new LinkedHashMap<>();
        for (ChatMessage msg : group) {
            if (msg.getType() == ChatMessage.MessageType.TOOL_CALL) {
                Call call = toCall(msg);
                if (call == null) return false;
                if (requiredKind != null && (msg.getCallState() == null || !requiredKind.equals(msg.getCallStateKind()))) {
                    return false;
                }
                calls.put(msg.getCallId(), call);
            } else {
                results.put(msg.getCallId(), msg);
            }
        }
        if (calls.isEmpty()) return false;

        items.add(Item.calls(new ArrayList<>(calls.values())));
        for (Map.Entry<String, Call> entry : calls.entrySet()) {
            ChatMessage result = results.get(entry.getKey());
            String text = result != null && result.getText() != null ? result.getText() : NO_RESULT_TEXT;
            boolean error = result == null || result.getType() == ChatMessage.MessageType.ERROR;
            items.add(Item.result(entry.getValue(), text, error));
        }
        return true;
    }

    private static Call toCall(ChatMessage msg) {
        try {
            JSONObject json = new JSONObject(msg.getText());
            String name = json.optString("tool", msg.getToolName());
            if (name == null || name.isEmpty()) return null;
            JSONObject args = json.optJSONObject("args");
            return new Call(msg.getCallId(), name, args != null ? args : new JSONObject(),
                    Math.max(0L, json.optLong(McpClientManager.PLANNED_DELAY_FIELD, 0L)),
                    msg.getCallState(), msg.getCallStateKind());
        } catch (Exception e) {
            return null;
        }
    }
}
