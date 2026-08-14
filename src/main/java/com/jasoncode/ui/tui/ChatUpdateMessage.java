package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * 通知 Model 重新渲染 viewport 的空信号消息（tui4j）。
 * <p>
 * 由后台轮询线程在检测到 ChatScreen 内容变化时通过 {@code program.send()} 发送。
 */
public record ChatUpdateMessage() implements Message {
}
