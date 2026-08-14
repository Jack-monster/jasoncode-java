package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 简易渲染器（v0.4.0+）：把 Markdown 文本渲染为带 lipgloss 样式的 ANSI 字符串。
 * <p>
 * 支持的元素：
 * - 六级标题（# ~ ######），H1/H2 使用醒目的主色加粗，与上下文有空行分隔
 * - 无序列表（- / * / +）、有序列表（1. / 2.），支持两级嵌套
 * - 块引用（>），支持多行合并
 * - 行内代码（`code`）、代码块（```lang ... ```），代码块带独立背景色与语言标识
 * - 加粗（**text**）、斜体（*text* / _text_）
 * - 链接（[text](url)），仅显示文本并加下划线
 * - 表格（GFM pipe table），含表头行、分隔行、数据行，自动对齐列宽
 * 不支持图片、HTML 等复杂元素。
 */
public final class MarkdownRenderer {

    // ── 前景色优先调色板（无背景色，兼容浅色/深色终端） ──
    private static final Style CODE = Style.newStyle().foreground(Color.color("30"));
    private static final Style CODE_BLOCK = Style.newStyle().foreground(Color.color("246"));
    private static final Style CODE_BLOCK_BORDER = Style.newStyle().foreground(Color.color("244"));
    private static final Style CODE_BLOCK_LANG = Style.newStyle().foreground(Color.color("39")).bold(true);
    private static final Style BOLD = Style.newStyle().foreground(Color.color("214")).bold(true);
    private static final Style ITALIC = Style.newStyle().foreground(Color.color("141")).italic(true);
    private static final Style LINK = Style.newStyle().foreground(Color.color("75")).underline(true);
    private static final Style HEADER = Style.newStyle().foreground(Color.color("39")).bold(true);
    private static final Style HEADER_MINOR = Style.newStyle().bold(true);
    private static final Style QUOTE = Style.newStyle().foreground(Color.color("244"));
    private static final Style QUOTE_BORDER = Style.newStyle().foreground(Color.color("60"));
    private static final Style PLAIN = Style.newStyle();
    private static final Style TABLE_HEADER = Style.newStyle().bold(true).foreground(Color.color("39"));
    private static final Style TABLE_CELL = Style.newStyle();
    private static final Style TABLE_BORDER = Style.newStyle().foreground(Color.color("244"));

    private record Span(String text, Style style) {}

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)");
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*)(?:([-*+])|((\\d+)\\.))\\s+(.*)");
    private static final Pattern LINK_PATTERN = Pattern.compile("^\\[([^\\]]+)\\]\\([^)]+\\)(.*)");
    private static final Pattern ORDERED_MARKER = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)");
    private static final Pattern BULLET_MARKER = Pattern.compile("^(\\s*)[-*+]\\s+(.*)");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\|\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|\\s*$");

    /**
     * 渲染 Markdown 文本为 ANSI 样式字符串。
     *
     * @param markdown 原始 Markdown
     * @param width    可用显示宽度（包含左侧缩进）
     * @return ANSI 样式字符串，每行以 \n 结尾
     */
    public static String render(String markdown, int width) {
        StringBuilder out = new StringBuilder();
        String[] rawLines = markdown.split("\n", -1);

        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();
        String codeLang = null;

        int i = 0;
        while (i < rawLines.length) {
            String raw = rawLines[i];

            if (raw.startsWith("```")) {
                if (inCodeBlock) {
                    renderCodeBlock(out, codeBuffer.toString(), codeLang, width);
                    inCodeBlock = false;
                    codeBuffer.setLength(0);
                    codeLang = null;
                } else {
                    inCodeBlock = true;
                    codeLang = raw.length() > 3 ? raw.substring(3).trim() : null;
                }
                i++;
                continue;
            }

            if (inCodeBlock) {
                if (codeBuffer.length() > 0) {
                    codeBuffer.append('\n');
                }
                codeBuffer.append(raw);
                i++;
                continue;
            }

            if (raw.isEmpty()) {
                out.append("\n");
                i++;
                continue;
            }

            // 块引用：可能跨多行
            if (raw.startsWith(">")) {
                i = renderQuote(rawLines, i, out, width);
                continue;
            }

            // 表格：检测 pipe table（至少需要表头行 + 分隔行 + 可选数据行）
            if (isTableRow(raw) && i + 1 < rawLines.length && isTableSeparator(rawLines[i + 1])) {
                i = renderTable(rawLines, i, out, width);
                continue;
            }

            // 标题
            Matcher header = HEADER_PATTERN.matcher(raw);
            if (header.matches()) {
                renderHeader(out, header, width);
                i++;
                continue;
            }

            // 列表
            if (isListItem(raw)) {
                i = renderListGroup(rawLines, i, out, width);
                continue;
            }

            // 普通段落
            renderParagraph(out, raw, width);
            i++;
        }

        // 未闭合的代码块：兜底渲染
        if (inCodeBlock) {
            renderCodeBlock(out, codeBuffer.toString(), codeLang, width);
        }

        return out.toString();
    }

    private static void renderHeader(StringBuilder out, Matcher header, int width) {
        int level = header.group(1).length();
        String text = header.group(2).trim();
        Style style = level <= 2 ? HEADER : (level <= 4 ? HEADER_MINOR : PLAIN);

        out.append("\n");
        List<Span> spans = new ArrayList<>();
        spans.add(new Span(ScreenItem.INDENT, PLAIN));
        spans.add(new Span(text, style));
        for (String line : renderSpans(wrapSpans(spans, width))) {
            out.append(line).append("\n");
        }
        out.append("\n");
    }

    private static boolean isListItem(String raw) {
        return LIST_PATTERN.matcher(raw).matches();
    }

    private static int listLevel(String raw) {
        int spaces = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ' ') {
                spaces++;
            } else {
                break;
            }
        }
        return spaces / 2;
    }

    private static int renderListGroup(String[] rawLines, int start, StringBuilder out, int width) {
        int i = start;
        while (i < rawLines.length) {
            String raw = rawLines[i];
            if (raw.isEmpty()) {
                i++;
                continue;
            }
            if (!isListItem(raw)) {
                break;
            }
            int level = listLevel(raw);
            level = Math.max(0, Math.min(level, 2));

            String marker;
            String content;
            Matcher ordered = ORDERED_MARKER.matcher(raw);
            Matcher bullet = BULLET_MARKER.matcher(raw);
            if (ordered.matches()) {
                marker = ordered.group(2) + ". ";
                content = ordered.group(3);
            } else if (bullet.matches()) {
                marker = "• ";
                content = bullet.group(2);
            } else {
                i++;
                continue;
            }

            String indentPrefix = ScreenItem.INDENT + " ".repeat(level * 2) + marker;
            List<Span> spans = new ArrayList<>();
            spans.add(new Span(indentPrefix, PLAIN));
            spans.addAll(parseInline(content));
            for (String line : renderSpans(wrapSpans(spans, width))) {
                out.append(line).append("\n");
            }
            i++;
        }
        return i;
    }

    private static int renderQuote(String[] rawLines, int start, StringBuilder out, int width) {
        int i = start;
        StringBuilder quoteText = new StringBuilder();
        while (i < rawLines.length && rawLines[i].startsWith(">")) {
            String raw = rawLines[i];
            String lineContent = raw.startsWith("> ") ? raw.substring(2) : raw.substring(1);
            if (quoteText.length() > 0) {
                quoteText.append(' ');
            }
            quoteText.append(lineContent.trim());
            i++;
        }
        int availWidth = Math.max(2, width - TextWrap.width(ScreenItem.INDENT) - 2);
        for (String line : TextWrap.wrap(quoteText.toString(), availWidth)) {
            out.append(QUOTE_BORDER.render(ScreenItem.INDENT + "│ ")).append(QUOTE.render(line)).append("\n");
        }
        return i;
    }

    private static boolean isTableRow(String line) {
        return TABLE_ROW_PATTERN.matcher(line).matches();
    }

    private static boolean isTableSeparator(String line) {
        return TABLE_SEPARATOR_PATTERN.matcher(line).matches();
    }

    private static List<String> parseTableRow(String line) {
        String trimmed = line.trim();
        // Remove leading and trailing pipes
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        // Simple split by | — does not handle escaped \| in cells
        for (String cell : trimmed.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    /**
     * 检测对齐方式：:- left, -: right, :-: center，无冒号为默认 left
     */
    private enum Align { LEFT, CENTER, RIGHT }

    private static List<Align> parseTableAligns(String separatorLine) {
        List<String> rawCells = parseTableRow(separatorLine);
        List<Align> aligns = new ArrayList<>();
        for (String cell : rawCells) {
            String c = cell.trim();
            boolean leftColon = c.startsWith(":");
            boolean rightColon = c.endsWith(":");
            if (leftColon && rightColon) {
                aligns.add(Align.CENTER);
            } else if (rightColon) {
                aligns.add(Align.RIGHT);
            } else if (leftColon) {
                aligns.add(Align.LEFT);
            } else {
                aligns.add(Align.LEFT);
            }
        }
        return aligns;
    }

    private static int[] computeColumnWidths(List<List<String>> rows, int numCols) {
        int[] widths = new int[numCols];
        for (List<String> row : rows) {
            for (int c = 0; c < numCols && c < row.size(); c++) {
                // Parse inline to get display text width (strip markdown syntax)
                String displayText = stripInlineMarkdown(row.get(c));
                widths[c] = Math.max(widths[c], TextWrap.width(displayText));
            }
        }
        return widths;
    }

    /**
     * 粗略去除 inline markdown 标记以测量显示宽度。
     */
    private static String stripInlineMarkdown(String text) {
        String s = text;
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "$1"); // bold
        s = s.replaceAll("\\*(.+?)\\*", "$1");     // italic
        s = s.replaceAll("_(.+?)_", "$1");           // italic underscore
        s = s.replaceAll("`(.+?)`", "$1");            // inline code
        s = s.replaceAll("\\[(.+?)]\\([^)]+\\)", "$1"); // link
        return s;
    }

    private static int renderTable(String[] rawLines, int start, StringBuilder out, int width) {
        int i = start;

        // Collect all table rows: header + separator + data rows
        List<String> headerCells = parseTableRow(rawLines[i]);
        i++; // skip header

        String separator = rawLines[i];
        List<Align> aligns = parseTableAligns(separator);
        i++; // skip separator

        List<List<String>> dataRows = new ArrayList<>();
        while (i < rawLines.length && isTableRow(rawLines[i])) {
            dataRows.add(parseTableRow(rawLines[i]));
            i++;
        }

        int numCols = headerCells.size();
        // Ensure aligns list has enough entries
        while (aligns.size() < numCols) {
            aligns.add(Align.LEFT);
        }

        // Build all rows for width computation
        List<List<String>> allRows = new ArrayList<>();
        allRows.add(headerCells);
        allRows.addAll(dataRows);

        int[] colWidths = computeColumnWidths(allRows, numCols);

        // Shrink columns if total width exceeds available space
        int indentWidth = TextWrap.width(ScreenItem.INDENT);
        int availableWidth = Math.max(10, width - indentWidth);
        // Each column has 1 space padding on each side + 1 border char = 3 chars per column + 1 trailing border
        int totalTableWidth = numCols * 3 + 1;
        for (int w : colWidths) {
            totalTableWidth += w;
        }
        if (totalTableWidth > availableWidth) {
            // Distribute available width evenly
            int contentWidth = Math.max(numCols * 2, availableWidth - numCols * 3 - 1);
            int perCol = contentWidth / numCols;
            for (int c = 0; c < numCols; c++) {
                colWidths[c] = perCol;
            }
        }

        out.append("\n");

        // Top border
        out.append(TABLE_BORDER.render(ScreenItem.INDENT + buildBorderLine(colWidths, '┌', '┬', '┐'))).append("\n");

        // Header row
        out.append(renderTableRow(headerCells, colWidths, aligns, TABLE_HEADER)).append("\n");

        // Header-body separator
        out.append(TABLE_BORDER.render(ScreenItem.INDENT + buildBorderLine(colWidths, '├', '┼', '┤'))).append("\n");

        // Data rows
        for (List<String> row : dataRows) {
            out.append(renderTableRow(row, colWidths, aligns, TABLE_CELL)).append("\n");
        }

        // Bottom border
        out.append(TABLE_BORDER.render(ScreenItem.INDENT + buildBorderLine(colWidths, '└', '┴', '┘'))).append("\n");
        out.append("\n");

        return i;
    }

    private static String buildBorderLine(int[] colWidths, char left, char mid, char right) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int c = 0; c < colWidths.length; c++) {
            if (c > 0) {
 sb.append(mid); }
            sb.append("─".repeat(colWidths[c] + 2));
        }
        sb.append(right);
        return sb.toString();
    }

    private static String renderTableRow(List<String> cells, int[] colWidths, List<Align> aligns, Style style) {
        StringBuilder sb = new StringBuilder();
        sb.append(TABLE_BORDER.render(ScreenItem.INDENT + "│"));
        for (int c = 0; c < colWidths.length; c++) {
            String cellText = c < cells.size() ? cells.get(c) : "";
            // Parse inline markdown within the cell
            List<Span> cellSpans = parseInline(cellText);
            // Render spans and then pad to column width
            StringBuilder cellOut = new StringBuilder();
            Style currentStyle = null;
            StringBuilder buffer = new StringBuilder();
            for (Span span : cellSpans) {
                if (span.style() != currentStyle) {
                    if (buffer.length() > 0) {
                        cellOut.append(currentStyle.render(buffer.toString()));
                        buffer.setLength(0);
                    }
                    currentStyle = span.style();
                }
                buffer.append(span.text());
            }
            if (buffer.length() > 0) {
                cellOut.append(currentStyle.render(buffer.toString()));
            }
            // Calculate display width of cell content (strip ANSI for width calculation)
            String displayText = stripInlineMarkdown(cellText);
            int displayWidth = TextWrap.width(displayText);
            int padBefore = 0;
            int padAfter = 0;
            int padding = Math.max(0, colWidths[c] - displayWidth);
            Align align = c < aligns.size() ? aligns.get(c) : Align.LEFT;
            switch (align) {
                case LEFT -> { padBefore = 0; padAfter = padding; }
                case RIGHT -> { padBefore = padding; padAfter = 0; }
                case CENTER -> { padBefore = padding / 2; padAfter = padding - padBefore; }
            }
            // 1 space left padding + content + padAfter + 1 space right padding
            sb.append(" ".repeat(padBefore + 1));
            sb.append(cellOut);
            sb.append(" ".repeat(padAfter + 1));
            sb.append(TABLE_BORDER.render("│"));
        }
        return sb.toString();
    }

    private static void renderParagraph(StringBuilder out, String raw, int width) {
        List<Span> spans = new ArrayList<>();
        spans.add(new Span(ScreenItem.INDENT, PLAIN));
        spans.addAll(parseInline(raw));
        for (String line : renderSpans(wrapSpans(spans, width))) {
            out.append(line).append("\n");
        }
    }

    private static void renderCodeBlock(StringBuilder out, String code, String lang, int width) {
        int indentWidth = TextWrap.width(ScreenItem.INDENT);
        int availableWidth = Math.max(10, width - indentWidth);
        String label = (lang != null && !lang.isEmpty()) ? lang : "";

        out.append("\n");

        // 上边框：┌─ lang ──────────
        out.append(CODE_BLOCK_BORDER.render(ScreenItem.INDENT + "┌─"));
        if (!label.isEmpty()) {
            out.append(CODE_BLOCK_LANG.render(" " + label + " "));
        }
        int topUsed = 2 + (label.isEmpty() ? 0 : TextWrap.width(label) + 2);
        int topRemaining = Math.max(0, availableWidth - topUsed);
        out.append(CODE_BLOCK_BORDER.render("─".repeat(topRemaining)));
        out.append("\n");

        // 代码行：│ <code>
        int codeAvailWidth = Math.max(2, availableWidth - 4);
        for (String codeLine : code.split("\n", -1)) {
            for (String wrapped : TextWrap.wrap(codeLine, codeAvailWidth)) {
                out.append(CODE_BLOCK_BORDER.render(ScreenItem.INDENT + "│ "));
                out.append(CODE_BLOCK.render(wrapped));
                out.append("\n");
            }
        }

        // 下边框：└────────────────
        out.append(CODE_BLOCK_BORDER.render(ScreenItem.INDENT + "└" + "─".repeat(Math.max(1, availableWidth - 1))));
        out.append("\n\n");
    }

    /**
     * 将 Span 列表按显示宽度折行，返回多行 Span 列表。
     * 续行不加缩进（由调用方在 spans 中处理）。
     */
    private static List<List<Span>> wrapSpans(List<Span> spans, int maxWidth) {
        int limit = Math.max(2, maxWidth);
        List<List<Span>> lines = new ArrayList<>();
        List<Span> current = new ArrayList<>();
        int currentWidth = 0;

        for (Span span : spans) {
            if (span.text().isEmpty()) {
                continue;
            }
            // 将 span 按字符拆分，逐字符填入当前行
            for (int i = 0; i < span.text().length(); ) {
                int cp = span.text().codePointAt(i);
                int cw = TextWrap.charWidth(cp);
                String ch = new String(Character.toChars(cp));
                if (currentWidth + cw > limit && !current.isEmpty()) {
                    lines.add(current);
                    current = new ArrayList<>();
                    currentWidth = 0;
                }
                current.add(new Span(ch, span.style()));
                currentWidth += cw;
                i += Character.charCount(cp);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        if (lines.isEmpty()) {
            lines.add(List.of());
        }
        return lines;
    }

    /**
     * 将每行 Span 列表渲染为 ANSI 字符串（合并连续相同 style 的 span）。
     */
    private static List<String> renderSpans(List<List<Span>> lines) {
        List<String> result = new ArrayList<>();
        for (List<Span> line : lines) {
            StringBuilder sb = new StringBuilder();
            Style currentStyle = null;
            StringBuilder buffer = new StringBuilder();
            for (Span span : line) {
                if (span.style() != currentStyle) {
                    if (buffer.length() > 0) {
                        sb.append(currentStyle.render(buffer.toString()));
                        buffer.setLength(0);
                    }
                    currentStyle = span.style();
                }
                buffer.append(span.text());
            }
            if (buffer.length() > 0) {
                sb.append(currentStyle.render(buffer.toString()));
            }
            result.add(sb.toString());
        }
        return result;
    }

    /**
     * 对纯文本做 inline Markdown 解析，返回带样式的 Span 列表。
     */
    private static List<Span> parseInline(String text) {
        List<Span> spans = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int codeIdx = text.indexOf('`', pos);
            int boldIdx = text.indexOf("**", pos);
            int italicIdx = text.indexOf('*', pos);
            int italicUnderIdx = text.indexOf('_', pos);
            int linkIdx = text.indexOf('[', pos);

            int nearest = text.length();
            int type = -1;

            if (codeIdx >= 0 && codeIdx < nearest) {
                nearest = codeIdx;
                type = 0;
            }
            // bold 优先于 italic，避免 ** 被 * 截断
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
                type = 3;
            }
            if (linkIdx >= 0 && linkIdx < nearest) {
                nearest = linkIdx;
                type = 4;
            }

            if (type == -1) {
                spans.add(new Span(text.substring(pos), PLAIN));
                break;
            }

            if (nearest > pos) {
                spans.add(new Span(text.substring(pos, nearest), PLAIN));
            }

            String after = text.substring(nearest);
            switch (type) {
                case 0 -> {
                    int close = after.indexOf('`', 1);
                    if (close > 0) {
                        spans.add(new Span(after.substring(1, close), CODE));
                        pos = nearest + close + 1;
                    } else {
                        spans.add(new Span(text.substring(nearest), PLAIN));
                        pos = text.length();
                    }
                }
                case 1 -> {
                    int close = after.indexOf("**", 2);
                    if (close > 0) {
                        spans.add(new Span(after.substring(2, close), BOLD));
                        pos = nearest + close + 2;
                    } else {
                        spans.add(new Span(text.substring(nearest), PLAIN));
                        pos = text.length();
                    }
                }
                case 2 -> {
                    int close = after.indexOf('*', 1);
                    if (close > 0) {
                        spans.add(new Span(after.substring(1, close), ITALIC));
                        pos = nearest + close + 1;
                    } else {
                        spans.add(new Span(text.substring(nearest), PLAIN));
                        pos = text.length();
                    }
                }
                case 3 -> {
                    int close = after.indexOf('_', 1);
                    if (close > 0) {
                        spans.add(new Span(after.substring(1, close), ITALIC));
                        pos = nearest + close + 1;
                    } else {
                        spans.add(new Span(text.substring(nearest), PLAIN));
                        pos = text.length();
                    }
                }
                case 4 -> {
                    Matcher m = LINK_PATTERN.matcher(after);
                    if (m.matches()) {
                        spans.add(new Span(m.group(1), LINK));
                        int linkSyntaxLen = m.group(0).length() - m.group(2).length();
                        pos = nearest + linkSyntaxLen;
                    } else {
                        spans.add(new Span(text.substring(nearest), PLAIN));
                        pos = text.length();
                    }
                }
            }
        }
        return spans;
    }
}
