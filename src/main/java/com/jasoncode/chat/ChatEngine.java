package com.jasoncode.chat;

import com.jasoncode.history.HistoryStore;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ChatProvider;
import com.jasoncode.provider.StreamEvent;
import com.jasoncode.ui.tui.ChatScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步对话引擎（F9 输入队列）：工作线程消费提交的 prompt。
 * <p>
 * 空闲时直接受理；忙碌时新提交进入队列并在界面以 queued 标记展示；
 * 开始消费时把所有未消费的 queued 消息合并为一条（换行连接）发起请求。
 * 流式事件直接写入 {@link ChatScreen}，由 TUI 轮询重绘。
 */
public final class ChatEngine implements AutoCloseable {

    private final ChatProvider provider;
    private final HistoryStore history;
    private final ChatScreen screen;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean closed;
    private Thread worker;

    public ChatEngine(ChatProvider provider, HistoryStore history, ChatScreen screen) {
        this.provider = provider;
        this.history = history;
        this.screen = screen;
    }

    public void start() {
        worker = new Thread(this::loop, "jasoncode-engine");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isBusy() {
        return busy.get();
    }

    /** 空闲时直接受理：返回 true；忙碌返回 false（调用方转入排队路径）。 */
    public boolean tryDirectSubmit(String text) {
        if (busy.compareAndSet(false, true)) {
            queue.offer(text);
            return true;
        }
        return false;
    }

    /** 忙碌时提交入队（界面 queued 标记由调用方经 ChatScreen 展示）。 */
    public void enqueue(String text) {
        queue.offer(text);
    }

    private void loop() {
        while (!closed) {
            String first;
            try {
                first = queue.take();
            } catch (InterruptedException e) {
                if (closed) {
                    return;
                }
                continue;
            }
            busy.set(true);
            List<String> drained = new ArrayList<>();
            queue.drainTo(drained);
            screen.consumeQueued(); // 移除界面 queued 标记（F9）；合并文本以引擎队列为准
            List<String> all = new ArrayList<>();
            all.add(first);
            all.addAll(drained);
            runTurn(String.join("\n", all));
            busy.set(!queue.isEmpty());
        }
    }

    private void runTurn(String userInput) {
        screen.addUser(userInput);
        history.append(ChatMessage.user(userInput));
        screen.beginTurn();
        try {
            provider.streamChat(history.snapshot(), event -> {
                switch (event) {
                    case StreamEvent.ThinkingDelta d -> screen.thinkingDelta(d.text());
                    case StreamEvent.TextDelta d -> screen.textDelta(d.text());
                    case StreamEvent.Done ignored -> {
                    }
                    case StreamEvent.Error ignored -> {
                        // 错误经 streamChat 的异常路径统一处理
                    }
                }
            }).get();
            history.append(ChatMessage.assistant(screen.takeTurnText()));
            screen.endTurn();
        } catch (Exception e) {
            // 回滚本轮 user 消息：Anthropic 要求 user/assistant 严格交替
            history.removeLast();
            screen.endTurn();
            screen.error(rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void close() {
        closed = true;
        if (worker != null) {
            worker.interrupt();
        }
    }
}
