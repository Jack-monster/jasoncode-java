package com.jasoncode.history;

import com.jasoncode.provider.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryHistoryStoreTest {

    private final InMemoryHistoryStore store = new InMemoryHistoryStore();

    @Test
    void appendKeepsOrder() {
        store.append(ChatMessage.user("q1"));
        store.append(ChatMessage.assistant("a1"));
        store.append(ChatMessage.user("q2"));

        List<ChatMessage> snapshot = store.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals("q1", snapshot.get(0).content());
        assertEquals("a1", snapshot.get(1).content());
        assertEquals("q2", snapshot.get(2).content());
    }

    @Test
    void snapshotIsImmutableCopy() {
        store.append(ChatMessage.user("q1"));
        List<ChatMessage> snapshot = store.snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(ChatMessage.user("injected")));
        // 快照后追加不影响已取出的快照
        store.append(ChatMessage.user("q2"));
        assertEquals(1, snapshot.size());
    }

    @Test
    void removeLastRemovesTailAndIsSafeWhenEmpty() {
        store.append(ChatMessage.user("q1"));
        store.append(ChatMessage.user("q2"));

        store.removeLast();
        assertEquals(1, store.snapshot().size());
        assertEquals("q1", store.snapshot().get(0).content());

        store.removeLast();
        store.removeLast(); // 空库调用不抛异常
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void clearEmptiesHistory() {
        store.append(ChatMessage.user("q1"));
        store.append(ChatMessage.assistant("a1"));

        store.clear();
        assertTrue(store.snapshot().isEmpty());
    }
}
