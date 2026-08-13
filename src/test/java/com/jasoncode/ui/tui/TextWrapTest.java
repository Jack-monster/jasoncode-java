package com.jasoncode.ui.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TextWrap 宽度感知换行测试（F3）：CJK 双列宽度、贪心折行、换行分段。
 */
class TextWrapTest {

    @Test
    void cjkCharsCountTwoColumns() {
        assertEquals(1, TextWrap.charWidth('a'));
        assertEquals(2, TextWrap.charWidth('中'));
        assertEquals(2, TextWrap.charWidth('Ａ'));
        assertEquals(6, TextWrap.width("中文ab"));
    }

    @Test
    void wrapSplitsByColumnWidth() {
        // 4 列：每个汉字占 2 列，一行最多 2 个汉字
        List<String> lines = TextWrap.wrap("中中中中中", 4);
        assertEquals(List.of("中中", "中中", "中"), lines);
    }

    @Test
    void wrapKeepsExplicitNewlines() {
        List<String> lines = TextWrap.wrap("ab\ncd", 10);
        assertEquals(List.of("ab", "cd"), lines);
        // 空段保留为空行
        assertEquals(List.of("ab", "", "cd"), TextWrap.wrap("ab\n\ncd", 10));
    }

    @Test
    void emojiCountedAsTwoColumnsAndNotSplit() {
        String emoji = "😀"; // U+1F600，surrogate pair
        assertEquals(2, TextWrap.width(emoji));
        List<String> lines = TextWrap.wrap("a" + emoji + "b", 3);
        // 3 列容不下 a + emoji(2) + b，所以第一行是 a + emoji，第二行是 b
        assertEquals(List.of("a😀", "b"), lines);
    }

    @Test
    void wrapClampsTinyWidth() {
        // maxWidth 低于 2 时被钳制，不死循环
        List<String> lines = TextWrap.wrap("abc", 0);
        assertEquals(List.of("ab", "c"), lines);
    }
}
