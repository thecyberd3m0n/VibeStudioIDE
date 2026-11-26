package com.vibestudio.app.chat.model;

import android.graphics.Bitmap;

public class ChatAttachment {

    public enum AttachmentType {
        PHOTO,
        CAMERA,
        FILE
    }

    private final String name;
    private final String mimeType;
    private final String base64Data;
    private final AttachmentType type;
    private final Bitmap thumbnail;

    public ChatAttachment(String name, String mimeType, String base64Data, AttachmentType type) {
        this(name, mimeType, base64Data, type, null);
    }

    public ChatAttachment(String name, String mimeType, String base64Data, AttachmentType type, Bitmap thumbnail) {
        this.name = name;
        this.mimeType = mimeType;
        this.base64Data = base64Data;
        this.type = type;
        this.thumbnail = thumbnail;
    }

    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getBase64Data() {
        return base64Data;
    }

    public AttachmentType getType() {
        return type;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }
}
