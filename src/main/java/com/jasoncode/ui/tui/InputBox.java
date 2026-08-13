package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏 TUI 的输入框模型。
 * <p>
 * 负责管理输入缓冲区、光标位置、输入历史，并将文本按终端宽度折行，
 * 输出给 {@link LanternaTui} 渲染。所有字符宽度均按 {@link TextWrap}
 * 计算（CJK / Emoji 双列），保证中文、Emoji 输入稳定。
 */
public final class InputBox {

    static final int MAX_INPUT_ROWS = 5;
    static final String PROMPT = ScreenItem.INDENT + "You ❯ ";

    private final StringBuilder buffer = new StringBuilder();

    private record WrappedLine(String text, int start) {
    }

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private int cursor = 0;
    private boolean focused;

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public String text() {
        return buffer.toString();
    }

    public int cursor() {
        return cursor;
    }

    public void clear() {
        buffer.setLength(0);
        cursor = 0;
    }

    public void setText(String text) {
        buffer.setLength(0);
        buffer.append(text);
        cursor = text.length();
    }

    public void insert(char c) {
        if (c < 32) {
            return;
        }
        int count = Character.charCount(c);
        buffer.insert(cursor, c);
        cursor += count;
    }

    public void insertNewline() {
        buffer.insert(cursor, '\n');
        cursor++;
    }

    public void backspace() {
        if (cursor <= 0) {
            return;
        }
        int prev = prevCodePointIndex(cursor);
        buffer.delete(prev, cursor);
        cursor = prev;
    }

    public void delete() {
        if (cursor >= buffer.length()) {
            return;
        }
        int next = nextCodePointIndex(cursor);
        buffer.delete(cursor, next);
    }

    public void moveCursorLeft() {
        if (cursor > 0) {
            cursor = prevCodePointIndex(cursor);
        }
    }

    public void moveCursorRight() {
        if (cursor < buffer.length()) {
            cursor = nextCodePointIndex(cursor);
        }
    }

    public void moveCursorHome() {
        cursor = 0;
    }

    public void moveCursorEnd() {
        cursor = buffer.length();
    }

    public void moveCursorUp(int contentCols) {
        moveCursorVertical(contentCols, -1);
    }

    public void moveCursorDown(int contentCols) {
        moveCursorVertical(contentCols, 1);
    }

    /**
     * 根据鼠标点击位置设置光标。
     *
     * @param lineInInput 点击的输入行在可见区域内的行索引
     * @param column      鼠标点击的绝对列号
     * @param contentCols 当前输入内容可用宽度
     */
    public void moveCursorTo(int lineInInput, int column, int contentCols) {
        InputLayout layout = build(contentCols);
        if (lineInInput < 0 || lineInInput >= layout.rawLines().size()) {
            return;
        }
        String line = layout.rawLines().get(lineInInput);
        int textCol = Math.max(0, column - layout.promptWidth());
        int charOffset = charIndexByWidth(line, textCol);
        int start = layout.lineStarts().get(lineInInput);
        cursor = start + charOffset;
    }

    public void historyUp() {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex < 0) {
            historyIndex = history.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }
        buffer.setLength(0);
        buffer.append(history.get(historyIndex));
        cursor = buffer.length();
    }

    public void historyDown() {
        if (historyIndex < 0) {
            return;
        }
        historyIndex++;
        buffer.setLength(0);
        if (historyIndex < history.size()) {
            buffer.append(history.get(historyIndex));
        }
        cursor = buffer.length();
    }

    public void addHistory(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(text)) {
            history.add(text);
        }
    }

    /**
     * 按最大宽度折行，并保留每行在原始文本中的起始字符索引。
     * <p>
     * 这样可以正确把光标位置映射到换行后的某一行，不会因为换行符被吞掉而错位。
     */
    private List<WrappedLine> wrapWithStarts(String text, int maxWidth) {
        List<WrappedLine> result = new ArrayList<>();
        String[] segments = text.split("\n", -1);
        int pos = 0;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                result.add(new WrappedLine("", pos));
            } else {
                for (String line : TextWrap.wrap(segment, maxWidth)) {
                    result.add(new WrappedLine(line, pos));
                    pos += line.length();
                }
            }
            // 最后一个 segment 后面没有换行符
            if (i < segments.length - 1) {
                pos++; // 换行符占 1 个字符
            }
        }
        return result;
    }

    /**
     * 构建当前缓冲区的输入布局。
     *
     * @param contentCols 文本可用宽度（不含右侧留白）
     */
    public InputLayout build(int contentCols) {
        int promptWidth = TextWrap.width(PROMPT);
        int maxTextWidth = Math.max(2, contentCols - promptWidth);

        String text = buffer.toString();
        List<WrappedLine> fullWrapped = wrapWithStarts(text, maxTextWidth);
        if (fullWrapped.isEmpty()) {
            fullWrapped = List.of(new WrappedLine("", 0));
        }

        List<Integer> lineStarts = new ArrayList<>();
        for (WrappedLine line : fullWrapped) {
            lineStarts.add(line.start);
        }

        int cursorLine = 0;
        for (int i = 0; i < fullWrapped.size(); i++) {
            int start = lineStarts.get(i);
            int end = (i == fullWrapped.size() - 1)
                    ? buffer.length()
                    : start + fullWrapped.get(i).text.length();
            if (cursor >= start && cursor <= end) {
                cursorLine = i;
                break;
            }
        }

        // 限制显示高度，同时保证光标所在行可见
        int startLine = 0;
        if (fullWrapped.size() > MAX_INPUT_ROWS) {
            startLine = Math.max(0, Math.min(cursorLine - (MAX_INPUT_ROWS - 1), fullWrapped.size() - MAX_INPUT_ROWS));
        }
        int endLine = Math.min(fullWrapped.size(), startLine + MAX_INPUT_ROWS);
        List<String> rawLines = new ArrayList<>();
        for (int i = startLine; i < endLine; i++) {
            rawLines.add(fullWrapped.get(i).text);
        }

        // 计算光标在屏幕上的位置
        String cursorLineText = fullWrapped.get(cursorLine).text;
        int cursorLineStart = lineStarts.get(cursorLine);
        int prefixLength = Math.min(cursor - cursorLineStart, cursorLineText.length());
        String cursorPrefix = cursorLineText.substring(0, prefixLength);
        int cursorCol = promptWidth + TextWrap.width(cursorPrefix);
        int cursorRow = cursorLine - startLine;

        // 可见行的起始字符索引（用于鼠标定位光标）
        List<Integer> visibleStarts = new ArrayList<>();
        for (int i = startLine; i < endLine; i++) {
            visibleStarts.add(lineStarts.get(i));
        }

        return new InputLayout(rawLines, cursorRow, cursorCol, visibleStarts, startLine, promptWidth);
    }

    private void moveCursorVertical(int contentCols, int direction) {
        if (buffer.isEmpty()) {
            return;
        }
        int promptWidth = TextWrap.width(PROMPT);
        int maxTextWidth = Math.max(2, contentCols - promptWidth);
        List<WrappedLine> fullWrapped = wrapWithStarts(buffer.toString(), maxTextWidth);
        if (fullWrapped.isEmpty()) {
            return;
        }

        List<Integer> lineStarts = new ArrayList<>();
        for (WrappedLine line : fullWrapped) {
            lineStarts.add(line.start);
        }

        int cursorLine = 0;
        for (int i = 0; i < fullWrapped.size(); i++) {
            int start = lineStarts.get(i);
            int end = start + fullWrapped.get(i).text.length();
            if (cursor >= start && cursor <= end) {
                cursorLine = i;
                break;
            }
        }

        int targetLine = cursorLine + direction;
        if (targetLine < 0) {
            cursor = 0;
            return;
        }
        if (targetLine >= fullWrapped.size()) {
            cursor = buffer.length();
            return;
        }

        // 保持当前行内的列位置，尽量对齐到目标行
        String currentLine = fullWrapped.get(cursorLine).text;
        int currentLineStart = lineStarts.get(cursorLine);
        String prefix = currentLine.substring(0, cursor - currentLineStart);
        int currentWidth = TextWrap.width(prefix);

        String targetLineText = fullWrapped.get(targetLine).text;
        int newOffset = charIndexByWidth(targetLineText, currentWidth);
        cursor = lineStarts.get(targetLine) + newOffset;
    }

    private int prevCodePointIndex(int index) {
        if (index <= 0) {
            return 0;
        }
        char c = buffer.charAt(index - 1);
        if (Character.isLowSurrogate(c) && index >= 2) {
            char high = buffer.charAt(index - 2);
            if (Character.isHighSurrogate(high)) {
                return index - 2;
            }
        }
        return index - 1;
    }

    private int nextCodePointIndex(int index) {
        if (index >= buffer.length()) {
            return buffer.length();
        }
        char c = buffer.charAt(index);
        if (Character.isHighSurrogate(c) && index + 1 < buffer.length()) {
            return index + 2;
        }
        return index + 1;
    }

    private int charIndexByWidth(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return 0;
        }
        int w = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int cw = TextWrap.charWidth(cp);
            if (w + cw > maxWidth) {
                break;
            }
            w += cw;
            i += Character.charCount(cp);
        }
        return i;
    }
}
