package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 可折叠块（F5，可复用组件）：思考块、后续工具调用块共用。
 * <p>
 * 状态机：流式中（黄色"思考中..."，内容实时展开）→ 完成（标题切换、自动收起，
 * 鼠标点击标题行可展开/收起详情）。正文以左边界线缩进区呈现，与顶层正文形成空间层级。
 */
public final class CollapsibleBlock implements ScreenItem {

    private final String doneTitle;
    private final StringBuilder body = new StringBuilder();
    private boolean streaming = true;
    private boolean expanded = true;

    public CollapsibleBlock(String doneTitle) {
        this.doneTitle = doneTitle;
    }

    public synchronized void append(String text) {
        body.append(text);
    }

    /** 流式结束：标题切换并自动收起（F5）。 */
    public synchronized void finish() {
        streaming = false;
        expanded = false;
    }

    /** 点击标题行切换展开/收起；仅完成态可切换。 */
    public synchronized void toggle() {
        if (!streaming) {
            expanded = !expanded;
        }
    }

    public synchronized boolean isExpanded() {
        return expanded;
    }

    public synchronized boolean isStreaming() {
        return streaming;
    }

    @Override
    public synchronized List<StyledLine> render(int width) {
        List<StyledLine> lines = new ArrayList<>();
        if (streaming) {
            lines.add(StyledLine.styled(INDENT + "● 思考中...", Style.YELLOW_BOLD));
        } else {
            lines.add(new StyledLine(List.of(
                    new StyledSpan(INDENT, Style.DEFAULT),
                    new StyledSpan((expanded ? "▾" : "▸") + " " + doneTitle, Style.YELLOW_BOLD),
                    new StyledSpan("  （点击" + (expanded ? "收起" : "展开") + "）", Style.DIM))));
        }
        if (streaming || expanded) {
            int bodyWidth = Math.max(2, width - TextWrap.width(INDENT + "│  "));
            for (String line : TextWrap.wrap(body.toString(), bodyWidth)) {
                lines.add(StyledLine.styled(INDENT + "│  " + line, Style.DIM));
            }
        }
        return lines;
    }
}
