package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownRenderer 测试：加粗、斜体、代码、列表、链接、折行、代码块、引用、标题、表格。
 * <p>
 * render() 返回 ANSI 样式字符串，测试通过 contains 检查文本内容。
 */
class MarkdownRendererTest {

    @BeforeAll
    static void setupTerminalInfo() {
        TerminalInfo.provide(() -> new TerminalInfo(false, null));
    }

    @Test
    void rendersBoldAndItalic() {
        String result = MarkdownRenderer.render("这是**粗体**和*斜体*。", 80);
        assertTrue(result.contains("粗体"));
        assertTrue(result.contains("斜体"));
    }

    @Test
    void rendersBoldWithAsteriskConflict() {
        // 确保 ** 不会被错误解析为 * 斜体
        String result = MarkdownRenderer.render("**bold** and *italic*", 80);
        assertTrue(result.contains("bold"));
        assertTrue(result.contains("italic"));
        assertTrue(result.contains(" and "));
    }

    @Test
    void rendersInlineCode() {
        String result = MarkdownRenderer.render("使用 `java -jar` 启动。", 80);
        assertTrue(result.contains("java -jar"));
    }

    @Test
    void rendersHeader() {
        String result = MarkdownRenderer.render("## 二级标题", 80);
        assertTrue(result.contains("二级标题"));
        // 标题上下有分隔空行
        assertTrue(result.startsWith("\n"));
        assertTrue(result.endsWith("\n\n"));
    }

    @Test
    void rendersAllHeaderLevels() {
        for (int i = 1; i <= 6; i++) {
            String result = MarkdownRenderer.render("#".repeat(i) + " 标题", 80);
            assertTrue(result.contains("标题"));
            assertTrue(result.contains("\n\n"));
        }
    }

    @Test
    void rendersBulletAndCodeBlock() {
        String md = "- 列表项 A\n```\ncode\n```\n- 列表项 B";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("列表项 A"));
        assertTrue(result.contains("code"));
        assertTrue(result.contains("列表项 B"));
    }

    @Test
    void rendersCodeBlockWithLanguage() {
        String md = "```java\npublic class A {}\n```";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("java"));
        assertTrue(result.contains("public class A {}"));
        // 框线边框替代 ``` 标记
        assertTrue(result.contains("┌"));
        assertTrue(result.contains("└"));
        assertTrue(result.contains("│"));
    }

    @Test
    void rendersNestedLists() {
        String md = "- 一级\n  - 二级 A\n  - 二级 B\n- 一级二";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("一级"));
        assertTrue(result.contains("二级 A"));
        assertTrue(result.contains("二级 B"));
        assertTrue(result.contains("一级二"));
    }

    @Test
    void rendersOrderedList() {
        String md = "1. 第一项\n2. 第二项\n3. 第三项";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("1. 第一项"));
        assertTrue(result.contains("2. 第二项"));
        assertTrue(result.contains("3. 第三项"));
    }

    @Test
    void rendersBlockquote() {
        String md = "> 这是引用\n> 多行引用";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("│"));
        assertTrue(result.contains("这是引用"));
        assertTrue(result.contains("多行引用"));
    }

    @Test
    void rendersInlineLink() {
        String result = MarkdownRenderer.render("点击 [这里](https://example.com) 查看。", 80);
        assertTrue(result.contains("这里"));
        assertTrue(result.contains("点击"));
        assertTrue(result.contains("查看"));
    }

    @Test
    void wrapsParagraphWithIndent() {
        String result = MarkdownRenderer.render("这是一段非常非常长的文本，应该会被折行。", 20);
        // 折行后应该有多行
        assertTrue(result.contains("\n"));
    }

    @Test
    void wrapsListContinuationWithIndent() {
        String result = MarkdownRenderer.render("- 这是一个非常非常长的列表项，应该会被折行。", 24);
        assertTrue(result.contains("\n"));
    }

    @Test
    void codeBlockPreservesLineBreaks() {
        String md = "```\nline1\nline2\nline3\n```";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
        assertTrue(result.contains("line3"));
    }

    @Test
    void rendersTable() {
        String md = "| 姓名 | 年龄 |\n|------|------|\n| 张三 | 30 |\n| 李四 | 25 |";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("姓名"));
        assertTrue(result.contains("年龄"));
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("李四"));
        // 应该有表格边框
        assertTrue(result.contains("┌"));
        assertTrue(result.contains("┐"));
        assertTrue(result.contains("└"));
        assertTrue(result.contains("┘"));
        assertTrue(result.contains("│"));
        assertTrue(result.contains("─"));
    }

    @Test
    void rendersTableWithBoldCells() {
        String md = "| 命令 | 说明 |\n|------|------|\n| **bold** | `code` |";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("命令"));
        assertTrue(result.contains("说明"));
        assertTrue(result.contains("bold"));
        assertTrue(result.contains("code"));
    }

    @Test
    void rendersTableWithAlignment() {
        String md = "| 左 | 中 | 右 |\n|:---|:---:|---:|\n| a | b | c |";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("左"));
        assertTrue(result.contains("中"));
        assertTrue(result.contains("右"));
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    void rendersTableFollowedByParagraph() {
        String md = "| H |\n|---|\n| a |\n\n后续段落。";
        String result = MarkdownRenderer.render(md, 80);
        assertTrue(result.contains("H"));
        assertTrue(result.contains("a"));
        assertTrue(result.contains("后续段落"));
    }
}
