package com.test.cutipdo2026;

import com.google.gson.annotations.SerializedName;

public class WahaMessageRequest {
    @SerializedName("chatId")
    private String chatId; // Format: 628998366182@c.us

    @SerializedName("text")
    private String text;

    @SerializedName("session")
    private String session = "default";

    public WahaMessageRequest(String chatId, String text) {
        this.chatId = chatId;
        this.text = text;
    }

    // Getters and Setters if needed
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }
}