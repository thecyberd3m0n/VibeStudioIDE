package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.provider.AbstractAiProvider;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.view.BaseToolMessageView;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Code example on how to add a custom MCP Server to VibeStudio IDE.
 * This test defines and registers "TestMCPServer", which controls a light switcher
 * with only 'turn_on' and 'turn_off' methods / tools.
 * It also tests the full LLM flow where system instructions contain the tool prompt
 * and a mocked LLM responds with tool calls to switch on and off the lights.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {com.vibestudio.app.test.ShadowDatabaseHelper.class})
public class TestMCPServerTest {

    /**
     * Example custom MCP Server controlling a light switcher.
     */
    public static class TestMCPServer implements McpServer {
        private static final String NAME = "light_switcher";
        private static final String DESCRIPTION = "Controls light switcher with on and off capabilities.";

        public static final String TOOL_TURN_ON = "light_turn_on";
        public static final String TOOL_TURN_OFF = "light_turn_off";

        private boolean mIsActive = false;
        private boolean mIsLightOn = false;
        private final List<McpTool> mTools;

        public TestMCPServer() {
            mTools = new ArrayList<>();

            // Tool 1: Turn Light On
            mTools.add(McpTool.builder(TOOL_TURN_ON, "Turn on the light.")
                    .build());

            // Tool 2: Turn Light Off
            mTools.add(McpTool.builder(TOOL_TURN_OFF, "Turn off the light.")
                    .build());
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public String getDescription() {
            return DESCRIPTION;
        }

        @Override
        public void startServer() {
            mIsActive = true;
        }

        @Override
        public void stopServer() {
            mIsActive = false;
        }

        @Override
        public boolean isActive() {
            return mIsActive;
        }

        @Override
        public List<McpTool> getTools() {
            return mTools;
        }

        @Override
        public boolean handlesTool(String toolName) {
            if (toolName == null) return false;
            return TOOL_TURN_ON.equalsIgnoreCase(toolName) || TOOL_TURN_OFF.equalsIgnoreCase(toolName);
        }

        @Override
        public McpToolResult callTool(String toolName, Map<String, Object> arguments, Context context) {
            if (!mIsActive) {
                return McpToolResult.error("Light switcher MCP server is not active.");
            }

            if (TOOL_TURN_ON.equalsIgnoreCase(toolName)) {
                mIsLightOn = true;
                return McpToolResult.success("Light is now ON.");
            } else if (TOOL_TURN_OFF.equalsIgnoreCase(toolName)) {
                mIsLightOn = false;
                return McpToolResult.success("Light is now OFF.");
            }

            return McpToolResult.error("Unknown tool: " + toolName);
        }

        @Override
        public BaseToolMessageView createToolMessageView(Context context, String toolName) {
            return null;
        }

        public CharSequence formatToolCall(String toolName, JSONObject args) {
            if (TOOL_TURN_ON.equalsIgnoreCase(toolName)) {
                return "Switching light ON";
            } else if (TOOL_TURN_OFF.equalsIgnoreCase(toolName)) {
                return "Switching light OFF";
            }
            return toolName;
        }

        public boolean isLightOn() {
            return mIsLightOn;
        }
    }

    /**
     * Mock AI Provider that verifies system instructions contain the registered MCP server's details
     * and responds with tool calls or conversational replies.
     */
    private static class MockLightLlmProvider extends AbstractAiProvider {
        private String mLastReceivedSystemInstruction;

        @Override
        public String getName() {
            return "MockLightLLM";
        }

        @Override
        public String getDefaultModel() {
            return "mock-light-model";
        }

        @Override
        public List<String> fetchAvailableModelsSync(String apiKey) {
            return Arrays.asList("mock-light-model");
        }

        @Override
        public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history) {
            this.mLastReceivedSystemInstruction = systemInstruction;

            if (history == null || history.isEmpty()) {
                return AiResponse.success("Hello!");
            }

            ChatMessage lastMessage = history.get(history.size() - 1);
            String userText = lastMessage.getText() != null ? lastMessage.getText() : "";

            if (userText.contains("switch on") || userText.contains("turn on")) {
                // LLM response requesting tool call to turn on the light
                String toolCallResponse = "{\n" +
                        "  \"planned_delay_ms\": 0,\n" +
                        "  \"tool\": \"light_turn_on\",\n" +
                        "  \"args\": {}\n" +
                        "}";
                return AiResponse.success(toolCallResponse);
            } else if (userText.contains("switch off") || userText.contains("turn off")) {
                // LLM response requesting tool call to turn off the light
                String toolCallResponse = "{\n" +
                        "  \"planned_delay_ms\": 0,\n" +
                        "  \"tool\": \"light_turn_off\",\n" +
                        "  \"args\": {}\n" +
                        "}";
                return AiResponse.success(toolCallResponse);
            }

            return AiResponse.success("I can control your lights. Ask me to turn on or turn off the lights.");
        }

        public String getLastReceivedSystemInstruction() {
            return mLastReceivedSystemInstruction;
        }
    }

    private McpClientManager mMcpClientManager;
    private TestMCPServer mTestMCPServer;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mMcpClientManager = new McpClientManager();
        mTestMCPServer = new TestMCPServer();
        mMcpClientManager.registerServer(mTestMCPServer);
    }

    @Test
    public void testServerRegistrationAndMetadata() {
        Assert.assertTrue(mTestMCPServer.isActive());
        Assert.assertEquals("light_switcher", mTestMCPServer.getName());
        Assert.assertEquals(2, mTestMCPServer.getTools().size());

        McpServer foundServer = mMcpClientManager.findServerForTool("light_turn_on");
        Assert.assertNotNull(foundServer);
        Assert.assertEquals("light_switcher", foundServer.getName());

        McpServer foundServerOff = mMcpClientManager.findServerForTool("light_turn_off");
        Assert.assertNotNull(foundServerOff);
        Assert.assertEquals("light_switcher", foundServerOff.getName());
    }

    @Test
    public void testLightSwitcherOnAndOff() {
        Assert.assertFalse(mTestMCPServer.isLightOn());

        // Directly via server
        McpToolResult turnOnDirect = mTestMCPServer.callTool("light_turn_on", new HashMap<String, Object>(), mContext);
        Assert.assertTrue(turnOnDirect.isSuccess());
        Assert.assertEquals("Light is now ON.", turnOnDirect.getOutput());
        Assert.assertTrue(mTestMCPServer.isLightOn());

        McpToolResult turnOffDirect = mTestMCPServer.callTool("light_turn_off", new HashMap<String, Object>(), mContext);
        Assert.assertTrue(turnOffDirect.isSuccess());
        Assert.assertEquals("Light is now OFF.", turnOffDirect.getOutput());
        Assert.assertFalse(mTestMCPServer.isLightOn());

        // Via McpClientManager
        JSONObject onResponse = mMcpClientManager.executeToolCall("light_turn_on", new JSONObject(), mContext);
        Assert.assertEquals("success", onResponse.optString("status"));
        Assert.assertEquals("Light is now ON.", onResponse.optString("output"));
        Assert.assertTrue(mTestMCPServer.isLightOn());

        JSONObject offResponse = mMcpClientManager.executeToolCall("light_turn_off", new JSONObject(), mContext);
        Assert.assertEquals("success", offResponse.optString("status"));
        Assert.assertEquals("Light is now OFF.", offResponse.optString("output"));
        Assert.assertFalse(mTestMCPServer.isLightOn());
    }

    @Test
    public void testInactiveServerReturnsError() {
        mTestMCPServer.stopServer();
        Assert.assertFalse(mTestMCPServer.isActive());

        JSONObject response = mMcpClientManager.executeToolCall("light_turn_on", new JSONObject(), mContext);
        Assert.assertEquals("error", response.optString("status"));
        Assert.assertEquals("Light switcher MCP server is not active.", response.optString("message"));
    }

    @Test
    public void testLlmFlowReceivesToolPromptAndSwitchesOnLight() throws InterruptedException {
        // Save dummy API key so ChatSession proceeds to LLM provider
        DatabaseHelper dbHelper = new DatabaseHelper(mContext);
        dbHelper.saveApiKey("MockLightLLM", "mock-api-key");

        // Setup ChatSession with registered TestMCPServer and Mock LLM Provider
        ChatSession chatSession = new ChatSession();
        chatSession.getMcpClientManager().registerServer(mTestMCPServer);

        MockLightLlmProvider mockLlm = new MockLightLlmProvider();
        AiRouter aiRouter = AiRouter.getInstance();
        aiRouter.registerProvider(mockLlm);
        aiRouter.saveActiveSelection(mContext, "MockLightLLM", "mock-light-model");

        Assert.assertFalse(mTestMCPServer.isLightOn());

        // User requests switching on the lights
        chatSession.sendMessage(mContext, "Please switch on the lights");

        // Wait for background worker thread and drain main looper runnables
        Thread.sleep(500);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // 1. Verify LLM received tool prompts in system instruction catalog
        String receivedSysInstruction = mockLlm.getLastReceivedSystemInstruction();
        Assert.assertNotNull(receivedSysInstruction);
        Assert.assertTrue(receivedSysInstruction.contains("light_switcher"));

        // 2. Verify light switcher state was mutated to ON by mock LLM's tool call
        Assert.assertTrue(mTestMCPServer.isLightOn());

        // 3. User requests switching off the lights
        chatSession.sendMessage(mContext, "Please switch off the lights");
        Thread.sleep(500);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        Assert.assertFalse(mTestMCPServer.isLightOn());
    }
}
