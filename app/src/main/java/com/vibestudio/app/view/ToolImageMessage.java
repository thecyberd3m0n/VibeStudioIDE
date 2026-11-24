package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vibestudio.app.chat.model.ChatAttachment;
import com.vibestudio.app.chat.model.ChatMessage;

/** Chat view for images returned by a tool (e.g. a screenshot): a tool card with a tappable preview. */
public class ToolImageMessage extends LinearLayout {

    public ToolImageMessage(Context context, ChatMessage msg, java.util.function.Consumer<ChatAttachment> onOpen) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#1E1E24"));
        setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        setLayoutParams(params);

        TextView title = new TextView(context);
        title.setText("🖼 " + msg.getText());
        title.setTextColor(Color.parseColor("#BB86FC"));
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        LinearLayout row = new LinearLayout(context);
        scroll.addView(row);
        for (ChatAttachment att : msg.getAttachments()) {
            if (att.getThumbnail() == null) continue;
            ImageView img = new ImageView(context);
            img.setImageBitmap(att.getThumbnail());
            img.setAdjustViewBounds(true);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams ip = new LayoutParams((int) (160 * d), (int) (240 * d));
            ip.setMargins(0, (int) (6 * d), (int) (8 * d), 0);
            ip.gravity = Gravity.START;
            img.setLayoutParams(ip);
            img.setOnClickListener(v -> onOpen.accept(att));
            row.addView(img);
        }
        addView(scroll);
    }
}
