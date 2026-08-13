package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏 TUI 屏幕模型（F3/F5/F9）：线程安全地持有对话历史区内容与排队项，
 * 由流式事件线程写入、TUI 渲染线程读取。
 */
public final class ChatScreen {

    /** 一次渲染结果：行 + 每行的鼠标命中目标（仅折叠块标题行非 null）。 */
    public record Rendered(List<StyledLine> lines, List<CollapsibleBlock> hitTargets) {
        public CollapsibleBlock targetAt(int line) {
            return line >= 0 && line < hitTargets.size() ? hitTargets.get(line) : null;
        }
    }

    private final Object lock = new Object();
    private final List<ScreenItem> items = new ArrayList<>();
    private final List<ScreenItem.QueuedItem> queued = new ArrayList<>();
    private CollapsibleBlock currentThinking;
    private ScreenItem.AnswerBlock currentAnswer;

    public void addBanner(ScreenItem.BannerItem banner) {
        synchronized (lock) {
            items.add(banner);
        }
    }

    public void addUser(String text) {
        synchronized (lock) {
            items.add(new ScreenItem.UserItem(text));
        }
    }

    public void note(String text) {
        synchronized (lock) {
            items.add(new ScreenItem.NoteItem(text));
        }
    }

    public void error(String message) {
        synchronized (lock) {
            items.add(new ScreenItem.ErrorItem(message));
        }
    }

    /** 忙碌时提交进入队列（F9）：界面出现 queued 标记。 */
    public void enqueue(String text) {
        synchronized (lock) {
            queued.add(new ScreenItem.QueuedItem(text));
        }
    }

    /** 消费时移除全部 queued 标记并返回其文本（F9）。 */
    public List<String> consumeQueued() {
        synchronized (lock) {
            List<String> texts = queued.stream().map(ScreenItem.QueuedItem::text).toList();
            queued.clear();
            return texts;
        }
    }

    public int queueDepth() {
        synchronized (lock) {
            return queued.size();
        }
    }

    /** 新一轮回复开始：重置流式块引用。 */
    public void beginTurn() {
        synchronized (lock) {
            currentThinking = null;
            currentAnswer = null;
        }
    }

    public void thinkingDelta(String text) {
        synchronized (lock) {
            if (currentThinking == null) {
                currentThinking = new CollapsibleBlock("思考内容");
                items.add(currentThinking);
            }
            currentThinking.append(text);
        }
    }

    public void textDelta(String text) {
        synchronized (lock) {
            if (currentThinking != null) {
                currentThinking.finish(); // 思考完成：标题切换并自动收起（F5）
                currentThinking = null;
            }
            if (currentAnswer == null) {
                items.add(new ScreenItem.SpacerItem()); // 思考块与正文的空间分隔
                currentAnswer = new ScreenItem.AnswerBlock();
                items.add(currentAnswer);
            }
            currentAnswer.append(text);
        }
    }

    /** 本轮结束：收尾未完成的块，历史与正文间留空行。 */
    public void endTurn() {
        synchronized (lock) {
            if (currentThinking != null) {
                currentThinking.finish();
                currentThinking = null;
            }
            currentAnswer = null;
        }
    }

    /** 取出本轮正文（写入对话历史用；需在 endTurn 前调用）。 */
    public String takeTurnText() {
        synchronized (lock) {
            return currentAnswer == null ? "" : currentAnswer.text();
        }
    }

    /** 渲染全部内容（历史项 + 队尾排队项），并给出鼠标命中映射。 */
    public Rendered render(int width) {
        synchronized (lock) {
            List<StyledLine> lines = new ArrayList<>();
            List<CollapsibleBlock> targets = new ArrayList<>();
            for (ScreenItem item : items) {
                appendItem(item, width, lines, targets);
            }
            if (!queued.isEmpty()) {
                lines.add(StyledLine.empty());
                targets.add(null);
                for (ScreenItem.QueuedItem q : queued) {
                    appendItem(q, width, lines, targets);
                }
            }
            return new Rendered(lines, targets);
        }
    }

    private void appendItem(ScreenItem item, int width,
                            List<StyledLine> lines, List<CollapsibleBlock> targets) {
        List<StyledLine> rendered = item.render(width);
        boolean headerPending = item instanceof CollapsibleBlock;
        for (StyledLine line : rendered) {
            lines.add(line);
            targets.add(headerPending ? (CollapsibleBlock) item : null);
            headerPending = false; // 仅首行（标题行）可点击
        }
    }
}
