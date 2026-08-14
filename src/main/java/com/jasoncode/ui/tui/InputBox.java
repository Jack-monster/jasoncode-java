package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 自绘多行输入框：不依赖 tui4j Textarea（其单词级 wrap、光标样式、
 * 内部滚动行为均不可控），完全自管理编辑缓冲区、光标与渲染。
 * <p>
 * - 光标位置按 code point 记录，中文/Emoji 不会被切成半个
 * - 折行用 {@link TextWrap} 硬折行，超长无空格内容不会破坏布局
 * - 高度随内容在 MIN_HEIGHT..MAX_HEIGHT 间伸缩，超出显示以光标为锚的滚动窗口
 * - 续行缩进与首行文本对齐，光标以反显块渲染
 * - {@link #render(int)} 恒定输出恰好  行
 */
public final class InputBox {

    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 5;
    private static final String PROMPT = "┃ ";

    /** 逻辑行列表（每行是一个 StringBuilder）。 */
    private final List<StringBuilder> lines = new ArrayList<>(List.of(new StringBuilder()));
    /** 光标所在逻辑行。 */
    private int row;
    /** 光标所在 code point 列。 */
    private int col;
    /** 滚动窗口起点（显示行索引）。 */
    private int displayOffset;

    private final Style promptStyle = Style.newStyle().foreground(Color.color("39")).bold(true);
    private final Style placeholderStyle = Style.newStyle().foreground(Color.color("244"));
    private final Style cursorStyle = Style.newStyle().reverse(true);

    private String placeholder = "";

    // ── 编辑操作 ──

    /** 在光标处插入文本（可含换行）。 */
    public void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] parts = text.split("\n", -1);
        StringBuilder current = lines.get(row);
        String currentStr = current.toString();
        String before = cpSubstring(currentStr, 0, col);
        String after = cpSubstring(currentStr, col, cpCount(currentStr));
        if (parts.length == 1) {
            current.setLength(0);
            current.append(before).append(parts[0]).append(after);
            col += cpCount(parts[0]);
            return;
        }
        lines.set(row, new StringBuilder(before).append(parts[0]));
        List<StringBuilder> middle = new ArrayList<>();
        for (int i = 1; i < parts.length - 1; i++) {
            middle.add(new StringBuilder(parts[i]));
        }
        StringBuilder last = new StringBuilder(parts[parts.length - 1]).append(after);
        lines.addAll(row + 1, middle);
        lines.add(row + 1 + middle.size(), last);
        row = row + middle.size() + 1;
        col = cpCount(parts[parts.length - 1]);
    }

    /** 删除光标前一个字符；行首时与上一行合并。 */
    public void backspace() {
        StringBuilder current = lines.get(row);
        if (col > 0) {
            cpDelete(current, col - 1, col);
            col--;
        } else if (row > 0) {
            StringBuilder above = lines.get(row - 1);
            col = cpCount(above);
            above.append(current);
            lines.remove(row);
            row--;
        }
    }

    /** 删除光标处字符；行尾时与下一行合并。 */
    public void deleteForward() {
        StringBuilder current = lines.get(row);
        int len = cpCount(current);
        if (col < len) {
            cpDelete(current, col, col + 1);
        } else if (row < lines.size() - 1) {
            current.append(lines.get(row + 1));
            lines.remove(row + 1);
        }
    }

    /** 光标左移一个 code point。 */
    public void cursorLeft() {
        if (col > 0) {
            col--;
        } else if (row > 0) {
            row--;
            col = cpCount(lines.get(row));
        }
    }

    /** 光标右移一个 code point。 */
    public void cursorRight() {
        if (col < cpCount(lines.get(row))) {
            col++;
        } else if (row < lines.size() - 1) {
            row++;
            col = 0;
        }
    }

    /** 光标上移一行（保持列，越界时截断）。 */
    public void cursorUp() {
        if (row > 0) {
            row--;
            col = Math.min(col, cpCount(lines.get(row)));
        }
    }

    /** 光标下移一行（保持列，越界时截断）。 */
    public void cursorDown() {
        if (row < lines.size() - 1) {
            row++;
            col = Math.min(col, cpCount(lines.get(row)));
        }
    }

    /** 光标到行首。 */
    public void home() {
        col = 0;
    }

    /** 光标到行尾。 */
    public void end() {
        col = cpCount(lines.get(row));
    }

    // ── 内容读写 ──

    /** 设置占位提示文本（仅空内容时显示）。 */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    /** 整体替换内容，光标移到末尾。 */
    public void setValue(String text) {
        lines.clear();
        if (text == null || text.isEmpty()) {
            lines.add(new StringBuilder());
        } else {
            for (String seg : text.split("\n", -1)) {
                lines.add(new StringBuilder(seg));
            }
        }
        row = lines.size() - 1;
        col = cpCount(lines.get(row));
    }

    /** 当前文本内容（逻辑行以 \n 连接）。 */
    public String value() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** 清空内容并重置光标。 */
    public void reset() {
        lines.clear();
        lines.add(new StringBuilder());
        row = 0;
        col = 0;
        displayOffset = 0;
    }

    // ── 渲染 ──

    /**
     * 渲染输入框：恰好输出 {@code height(width)} 行（每行以 \n 结尾），
     * 光标以反显块标出。
     *
     * @param width 终端总宽
     * @return ANSI 样式字符串
     */
    public String render(int width) {
        int promptW = TextWrap.width(PROMPT);
        int contentW = Math.max(2, width - promptW - 1);

        // 空内容（单个空行）时显示占位提示
        boolean empty = lines.size() == 1 && lines.get(0).length() == 0;

        // 逻辑行 → 显示行，同时定位光标所在显示行与行内 code point 偏移
        List<String> display = new ArrayList<>();
        int cursorDisplayRow = 0;
        int cursorOffsetInRow = 0;
        if (!empty) {
            for (int i = 0; i < lines.size(); i++) {
                String lineStr = lines.get(i).toString();
                List<String> wrapped = lineStr.isEmpty()
                        ? List.of("")
                        : TextWrap.wrap(lineStr, contentW);
                if (i == row) {
                    // 在光标行的显示行中定位：col 落在第几段、段内偏移多少 code point
                    int consumed = 0;
                    int inRow = 0;
                    for (String w : wrapped) {
                        int wCp = cpCount(w);
                        if (col <= consumed + wCp) {
                            cursorOffsetInRow = col - consumed;
                            break;
                        }
                        consumed += wCp;
                        inRow++;
                    }
                    cursorDisplayRow += inRow;
                }
                if (i < row) {
                    cursorDisplayRow += wrapped.size();
                }
                display.addAll(wrapped);
            }
        }

        int total = display.size();
        int h = Math.min(Math.max(total, MIN_HEIGHT), MAX_HEIGHT);

        // 滚动窗口跟随光标
        if (cursorDisplayRow < displayOffset) {
            displayOffset = cursorDisplayRow;
        }
        if (cursorDisplayRow >= displayOffset + h) {
            displayOffset = cursorDisplayRow - h + 1;
        }
        displayOffset = Math.max(0, Math.min(displayOffset, Math.max(0, total - h)));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h; i++) {
            int di = displayOffset + i;
            String promptPart = (di == 0)
                    ? promptStyle.render(PROMPT)
                    : " ".repeat(promptW);

            if (empty) {
                // 空内容：光标在开头 + 占位提示
                if (i == 0) {
                    sb.append(promptPart)
                            .append(cursorStyle.render(" "))
                            .append(placeholderStyle.render(placeholder))
                            .append('\n');
                } else {
                    sb.append('\n');
                }
                continue;
            }
            if (di >= total) {
                sb.append('\n');
                continue;
            }
            String text = display.get(di);
            if (di == cursorDisplayRow) {
                // 光标行：光标处字符反显（行尾反显空格）
                String before = cpSubstring(text, 0, cursorOffsetInRow);
                int textCp = cpCount(text);
                String cursorChar = cursorOffsetInRow < textCp
                        ? cpSubstring(text, cursorOffsetInRow, cursorOffsetInRow + 1)
                        : " ";
                String after = cursorOffsetInRow < textCp
                        ? cpSubstring(text, cursorOffsetInRow + 1, textCp)
                        : "";
                sb.append(promptPart)
                        .append(before)
                        .append(cursorStyle.render(cursorChar))
                        .append(after)
                        .append('\n');
            } else {
                sb.append(promptPart).append(text).append('\n');
            }
        }
        return sb.toString();
    }

    /** 渲染高度：与 {@link #render(int)} 的输出行数一致。 */
    public int height(int width) {
        if (lines.size() == 1 && lines.get(0).length() == 0) {
            return MIN_HEIGHT;
        }
        int promptW = TextWrap.width(PROMPT);
        int contentW = Math.max(2, width - promptW - 1);
        int total = 0;
        for (StringBuilder line : lines) {
            String s = line.toString();
            total += s.isEmpty() ? 1 : TextWrap.wrap(s, contentW).size();
        }
        return Math.min(Math.max(total, MIN_HEIGHT), MAX_HEIGHT);
    }

    // ── code point 辅助 ──

    private static int cpCount(String s) {
        return s.codePointCount(0, s.length());
    }

    private static int cpCount(StringBuilder sb) {
        return sb.codePointCount(0, sb.length());
    }

    private static String cpSubstring(String s, int startCp, int endCp) {
        int start = s.offsetByCodePoints(0, Math.max(0, startCp));
        int end = s.offsetByCodePoints(0, Math.min(cpCount(s), endCp));
        return s.substring(start, end);
    }

    private static void cpDelete(StringBuilder sb, int startCp, int endCp) {
        String s = sb.toString();
        int start = s.offsetByCodePoints(0, Math.max(0, startCp));
        int end = s.offsetByCodePoints(0, Math.min(cpCount(s), endCp));
        sb.delete(start, end);
    }
}
