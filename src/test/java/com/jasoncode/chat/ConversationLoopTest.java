package com.jasoncode.chat;

import com.jasoncode.chat.command.CommandRegistry;
import com.jasoncode.history.InMemoryHistoryStore;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ChatProvider;
import com.jasoncode.provider.ProviderException;
import com.jasoncode.provider.Role;
import com.jasoncode.provider.StreamEvent;
import com.jasoncode.provider.StreamEventListener;
import com.jasoncode.ui.AnsiColors;
import com.jasoncode.ui.StreamRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationLoopTest {

    private StreamRenderer renderer;
    private StringWriter output;

    @BeforeEach
    void setUp() {
        output = new StringWriter();
        renderer = new StreamRenderer(new PrintWriter(output), new AnsiColors(false));
        renderer.start();
    }

    @AfterEach
    void tearDown() {
        renderer.close();
    }

    /** 预置输入序列的假 UI。 */
    private static final class FakeUi implements ChatUi {
        final Queue<String> inputs = new ArrayDeque<>();
        final List<String> errors = new ArrayList<>();
        final List<String> lines = new ArrayList<>();

        @Override
        public String readLine() {
            return inputs.poll(); // 队列耗尽返回 null → 退出
        }

        @Override
        public void showError(String message) {
            errors.add(message);
        }

        @Override
        public void showWarning(String message) {
        }

        @Override
        public void println(String message) {
            lines.add(message);
        }
    }

    /** 按轮次返回预置回复或失败的假 Provider。 */
    private static final class FakeProvider implements ChatProvider {
        final Queue<Object> turns = new ArrayDeque<>(); // String=回复文本，Exception=失败
        int callCount;

        @Override
        public CompletableFuture<Void> streamChat(List<ChatMessage> history, StreamEventListener listener) {
            callCount++;
            Object turn = turns.poll();
            if (turn instanceof Exception e) {
                listener.onEvent(new StreamEvent.Error(
                        new ProviderException(ProviderException.Category.API, e.getMessage())));
                return CompletableFuture.failedFuture(e);
            }
            String text = turn == null ? "" : turn.toString();
            listener.onEvent(new StreamEvent.TextDelta(text));
            listener.onEvent(new StreamEvent.Done());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    private ConversationLoop loop(FakeUi ui, FakeProvider provider, InMemoryHistoryStore history) {
        return new ConversationLoop(ui, renderer, provider,
                new Conversation(history), CommandRegistry.defaults());
    }

    @Test
    void twoTurnsProduceAlternatingHistory() {
        FakeUi ui = new FakeUi();
        ui.inputs.add("我叫 Jason");
        ui.inputs.add("我叫什么？");
        FakeProvider provider = new FakeProvider();
        provider.turns.add("你好 Jason");
        provider.turns.add("你叫 Jason");
        InMemoryHistoryStore history = new InMemoryHistoryStore();

        loop(ui, provider, history).run();

        List<ChatMessage> messages = history.snapshot();
        assertEquals(4, messages.size());
        assertEquals(Role.USER, messages.get(0).role());
        assertEquals("我叫 Jason", messages.get(0).content());
        assertEquals(Role.ASSISTANT, messages.get(1).role());
        assertEquals("你好 Jason", messages.get(1).content());
        assertEquals(Role.USER, messages.get(2).role());
        assertEquals(Role.ASSISTANT, messages.get(3).role());
        assertEquals("你叫 Jason", messages.get(3).content());
        assertEquals(2, provider.callCount);
        assertTrue(output.toString().contains("你好 Jason"), "流式文本应被渲染");
    }

    @Test
    void failedTurnRollsBackUserMessageAndContinues() {
        FakeUi ui = new FakeUi();
        ui.inputs.add("会失败的请求");
        ui.inputs.add("会成功的请求");
        FakeProvider provider = new FakeProvider();
        provider.turns.add(new RuntimeException("模拟 API 错误"));
        provider.turns.add("成功了");
        InMemoryHistoryStore history = new InMemoryHistoryStore();

        loop(ui, provider, history).run();

        assertEquals(1, ui.errors.size(), "失败轮应打印错误且不退出");
        assertTrue(ui.errors.get(0).contains("模拟 API 错误"));
        List<ChatMessage> messages = history.snapshot();
        assertEquals(2, messages.size(), "失败的 user 消息应被回滚");
        assertEquals("会成功的请求", messages.get(0).content());
        assertEquals("成功了", messages.get(1).content());
    }

    @Test
    void exitCommandStopsLoopWithoutCallingProvider() {
        FakeUi ui = new FakeUi();
        ui.inputs.add("/exit");
        ui.inputs.add("不该被执行");
        FakeProvider provider = new FakeProvider();
        InMemoryHistoryStore history = new InMemoryHistoryStore();

        loop(ui, provider, history).run();

        assertEquals(0, provider.callCount);
        assertTrue(history.snapshot().isEmpty());
    }

    @Test
    void blankInputIsIgnored() {
        FakeUi ui = new FakeUi();
        ui.inputs.add("   ");
        ui.inputs.add("");
        ui.inputs.add("hello");
        FakeProvider provider = new FakeProvider();
        provider.turns.add("hi");
        InMemoryHistoryStore history = new InMemoryHistoryStore();

        loop(ui, provider, history).run();

        assertEquals(1, provider.callCount, "空输入不应触发请求");
        assertEquals(2, history.snapshot().size());
    }

    @Test
    void unknownCommandShowsErrorAndContinues() {
        FakeUi ui = new FakeUi();
        ui.inputs.add("/nosuchcmd");
        FakeProvider provider = new FakeProvider();
        InMemoryHistoryStore history = new InMemoryHistoryStore();

        loop(ui, provider, history).run();

        assertEquals(1, ui.errors.size());
        assertTrue(ui.errors.get(0).contains("/nosuchcmd"));
        assertEquals(0, provider.callCount);
    }
}
