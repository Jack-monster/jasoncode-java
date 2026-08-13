package com.jasoncode.chat.command;

import com.jasoncode.chat.ChatUi;

/**
 * 命令执行上下文：命令可访问的运行时对象。
 * <p>
 * 后续会话管理（如 /sessions）扩展时在此追加字段，命令接口不变。
 */
public record ChatContext(ChatUi ui) {
}
