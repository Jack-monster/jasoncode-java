package com.jasoncode.ui.tui;

import com.googlecode.lanterna.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 简易渲染器（v0.4.0+）：把 Markdown 文本渲染为带样式的 {@link StyledLine}。
 * <p>
 * 支持的元素：
 * - 六级标题 (# ~ ######)
 * - 无序列表 (- / * / +)、有序列表 (1.)
 * - 块引用 (>)
 * - 行内代码 (`code`)、代码块 (```)
 * - 加粗 (**text**)、斜体 (*text* / _text_)
 * - 链接 ([text](url))，仅显示文本并加下划线
 * 不支持表格、图片、HTML 等复杂元素。
 */
public final class MarkdownRenderer {

    private static final Style CODE = new Style(TextColor.ANSI.GREEN, new TextColor.Indexed(240), false, false);
    private static final Style CODE_BLOCK = new Style(TextColor.ANSI.GREEN, new TextColor.Indexed(236), false, false);
    private static final Style BOLD = Style.YELLOW_BOLD;
    private static final Style ITALIC = Style.DIM;
    private static final Style LINK = new Style(TextColor.ANSI.CYAN, null, false, false);
    private static final Style HEADER = Style.CYAN_BOLD;
    private static final Style QUOTE = Style.DIM;
    private static final Style LIST = Style.DEFAULT;

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.*)");
    private static final Pattern ORDERED_PATTERN = Pattern.compile("^\\s*(\\d+)\\.\\s+(.*)");
    private static final Pattern LINK_PATTERN = Pattern.compile("^\\[([^\\]]+)\\]\\([^)]+\\)(.*)");

    public static List<StyledLine> render(String markdown, int width) {
        List<StyledLine> lines = new ArrayList<>();
        boolean inCodeBlock = false;
        String[] rawLines = markdown.split("\n", -1);
        for (String raw : rawLines) {
            if (raw.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                lines.addAll(wrapWithIndent(StyledLine.styled(ScreenItem.INDENT + "  " + raw, CODE_BLOCK), width));
                continue;
            }
            lines.addAll(renderBlock(raw, width));
        }
        return lines;
    }

    private static List<StyledLine> renderBlock(String raw, int width) {
        List<StyledLine> lines = new ArrayList<>();

        // 标题
        Matcher header = HEADER_PATTERN.matcher(raw);
        if (header.matches()) {
            StyledLine line = new StyledLine(List.of(
                    new StyledSpan(ScreenItem.INDENT, Style.DEFAULT),
                    new StyledSpan(header.group(2), HEADER)));
            lines.addAll(wrapWithIndent(line, width));
            return lines;
        }

        // 无序列表
        Matcher bullet = BULLET_PATTERN.matcher(raw);
        if (bullet.matches()) {
            StyledLine line = new StyledLine(List.of(
                    new StyledSpan(ScreenItem.INDENT + "• ", LIST),
                    new StyledSpan(bullet.group(1), Style.DEFAULT)));
            lines.addAll(wrapWithIndent(line, width));
            return lines;
        }

        // 有序列表
        Matcher ordered = ORDERED_PATTERN.matcher(raw);
        if (ordered.matches()) {
            StyledLine line = new StyledLine(List.of(
                    new StyledSpan(ScreenItem.INDENT + ordered.group(1) + ". ", LIST),
                    new StyledSpan(ordered.group(2), Style.DEFAULT)));
            lines.addAll(wrapWithIndent(line, width));
            return lines;
        }

        // 块引用
        if (raw.startsWith("> ")) {
            StyledLine line = new StyledLine(List.of(
                    new StyledSpan(ScreenItem.INDENT + "│ ", QUOTE),
                    new StyledSpan(raw.substring(2), Style.DEFAULT)));
            lines.addAll(wrapWithIndent(line, width));
            return lines;
        }

        // 空行
        if (raw.isEmpty()) {
            lines.add(StyledLine.empty());
            return lines;
        }

        // 普通段落
        List<StyledSpan> spans = new ArrayList<>();
        spans.add(new StyledSpan(ScreenItem.INDENT, Style.DEFAULT));
        spans.addAll(parseInline(raw));
        lines.addAll(wrapWithIndent(new StyledLine(spans), width));
        return lines;
    }

    /**
     * 折行，并在续行处保留缩进（去掉 bullet/quote 标记，保留缩进宽度）。
     */
    private static List<StyledLine> wrapWithIndent(StyledLine line, int width) {
        List<StyledLine> wrapped = line.wrap(width);
        if (wrapped.size() <= 1) {
            return wrapped;
        }
        StyledSpan indentSpan = line.spans().get(0);
        String indentText = indentSpan.text().replace('•', ' ').replace('│', ' ');
        Style indentStyle = indentSpan.style();

        List<StyledLine> result = new ArrayList<>();
        result.add(wrapped.get(0));
        for (int i = 1; i < wrapped.size(); i++) {
            List<StyledSpan> spans = new ArrayList<>();
            spans.add(new StyledSpan(indentText, indentStyle));
            spans.addAll(wrapped.get(i).spans());
            result.add(new StyledLine(spans));
        }
        return result;
    }

    private static List<StyledSpan> parseInline(String text) {
        List<StyledSpan> spans = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            int codeIdx = remaining.indexOf('`');
            int boldIdx = remaining.indexOf("**");
            int italicIdx = remaining.indexOf('*');
            int italicUnderIdx = remaining.indexOf('_');
            int linkIdx = remaining.indexOf('[');

            int nearest = Integer.MAX_VALUE;
            int type = -1;
            if (codeIdx >= 0 && codeIdx < nearest) {
                nearest = codeIdx;
                type = 0;
            }
            if (boldIdx >= 0 && boldIdx < nearest) {
                nearest = boldIdx;
                type = 1;
            }
            if (italicIdx >= 0 && italicIdx < nearest) {
                nearest = italicIdx;
                type = 2;
            }
            if (italicUnderIdx >= 0 && italicUnderIdx < nearest) {
                nearest = italicUnderIdx;
                type = 2;
            }
            if (linkIdx >= 0 && linkIdx < nearest) {
                nearest = linkIdx;
                type = 3;
            }
            if (type == -1) {
                spans.add(new StyledSpan(remaining, Style.DEFAULT));
                break;
            }
            if (nearest > 0) {
                spans.add(new StyledSpan(remaining.substring(0, nearest), Style.DEFAULT));
            }
            String after = remaining.substring(nearest);
            switch (type) {
                case 0 -> {
                    int close = after.indexOf('`', 1);
                    if (close > 0) {
                        spans.add(new StyledSpan(after.substring(1, close), CODE));
                        remaining = after.substring(close + 1);
                    } else {
                        spans.add(new StyledSpan(after, Style.DEFAULT));
                        remaining = "";
                    }
                }
                case 1 -> {
                    int close = after.indexOf("**", 2);
                    if (close > 0) {
                        spans.add(new StyledSpan(after.substring(2, close), BOLD));
                        remaining = after.substring(close + 2);
                    } else {
                        spans.add(new StyledSpan(after, Style.DEFAULT));
                        remaining = "";
                    }
                }
                case 2 -> {
                    char marker = after.charAt(0);
                    int close = after.indexOf(marker, 1);
                    if (close > 0) {
                        spans.add(new StyledSpan(after.substring(1, close), ITALIC));
                        remaining = after.substring(close + 1);
                    } else {
                        spans.add(new StyledSpan(after, Style.DEFAULT));
                        remaining = "";
                    }
                }
                case 3 -> {
                    Matcher m = LINK_PATTERN.matcher(after);
                    if (m.matches()) {
                        spans.add(new StyledSpan(m.group(1), LINK));
                        remaining = m.group(2);
                    } else {
                        spans.add(new StyledSpan(after, Style.DEFAULT));
                        remaining = "";
                    }
                }
            }
        }
        return spans;
    }
}
