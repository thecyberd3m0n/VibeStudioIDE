package com.vibestudio.app.chat;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.fragments.ChatFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml")
public class ChatErrorViewTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testErrorMessageTypeAndUI() {
        ChatMessage errorMsg = new ChatMessage("Error", "HTTP 401: Unauthorized API key", false, ChatMessage.MessageType.ERROR);
        assertTrue(errorMsg.isError());
        assertEquals(ChatMessage.MessageType.ERROR, errorMsg.getType());

        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        ChatFragment fragment = new ChatFragment();
        activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment).commitNow();

        View root = fragment.getView();
        assertNotNull(root);

        fragment.onMessageAdded(errorMsg);

        // Retrieve mChatContainer inside ScrollView
        LinearLayout rootLayout = (LinearLayout) root;
        android.widget.ScrollView scrollView = (android.widget.ScrollView) rootLayout.getChildAt(0);
        LinearLayout chatContainer = (LinearLayout) scrollView.getChildAt(0);

        assertNotNull(chatContainer);
        assertTrue(chatContainer.getChildCount() > 0);

        View cardView = chatContainer.getChildAt(chatContainer.getChildCount() - 1);
        assertTrue(cardView instanceof LinearLayout);
        LinearLayout card = (LinearLayout) cardView;

        TextView tvSender = (TextView) card.getChildAt(0);
        TextView tvText = (TextView) card.getChildAt(1);

        assertEquals("Error", tvSender.getText().toString());
        assertEquals("HTTP 401: Unauthorized API key", tvText.getText().toString());
        assertEquals(Color.parseColor("#CF6679"), tvSender.getCurrentTextColor());
        assertEquals(Color.parseColor("#FFB4AB"), tvText.getCurrentTextColor());
    }
}
