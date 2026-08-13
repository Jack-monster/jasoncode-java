package com.jasoncode.provider;

/**
 * 一条对话消息。
 */
public record ChatMessage(Role role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
