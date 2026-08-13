package com.jasoncode.history;

import com.jasoncode.provider.ChatMessage;

import java.util.List;

/**
 * 对话历史存取抽象（F6）。
 * <p>
 * 一期提供内存实现；后续持久化只需新增实现，不改动调用方。
 */
public interface HistoryStore {

    /** 按序追加一条消息。 */
    void append(ChatMessage message);

    /** 返回不可变的历史快照，供请求构造。 */
    List<ChatMessage> snapshot();

    /** 移除最后一条消息（请求失败时回滚）；历史为空时静默返回。 */
    void removeLast();

    /** 清空全部历史。 */
    void clear();
}
