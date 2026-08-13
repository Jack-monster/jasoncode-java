package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏 TUI 对话历史区的内容项（F3/F5/F9）。
 * <p>
 * 每项自行完成宽度折行与左侧留白；块之间通过空间结构（缩进、边界线区、空行）区分。
 */
public sealed interface ScreenItem
        permits CollapsibleBlock, ScreenItem.BannerItem, ScreenItem.UserItem,
        ScreenItem.AnswerBlock, ScreenItem.SpacerItem, ScreenItem.QueuedItem,
        ScreenItem.ErrorItem, ScreenItem.NoteItem {

    /** 全局左侧留白。 */
    String INDENT = "  ";

    /** 渲染为若干行（含留白与换行处理）。 */
    List<StyledLine> render(int width);

    /** 启动 Banner：图案 + 程序信息区。 */
    record BannerItem(String art, String version, String providerDescription) implements ScreenItem {
        @Override
        public List<StyledLine> render(int width) {
            List<StyledLine> lines = new ArrayList<>();
            lines.add(StyledLine.empty());
            for (String line : art.stripTrailing().split("\n", -1)) {
                // 图案按宽度裁剪：超宽行交给终端硬换行会在尺寸变化时造成重绘错乱
                lines.add(StyledLine.styled(clip(line, width), Style.MAGENTA));
            }
            lines.add(StyledLine.empty());
            lines.add(new StyledLine(List.of(
                    new StyledSpan(INDENT, Style.DEFAULT),
                    new StyledSpan("JasonCode", Style.CYAN_BOLD),
                    new StyledSpan("  v" + version, Style.DIM))));
            lines.add(StyledLine.styled(INDENT + "终端 AI 助手 · 一期工程（流式对话）", Style.DIM));
            lines.add(new StyledLine(List.of(
                    new StyledSpan(INDENT + "供应商：", Style.DEFAULT),
                    new StyledSpan(providerDescription, Style.CYAN))));
            lines.add(StyledLine.styled(INDENT + "点击输入框或直接按键开始输入；点击思考块或 Ctrl+T 展开/收起", Style.DIM));
            lines.add(StyledLine.empty());
            return lines;
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

    /** 用户消息：带背景色块；续行对齐首行文本列。 */
    record UserItem(String text) implements ScreenItem {
        @Override
        public List<StyledLine> render(int width) {
            String prefix = INDENT + "You ❯ ";
            int prefixWidth = TextWrap.width(prefix);
            List<String> wrapped = TextWrap.wrap(text, Math.max(2, width - prefixWidth));
            List<StyledLine> lines = new ArrayList<>();
            for (int i = 0; i < wrapped.size(); i++) {
                List<StyledSpan> spans = new ArrayList<>();
                if (i == 0) {
                    spans.add(new StyledSpan(INDENT, Style.DEFAULT));
                    spans.add(new StyledSpan("You", Style.CYAN_BOLD));
                    spans.add(new StyledSpan(" ❯ ", Style.CYAN));
                } else {
                    spans.add(new StyledSpan(" ".repeat(prefixWidth), Style.DEFAULT));
                }
                spans.add(new StyledSpan(wrapped.get(i), Style.USER_TEXT));
                lines.add(new StyledLine(spans));
            }
            lines.add(StyledLine.empty());
            return lines;
        }
    }

    /** 回答正文块：顶层无边界线，与思考块（缩进边界线区）靠空间层级区分。 */
    final class AnswerBlock implements ScreenItem {
        private final StringBuilder body = new StringBuilder();
        private List<StyledLine> cachedLines = List.of();
        private String cachedText = "";
        private int cachedWidth = -1;

        public synchronized void append(String text) {
            body.append(text);
        }

        public synchronized String text() {
            return body.toString();
        }

        @Override
        public synchronized List<StyledLine> render(int width) {
            String text = body.toString();
            if (cachedWidth == width && cachedText.equals(text)) {
                return cachedLines;
            }
            List<StyledLine> lines = new ArrayList<>();
            lines.addAll(MarkdownRenderer.render(text, width));
            lines.add(StyledLine.empty());
            cachedLines = lines;
            cachedText = text;
            cachedWidth = width;
            return lines;
        }
    }

    /** 空行分隔项：块之间的空间留白。 */
    record SpacerItem() implements ScreenItem {
        @Override
        public List<StyledLine> render(int width) {
            return List.of(StyledLine.empty());
        }
    }

    /** 排队中的 prompt（F9）：带 queued 标记，消费时由 ChatScreen 移除。 */
    record QueuedItem(String text) implements ScreenItem {
        @Override
        public List<StyledLine> render(int width) {
            return List.of(StyledLine.styled(INDENT + "⧗ queued: " + text, Style.YELLOW_BOLD));
        }
    }

    /** 错误信息（F8）。 */
    record ErrorItem(String message) implements ScreenItem {
        @Override
        public List<StyledLine> render(int width) {
            List<StyledLine> lines = new ArrayList<>();
            for (String line : TextWrap.wrap("✗ " + message, width - TextWrap.width(INDENT))) {
                lines.add(StyledLine.styled(INDENT + line, Style.RED_BOLD));
            }
            return lines;
        }
    }

    /** 普通提示/命令输出。 */
    record NoteItem(String text) implements ScreenItem {
        @Override
        public List<StyledLine> render(int width) {
            List<StyledLine> lines = new ArrayList<>();
            for (String line : TextWrap.wrap(text, width - TextWrap.width(INDENT))) {
                lines.add(StyledLine.styled(INDENT + line, Style.DIM));
            }
            return lines;
        }
    }
}
