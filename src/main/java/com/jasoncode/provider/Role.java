package com.jasoncode.provider;

/**
 * 对话消息角色。
 */
public enum Role {
    USER,
    ASSISTANT;

    /** 协议请求体中的小写表示（openai / anthropic 一致）。 */
    public String wire() {
        return name().toLowerCase();
    }
}
