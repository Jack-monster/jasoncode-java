package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏 TUI 对话历史区的内容项（F3/F5/F9）。
 * <p>
 * 每项自行完成宽度折行与左侧留白；块之间通过空间结构（缩进、边界线区、空行）区分。
 * render(int width) 返回 ANSI 样式字符串（可含多行 \n）。
 */
public sealed interface ScreenItem
        permits CollapsibleBlock, ScreenItem.BannerItem, ScreenItem.UserItem,
        ScreenItem.AnswerBlock, ScreenItem.SpacerItem, ScreenItem.QueuedItem,
        ScreenItem.ErrorItem, ScreenItem.NoteItem {

    /** 全局左侧留白。 */
    String INDENT = "  ";

    /** 渲染为 ANSI 样式字符串（可含多行 \n）。 */
    String render(int width);

    // ── lipgloss 样式常量 ──
    Style CYAN_BOLD = Style.newStyle().foreground(Color.color("6")).bold(true);
    Style CYAN = Style.newStyle().foreground(Color.color("6"));
    Style MAGENTA = Style.newStyle().foreground(Color.color("5"));
    Style YELLOW_BOLD = Style.newStyle().foreground(Color.color("3")).bold(true);
    Style RED_BOLD = Style.newStyle().foreground(Color.color("1")).bold(true);
    Style GREEN_BOLD = Style.newStyle().foreground(Color.color("2")).bold(true);
    Style GREEN = Style.newStyle().foreground(Color.color("2"));
    Style DIM = Style.newStyle().foreground(Color.color("245"));


    /** 启动 Banner：图案 + 程序信息区。 */
    record BannerItem(String art, String version, String providerDescription) implements ScreenItem {
        @Override
        public String render(int width) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            for (String line : art.stripTrailing().split("\n", -1)) {
                sb.append(MAGENTA.render(clip(line, width))).append("\n");
            }
            sb.append("\n");
            sb.append(INDENT).append(CYAN_BOLD.render("JasonCode")).append("  ").append(DIM.render("v" + version)).append("\n");
            sb.append(DIM.render(INDENT + "终端 AI 助手 · 一期工程（流式对话）")).append("\n");
            sb.append(INDENT).append("供应商：").append(CYAN.render(providerDescription)).append("\n");
            sb.append(DIM.render(INDENT + "点击输入框或直接按键开始输入；点击思考块或 Ctrl+T 展开/收起")).append("\n");
            sb.append("\n");
            return sb.toString();
        }

        /** 按显示宽度裁剪单行（CJK/Emoji 双列），不补空格。 */
        private static String clip(String line, int width) {
            if (TextWrap.width(line) <= width) {
                return line;
            }
            StringBuilder sb = new StringBuilder();
            int w = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                int cw = TextWrap.charWidth(cp);
                if (w + cw > width) {
                    break;
                }
                sb.appendCodePoint(cp);
                w += cw;
                i += Character.charCount(cp);
            }
            return sb.toString();
        }
    }

    /** 用户消息：彩色前缀 + 左边框竖线，无背景色。 */
    record UserItem(String text) implements ScreenItem {
        @Override
        public String render(int width) {
            String prefix = INDENT + "┃ You ❯ ";
            int prefixWidth = TextWrap.width(prefix);
            List<String> wrapped = TextWrap.wrap(text, Math.max(2, width - prefixWidth));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wrapped.size(); i++) {
                if (i == 0) {
                    sb.append(INDENT);
                    sb.append(CYAN.render("┃ "));
                    sb.append(CYAN_BOLD.render("You"));
                    sb.append(CYAN.render(" ❯ "));
                } else {
                    sb.append(INDENT).append(CYAN.render("┃ ")).append(" ".repeat(prefixWidth - TextWrap.width(INDENT) - 2));
                }
                sb.append(wrapped.get(i));
                sb.append("\n");
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    /**
     * 回答正文块：绿色 ┃ 左边框区分 AI 消息。
     * 空行不画 ┃，边框在段落间断开；消息间靠空行自然分隔。
     */
    final class AnswerBlock implements ScreenItem {
        private final StringBuilder body = new StringBuilder();
        private String cachedString = "";
        private String cachedText = "";
        private int cachedWidth = -1;

        public synchronized void append(String text) {
            body.append(text);
        }

        public synchronized String text() {
            return body.toString();
        }

        @Override
        public synchronized String render(int width) {
            String text = body.toString();
            if (cachedWidth == width && cachedText.equals(text)) {
                return cachedString;
            }
            int contentWidth = Math.max(2, width - TextWrap.width(INDENT + "┃ "));
            String md = MarkdownRenderer.render(text, contentWidth);
            StringBuilder sb = new StringBuilder();
            for (String line : md.split("\n", -1)) {
                if (line.isEmpty()) {
                    // 消息内部空行保留 ┃，保持边框连续
                    sb.append(INDENT).append(GREEN.render("┃")).append("\n");
                } else {
                    sb.append(INDENT).append(GREEN.render("┃ ")).append(line).append("\n");
                }
            }
            // 空内容时至少显示边框
            if (sb.length() == 0) {
                sb.append(INDENT).append(GREEN.render("┃")).append("\n");
            }
            // 消息间分隔：尾部空行不画 ┃，与下一条消息断开
            sb.append("\n");
            String result = sb.toString();
            cachedString = result;
            cachedText = text;
            cachedWidth = width;
            return result;
        }
    }

    /** 空行分隔项：块之间的空间留白。 */
    record SpacerItem() implements ScreenItem {
        @Override
        public String render(int width) {
            return "\n";
        }
    }

    /** 排队中的 prompt（F9）：带 queued 标记，消费时由 ChatScreen 移除。 */
    record QueuedItem(String text) implements ScreenItem {
        @Override
        public String render(int width) {
            return YELLOW_BOLD.render(INDENT + "⧗ queued: " + text) + "\n";
        }
    }

    /** 错误信息（F8）。 */
    record ErrorItem(String message) implements ScreenItem {
        @Override
        public String render(int width) {
            StringBuilder sb = new StringBuilder();
            for (String line : TextWrap.wrap("✗ " + message, Math.max(2, width - TextWrap.width(INDENT)))) {
                sb.append(RED_BOLD.render(INDENT + line)).append("\n");
            }
            return sb.toString();
        }
    }

    /** 普通提示/命令输出。 */
    record NoteItem(String text) implements ScreenItem {
        @Override
        public String render(int width) {
            StringBuilder sb = new StringBuilder();
            for (String line : TextWrap.wrap(text, Math.max(2, width - TextWrap.width(INDENT)))) {
                sb.append(DIM.render(INDENT + line)).append("\n");
            }
            return sb.toString();
        }
    }
}
