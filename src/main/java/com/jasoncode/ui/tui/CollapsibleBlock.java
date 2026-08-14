package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

/**
 * 可折叠块（F5，可复用组件）：思考块、后续工具调用块共用。
 * <p>
 * 状态机：流式中（黄色"思考中..."，内容实时展开）→ 完成（标题切换、自动收起，
 * 鼠标点击标题行可展开/收起详情）。正文以左边界线缩进区呈现，与顶层正文形成空间层级。
 */
public final class CollapsibleBlock implements ScreenItem {

    private static final Style YELLOW_BOLD = Style.newStyle().foreground(Color.color("3")).bold(true);
    private static final Style DIM = Style.newStyle().foreground(Color.color("245"));

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
    public synchronized String render(int width) {
        StringBuilder sb = new StringBuilder();
        if (streaming) {
            sb.append(YELLOW_BOLD.render(INDENT + "● 思考中...")).append("\n");
        } else {
            sb.append(INDENT);
            sb.append(YELLOW_BOLD.render((expanded ? "▾" : "▸") + " " + doneTitle));
            sb.append(DIM.render("  （点击" + (expanded ? "收起" : "展开") + "）"));
            sb.append("\n");
        }
        if (streaming || expanded) {
            int bodyWidth = Math.max(2, width - TextWrap.width(INDENT + "│  "));
            for (String line : TextWrap.wrap(body.toString(), bodyWidth)) {
                sb.append(DIM.render(INDENT + "│  " + line)).append("\n");
            }
        }
        return sb.toString();
    }
}
