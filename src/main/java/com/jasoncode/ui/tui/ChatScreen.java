package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 全屏 TUI 屏幕模型（F3/F5/F9）：线程安全地持有对话历史区内容与排队项，
 * 由流式事件线程写入、TUI 渲染线程读取。
 */
public final class ChatScreen {

    /** 一次渲染结果：ANSI 样式内容字符串 + 每行（0-based 全局行号）对应的 CollapsibleBlock（仅标题行有值）。 */
    public record Rendered(String content, TreeMap<Integer, CollapsibleBlock> headerLines) {
        public CollapsibleBlock targetAt(int line) {
            return headerLines.get(line);
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
            StringBuilder content = new StringBuilder();
            TreeMap<Integer, CollapsibleBlock> headerLines = new TreeMap<>();
            int lineCount = 0;

            for (ScreenItem item : items) {
                if (item instanceof CollapsibleBlock cb) {
                    headerLines.put(lineCount, cb);
                }
                String rendered = item.render(width);
                content.append(rendered);
                lineCount += countLines(rendered);
            }

            if (!queued.isEmpty()) {
                content.append("\n");
                lineCount++;
                for (ScreenItem.QueuedItem q : queued) {
                    String rendered = q.render(width);
                    content.append(rendered);
                    lineCount += countLines(rendered);
                }
            }

            return new Rendered(content.toString(), headerLines);
        }
    }

    /** 计算字符串中的行数（以 \n 结尾的行计为一行；空字符串为 0 行）。 */
    private static int countLines(String s) {
        if (s.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                count++;
            }
        }
        // 如果最后一个字符不是 \n，则有一行没有结尾换行
        if (s.charAt(s.length() - 1) != '\n') {
            count++;
        }
        return count;
    }
}
