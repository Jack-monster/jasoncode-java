package com.jasoncode.chat.command;

/**
 * 会话内命令（如 /exit、/help）。新增命令只需实现本接口并注册，
 * 不改动对话循环逻辑（F3）。
 */
public interface ChatCommand {

    /** 命令名（不含前导 /），如 "exit"。 */
    String name();

    /** 用途描述，/help 展示用。 */
    String description();

    CommandResult execute(ChatContext ctx, String args);
}
