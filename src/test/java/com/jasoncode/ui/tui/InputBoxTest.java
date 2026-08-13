package com.jasoncode.ui.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InputBox} 单元测试：输入框模型、光标移动、折行、鼠标定位。
 */
class InputBoxTest {

    @Test
    void emptyLayoutHasOneLineWithPromptWidth() {
        InputBox box = new InputBox();
        box.setFocused(true);
        InputLayout layout = box.build(80);

        assertEquals(1, layout.rawLines().size());
        assertEquals("", layout.rawLines().get(0));
        assertEquals(0, layout.cursorRow());
        assertEquals(TextWrap.width(InputBox.PROMPT), layout.cursorCol());
    }

    @Test
    void textWrapsAtAvailableWidth() {
        InputBox box = new InputBox();
        box.setFocused(true);
        // 输入一段很长的英文
        String text = "a".repeat(200);
        box.setText(text);

        int contentCols = 40;
        InputLayout layout = box.build(contentCols);
        int promptWidth = TextWrap.width(InputBox.PROMPT);
        int maxTextWidth = contentCols - promptWidth;

        for (String line : layout.rawLines()) {
            assertTrue(TextWrap.width(line) <= maxTextWidth,
                    "每行文本宽度应 <= " + maxTextWidth + "，实际=" + TextWrap.width(line));
        }
    }

    @Test
    void cursorMovesLeftAndRightByCodePoint() {
        InputBox box = new InputBox();
        box.setFocused(true);
        box.setText("abc");
        assertEquals(3, box.cursor());

        box.moveCursorLeft();
        assertEquals(2, box.cursor());
        box.moveCursorLeft();
        assertEquals(1, box.cursor());
        box.moveCursorRight();
        assertEquals(2, box.cursor());
        box.moveCursorEnd();
        assertEquals(3, box.cursor());
        box.moveCursorHome();
        assertEquals(0, box.cursor());
    }

    @Test
    void insertAndBackspaceRespectCursor() {
        InputBox box = new InputBox();
        box.setFocused(true);
        box.setText("abcd");
        box.moveCursorHome();
        box.moveCursorRight(); // cursor = 1
        box.insert('X');

        assertEquals("aXbcd", box.text());
        assertEquals(2, box.cursor());

        box.backspace();
        assertEquals("abcd", box.text());
        assertEquals(1, box.cursor());
    }

    @Test
    void multiLineInputProducesMultipleRows() {
        InputBox box = new InputBox();
        box.setFocused(true);
        box.setText("first line");
        box.insertNewline();
        box.insert('X');

        InputLayout layout = box.build(80);
        assertEquals(2, layout.rawLines().size());
        assertEquals("first line", layout.rawLines().get(0));
        assertEquals("X", layout.rawLines().get(1));
    }

    @Test
    void cursorRowFollowsMultiLineLayout() {
        InputBox box = new InputBox();
        box.setFocused(true);
        box.setText("first line");
        box.insertNewline();
        box.insertNewline();
        box.insert('X');

        InputLayout layout = box.build(80);
        assertEquals(3, layout.rawLines().size());
        // 光标在第三行（索引 2）
        assertEquals(2, layout.cursorRow());
    }

    @Test
    void moveCursorUpDownPreservesColumn() {
        InputBox box = new InputBox();
        box.setFocused(true);
        // 构造两行，每行 5 个字符
        box.setText("12345\nabcde");
        box.moveCursorEnd();
        assertEquals(11, box.cursor()); // 5 + 1 + 5

        // 从第二行末尾上移
        box.moveCursorUp(80);
        // 第二行是 "abcde"，移到同一列位置，第一行 "12345"，光标应在 "12345" 末尾
        assertEquals(5, box.cursor());

        // 下移回第二行末尾
        box.moveCursorDown(80);
        assertEquals(11, box.cursor());
    }

    @Test
    void mouseClickSetsCursor() {
        InputBox box = new InputBox();
        box.setFocused(true);
        box.setText("hello world");

        int contentCols = 80;
        InputLayout layout = box.build(contentCols);
        int promptWidth = layout.promptWidth();

        // 点击第 0 行，列在 prompt 后第 3 列，应定位到第 3 个字符（ell -> 'l' 前）
        box.moveCursorTo(0, promptWidth + 3, contentCols);
        assertEquals(3, box.cursor());
    }

    @Test
    void cjkCountedAsTwoColumns() {
        InputBox box = new InputBox();
        box.setFocused(true);
        box.setText("中文字符");

        InputLayout layout = box.build(80);
        // 8 个 CJK 字符，每个 2 列，总宽度 16，仍小于可用宽度
        assertEquals(1, layout.rawLines().size());
        assertEquals("中文字符", layout.rawLines().get(0));

        box.moveCursorEnd();
        assertEquals(4, box.cursor()); // 4 个 CJK 字符
    }

    @Test
    void historyTracksUniqueEntries() {
        InputBox box = new InputBox();
        box.addHistory("hello");
        box.addHistory("world");
        box.addHistory("hello"); // 重复，不加入

        box.setText("current");
        box.historyUp();
        assertEquals("hello", box.text()); // 最后一条历史
        box.historyUp();
        assertEquals("world", box.text()); // 上一条历史
    }
}
