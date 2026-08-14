package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自绘输入框 InputBox 测试：编辑操作、光标按 code point 移动、硬折行渲染、
 * 渲染行数恒定、多行合并、历史读写。
 */
class InputBoxTest {

    @BeforeAll
    static void setupTerminalInfo() {
        TerminalInfo.provide(() -> new TerminalInfo(false, null));
    }

    private static InputBox box() {
        InputBox box = new InputBox();
        box.setPlaceholder("提示");
        return box;
    }

    /** 统计渲染结果的行数（以 \n 个数计，与 view() 的 countLines 一致）。 */
    private static int lineCount(String rendered) {
        int count = 0;
        for (int i = 0; i < rendered.length(); i++) {
            if (rendered.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    // ── 基本编辑 ──

    @Test
    void insertAndValueRoundTrip() {
        InputBox b = box();
        b.insert("hello world");
        assertEquals("hello world", b.value());
    }

    @Test
    void multilineInsertKeepsNewlines() {
        InputBox b = box();
        b.insert("line1\nline2");
        assertEquals("line1\nline2", b.value());
    }

    @Test
    void insertInMiddleMovesCursorAfterInserted() {
        InputBox b = box();
        b.insert("ac");
        b.cursorLeft();               // 光标在 a|c
        b.insert("b");                // → abc，光标在 b 后
        assertEquals("abc", b.value());
    }

    @Test
    void setValueAndReset() {
        InputBox b = box();
        b.setValue("some text");
        assertEquals("some text", b.value());
        b.reset();
        assertEquals("", b.value());
    }

    // ── 删除与行合并 ──

    @Test
    void backspaceDeletesCharacter() {
        InputBox b = box();
        b.insert("abc");
        b.backspace();
        assertEquals("ab", b.value());
    }

    @Test
    void backspaceAtLineStartMergesUp() {
        InputBox b = box();
        b.insert("ab\ncd");          // 光标在 cd 末尾
        b.home();                     // 行 1 行首
        b.backspace();                // 与上行合并
        assertEquals("abcd", b.value());
    }

    @Test
    void deleteAtLineEndMergesDown() {
        InputBox b = box();
        b.insert("ab\ncd");
        b.home();                     // 行 1 行首
        b.cursorUp();                 // 行 0 行首
        b.end();                      // 行 0 行尾（ab|）
        b.deleteForward();            // 合并下行
        assertEquals("abcd", b.value());
    }

    @Test
    void deleteForwardRemovesCharacterUnderCursor() {
        InputBox b = box();
        b.insert("abc");
        b.home();                     // 光标在 a 前
        b.deleteForward();            // 删除 a
        assertEquals("bc", b.value());
    }

    // ── 光标按 code point 移动（中文不切半字） ──

    @Test
    void cursorLeftDoesNotSplitCjk() {
        InputBox b = box();
        b.insert("你好");
        b.cursorLeft();               // 光标移到"你"与"好"之间
        b.backspace();                // 删除光标前的完整字符"你"
        assertEquals("好", b.value());
    }

    @Test
    void cursorUpKeepsColumnClamped() {
        InputBox b = box();
        b.insert("aaaa\nbb");         // 光标在 bb 末尾（col=2）
        b.home();                     // 行 1 行首
        b.cursorUp();                 // 移到行 0，col 截断
        b.end();                      // 行 0 行尾
        assertEquals("aaaa\nbb", b.value()); // 内容不变，仅光标移动
    }

    @Test
    void cursorRightWrapsToNextLine() {
        InputBox b = box();
        b.setValue("ab\ncd");         // 光标在 cd 末尾
        b.home();                     // 行 1 行首
        b.cursorUp();                 // 行 0 行首
        b.end();                      // 行 0 行尾
        b.cursorRight();              // 越过行尾 → 行 1 行首
        b.insert("X");                // 在行 1 行首插入
        assertEquals("ab\nXcd", b.value());
    }

    // ── 渲染：硬折行 + 行数恒定 ──

    @Test
    void longLineWrapsWithoutModifyingValue() {
        InputBox b = box();
        String a150 = "a".repeat(150);
        b.insert(a150);
        // 软折行：value 保持原样，不被插入换行符
        assertEquals(a150, b.value());
        // 渲染后显示为多行
        assertTrue(b.height(40) >= 3);
        assertTrue(b.height(40) <= 5);
    }

    @Test
    void renderProducesExactlyHeightLines() {
        InputBox b = box();
        b.insert("a".repeat(150));
        int width = 40;
        String rendered = b.render(width);
        int height = b.height(width);
        assertEquals(height, lineCount(rendered),
                "渲染行数必须恒等于 height，防止 diff 截断残影");
        assertTrue(rendered.endsWith("\n"), "渲染结果以换行结尾");
    }

    @Test
    void emptyContentRendersPlaceholderAndMinHeight() {
        InputBox b = box();
        int width = 40;
        String rendered = b.render(width);
        assertTrue(rendered.contains("提示"));
        assertEquals(b.height(width), lineCount(rendered));
        assertTrue(b.height(width) >= 3);
    }

    @Test
    void renderContainsPromptAndCursor() {
        InputBox b = box();
        b.insert("hi");
        String rendered = b.render(40);
        assertTrue(rendered.contains("┃"), "渲染应包含提示符");
        assertTrue(rendered.contains("hi"));
    }

    @Test
    void multilinesRenderEachWithAlignment() {
        InputBox b = box();
        b.insert("第一行\n第二行");
        String rendered = b.render(40);
        assertTrue(rendered.contains("第一行"));
        assertTrue(rendered.contains("第二行"));
        assertEquals(b.height(40), lineCount(rendered));
    }
}
