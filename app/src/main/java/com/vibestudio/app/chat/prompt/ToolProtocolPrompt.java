package com.vibestudio.app.chat.prompt;

import android.content.Context;

/** The part of the system prompt that depends on how the active model calls tools. */
public final class ToolProtocolPrompt {

    private ToolProtocolPrompt() {
    }

    /**
     * @param nativeTools the tools are declared through the provider's function-calling API
     * @param parallel    the model may return several tool calls per response (only meaningful with native tools)
     */
    public static String build(Context context, boolean nativeTools, boolean parallel) {
        if (!nativeTools) {
            return "\n" + PromptAssets.read(context, "tool_protocol_text.md");
        }
        return "\n" + PromptAssets.read(context, "tool_protocol_native.md")
                + PromptAssets.read(context, parallel ? "tool_protocol_native_parallel.md" : "tool_protocol_native_single.md");
    }
}
