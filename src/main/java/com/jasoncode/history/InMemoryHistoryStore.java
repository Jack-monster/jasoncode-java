package com.jasoncode.history;

import com.jasoncode.provider.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存历史存储：会话内保持，退出即丢失（一期行为）。
 */
public final class InMemoryHistoryStore implements HistoryStore {

    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    public synchronized void append(ChatMessage message) {
        messages.add(message);
    }

    @Override
    public synchronized List<ChatMessage> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    @Override
    public synchronized void removeLast() {
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
    }

    @Override
    public synchronized void clear() {
        messages.clear();
    }
}
