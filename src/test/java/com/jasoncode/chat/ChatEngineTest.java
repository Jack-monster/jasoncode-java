package com.jasoncode.chat;

import com.jasoncode.history.InMemoryHistoryStore;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ChatProvider;
import com.jasoncode.provider.Role;
import com.jasoncode.provider.StreamEventListener;
import com.jasoncode.ui.tui.ChatScreen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ChatEngine 异步引擎测试（F9）：忙碌时排队、消费时合并全部未消费 prompt、
 * 失败回滚 user 消息保持角色交替。
 */
class ChatEngineTest {

    /** 受控 Provider：每轮请求挂起，直到测试侧手动 complete。 */
    static final class FakeProvider implements ChatProvider {
        final List<List<ChatMessage>> requests = new CopyOnWriteArrayList<>();
        final List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<Void> streamChat(List<ChatMessage> history,
                                                  StreamEventListener listener) {
            requests.add(List.copyOf(history));
            CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    private static void waitFor(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("等待条件超时");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("等待被中断");
            }
        }
    }

    @Test
    void queuedPromptsMergeIntoSingleRequestOnConsume() {
        FakeProvider provider = new FakeProvider();
        InMemoryHistoryStore history = new InMemoryHistoryStore();
        ChatScreen screen = new ChatScreen();
        try (ChatEngine engine = new ChatEngine(provider, history, screen)) {
            engine.start();
            assertTrue(engine.tryDirectSubmit("t1")); // 空闲：直接受理
            waitFor(() -> provider.futures.size() == 1);
            assertTrue(engine.isBusy());

            // 忙碌中再提交两条：直接受理失败，转入排队（界面 queued 标记由 TUI 写入）
            assertFalse(engine.tryDirectSubmit("t2"));
            screen.enqueue("t2");
            engine.enqueue("t2");
            screen.enqueue("t3");
            engine.enqueue("t3");
            assertEquals(2, screen.queueDepth());

            provider.futures.get(0).complete(null); // 第一轮结束，开始消费队列
            waitFor(() -> provider.futures.size() == 2);

            assertEquals(0, screen.queueDepth()); // 消费开始即移除 queued 标记
            List<ChatMessage> second = provider.requests.get(1);
            ChatMessage merged = second.get(second.size() - 1);
            assertEquals(Role.USER, merged.role());
            assertEquals("t2\nt3", merged.content()); // 全部未消费消息合并为一条
        }
    }

    @Test
    void failedTurnRollsBackUserMessage() {
        FakeProvider provider = new FakeProvider();
        InMemoryHistoryStore history = new InMemoryHistoryStore();
        ChatScreen screen = new ChatScreen();
        try (ChatEngine engine = new ChatEngine(provider, history, screen)) {
            engine.start();
            assertTrue(engine.tryDirectSubmit("你好"));
            waitFor(() -> provider.futures.size() == 1);
            provider.futures.get(0).completeExceptionally(new RuntimeException("boom"));
            waitFor(() -> history.snapshot().isEmpty()); // user 消息已回滚（保持角色交替）
            // 错误进入历史区展示
            boolean shown = screen.render(80).lines().stream()
                    .anyMatch(line -> line.plain().contains("boom"));
            assertTrue(shown);
        }
    }
}
