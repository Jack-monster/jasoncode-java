package com.jasoncode.ui.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownRenderer 测试：加粗、斜体、代码、列表、链接、折行。
 */
class MarkdownRendererTest {

    private static String plain(List<StyledLine> lines) {
        return String.join("\n", lines.stream().map(StyledLine::plain).toList());
    }

    @Test
    void rendersBoldAndItalic() {
        List<StyledLine> lines = MarkdownRenderer.render("这是**粗体**和*斜体*。", 80);
        String out = plain(lines);
        assertTrue(out.contains("粗体"));
        assertTrue(out.contains("斜体"));
    }

    @Test
    void rendersInlineCode() {
        List<StyledLine> lines = MarkdownRenderer.render("使用 `java -jar` 启动。", 80);
        String out = plain(lines);
        assertTrue(out.contains("java -jar"));
    }

    @Test
    void rendersHeader() {
        List<StyledLine> lines = MarkdownRenderer.render("## 二级标题", 80);
        String out = plain(lines);
        assertTrue(out.contains("二级标题"));
    }

    @Test
    void rendersBulletAndCodeBlock() {
        String md = "- 列表项 A\n```\ncode\n```\n- 列表项 B";
        List<StyledLine> lines = MarkdownRenderer.render(md, 80);
        String out = plain(lines);
        assertTrue(out.contains("列表项 A"));
        assertTrue(out.contains("code"));
        assertTrue(out.contains("列表项 B"));
    }

    @Test
    void wrapsParagraphWithIndent() {
        List<StyledLine> lines = MarkdownRenderer.render("这是一段非常非常长的文本，应该会被折行。", 20);
        assertTrue(lines.size() > 1);
        // 续行保留缩进
        assertTrue(lines.get(1).plain().startsWith(ScreenItem.INDENT));
    }
}
