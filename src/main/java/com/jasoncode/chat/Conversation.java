package com.jasoncode.chat;

import com.jasoncode.history.HistoryStore;

/**
 * 单个会话对象：持有对话历史。
 * <p>
 * 对话循环操作的是"会话"而非全局历史——后续会话切换只需替换
 * 会话的来源与管理，循环与渲染逻辑不变。
 */
public final class Conversation {

    private final HistoryStore history;

    public Conversation(HistoryStore history) {
        this.history = history;
    }

    public HistoryStore history() {
        return history;
    }
}
