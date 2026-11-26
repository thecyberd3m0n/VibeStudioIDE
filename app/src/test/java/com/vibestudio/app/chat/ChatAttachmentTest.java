package com.vibestudio.app.chat;

import com.vibestudio.app.chat.model.ChatAttachment;
import com.vibestudio.app.chat.model.ChatMessage;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ChatAttachmentTest {

    @Test
    public void testChatAttachmentCreation() {
        ChatAttachment attachment = new ChatAttachment("test.png", "image/png", "base64encodeddata", ChatAttachment.AttachmentType.PHOTO);
        assertEquals("test.png", attachment.getName());
        assertEquals("image/png", attachment.getMimeType());
        assertEquals("base64encodeddata", attachment.getBase64Data());
        assertEquals(ChatAttachment.AttachmentType.PHOTO, attachment.getType());
        assertNull(attachment.getThumbnail());
    }

    @Test
    public void testChatMessageWithAttachment() {
        ChatAttachment attachment = new ChatAttachment("sample.txt", "text/plain", "dGVzdA==", ChatAttachment.AttachmentType.FILE);
        ChatMessage message = new ChatMessage("User", "Here is a file", true, Collections.singletonList(attachment));
        
        assertTrue(message.hasAttachments());
        assertEquals(1, message.getAttachments().size());
        assertEquals("sample.txt", message.getAttachments().get(0).getName());
    }
}
