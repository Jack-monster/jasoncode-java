package com.jasoncode.ui;

import com.jasoncode.provider.StreamEvent;
import com.jasoncode.provider.StreamEventListener;

import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 流式渲染器：实现 StreamEventListener，把 provider 事件投入阻塞队列，
 * 由独立渲染线程消费并打印（网络线程不被终端输出阻塞，N1）。
 * <p>
 * 二期升级 TUI（spinner、整屏重绘）时只需替换本类的消费端。
 */
public final class StreamRenderer implements StreamEventListener, AutoCloseable {

    private static final Object POISON = new Object();

    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final PrintWriter out;
    private final AnsiColors colors;

    // 以下字段仅在渲染线程写入；跨线程读取通过 turnLatch 建立 happens-before
    private final StringBuilder turnText = new StringBuilder();
    private volatile CountDownLatch turnLatch;
    private Thread renderThread;
    private boolean thinkingBlockOpen;

    public StreamRenderer(PrintWriter out, AnsiColors colors) {
        this.out = out;
        this.colors = colors;
    }

    /** 启动渲染线程。 */
    public void start() {
        renderThread = new Thread(this::renderLoop, "jasoncode-renderer");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    /** 新一轮对话前调用：重置本轮累积文本、块状态与完成信号。 */
    public void beginTurn() {
        synchronized (turnText) {
            turnText.setLength(0);
        }
        thinkingBlockOpen = false;
        turnLatch = new CountDownLatch(1);
    }

    /** provider 侧回调：入队立即返回。 */
    @Override
    public void onEvent(StreamEvent event) {
        queue.add(event);
    }

    /** 等待当前流打印完毕（Done/Error 事件被渲染后返回）。 */
    public void awaitDrain() {
        CountDownLatch latch = turnLatch;
        if (latch == null) {
            return;
        }
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 取出本轮累积的 assistant 正文（需在 awaitDrain 之后调用）。 */
    public String takeTurnText() {
        synchronized (turnText) {
            return turnText.toString();
        }
    }

    /** 停止渲染线程。 */
    @Override
    public void close() {
        queue.add(POISON);
        if (renderThread != null) {
            try {
                renderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void renderLoop() {
        try {
            while (true) {
                Object item = queue.take();
                if (item == POISON) {
                    return;
                }
                render((StreamEvent) item);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void render(StreamEvent event) {
        switch (event) {
            case StreamEvent.ThinkingDelta delta -> {
                // 思考块（F5 分块展示）：首个思考片段前打印标题行，内容暗色输出
                if (!thinkingBlockOpen) {
                    thinkingBlockOpen = true;
                    out.println();
                    out.println(colors.dim("✦ Thinking"));
                }
                out.print(colors.dim(delta.text()));
                out.flush();
            }
            case StreamEvent.TextDelta delta -> {
                // 思考块结束后打印分隔线，正文另起一块（F5）
                if (thinkingBlockOpen) {
                    thinkingBlockOpen = false;
                    out.println();
                    out.println(colors.dim("── Answer ──────────────"));
                }
                synchronized (turnText) {
                    turnText.append(delta.text());
                }
                out.print(delta.text());
                out.flush();
            }
            case StreamEvent.Done ignored -> {
                out.println();
                out.flush();
                endTurn();
            }
            case StreamEvent.Error ignored -> {
                // 错误信息由对话循环统一展示，这里只结束本轮渲染
                out.println();
                out.flush();
                endTurn();
            }
        }
    }

    private void endTurn() {
        CountDownLatch latch = turnLatch;
        if (latch != null) {
            latch.countDown();
        }
    }
}
