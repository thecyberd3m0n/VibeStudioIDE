package com.vibestudio.app.chat;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.chat.session.ChatSessionManager;
import com.vibestudio.app.fragments.ChatFragment;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ChatSessionTest {

    @Before
    public void setUp() {
        TabManager.getInstance().clearAll();
    }

    @Test
    public void testMultipleChatSessionsAreIndependent() {
        ChatSession session1 = ChatSessionManager.getInstance().createSession();
        ChatSession session2 = ChatSessionManager.getInstance().createSession();

        Assert.assertNotEquals(session1.getId(), session2.getId());

        ChatFragment fragment1 = ChatFragment.newInstance(session1);
        ChatFragment fragment2 = ChatFragment.newInstance(session2);

        TabItem tab1 = TabManager.getInstance().openTab(TabType.CHAT, fragment1);
        TabItem tab2 = TabManager.getInstance().openTab(TabType.CHAT, fragment2);

        Assert.assertNotEquals(tab1.getId(), tab2.getId());
        Assert.assertEquals("Chat #1", tab1.getTitle());
        Assert.assertEquals("Chat #2", tab2.getTitle());

        Assert.assertEquals(session1.getId(), fragment1.getChatSession().getId());
        Assert.assertEquals(session2.getId(), fragment2.getChatSession().getId());
    }

    @Test
    public void testSkillReferencedMessageStaysCleanAndSkillIsLoaded() {
        ChatSession session = ChatSessionManager.getInstance().createSession();

        session.addUserMessage(null, "/file edit index.html");

        Assert.assertFalse(session.getMessages().isEmpty());
        ChatMessage lastUserMsg = null;
        for (ChatMessage msg : session.getMessages()) {
            if (msg.isUser()) {
                lastUserMsg = msg;
            }
        }
        Assert.assertNotNull(lastUserMsg);
        Assert.assertEquals("/file edit index.html", lastUserMsg.getText());

        Assert.assertTrue(session.getMcpClientManager().isSkillLoaded("file"));
    }
}
