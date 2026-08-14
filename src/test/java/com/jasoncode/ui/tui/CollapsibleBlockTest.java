package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CollapsibleBlock 可折叠块测试（F5）：流式黄色标题、完成自动收起、点击切换仅完成态生效。
 * <p>
 * render() 返回 ANSI 样式字符串，测试通过 contains 检查文本内容。
 */
class CollapsibleBlockTest {

    @BeforeAll
    static void setupTerminalInfo() {
        TerminalInfo.provide(() -> new TerminalInfo(false, null));
    }

    @Test
    void streamingShowsYellowThinkingTitleAndLiveContent() {
        CollapsibleBlock block = new CollapsibleBlock("思考内容");
        block.append("正在推理");
        String out = block.render(80);
        assertTrue(block.isStreaming());
        assertTrue(out.contains("● 思考中..."));
        assertTrue(out.contains("正在推理")); // 流式中内容实时展开
    }

    @Test
    void finishCollapsesAndSwitchesTitle() {
        CollapsibleBlock block = new CollapsibleBlock("思考内容");
        block.append("思考细节");
        block.finish();
        assertFalse(block.isStreaming());
        assertFalse(block.isExpanded());
        String out = block.render(80);
        assertTrue(out.contains("▸") && out.contains("思考内容"));
        assertFalse(out.contains("思考细节")); // 收起后不渲染正文
        assertTrue(out.contains("点击展开"));
    }

    @Test
    void toggleExpandsAndCollapsesOnlyWhenFinished() {
        CollapsibleBlock block = new CollapsibleBlock("思考内容");
        block.append("思考细节");
        block.toggle(); // 流式中点击无效
        assertTrue(block.isExpanded());
        block.finish();
        assertFalse(block.isExpanded());
        block.toggle();
        assertTrue(block.isExpanded());
        String out = block.render(80);
        assertTrue(out.contains("│") && out.contains("思考细节")); // 展开后带左边界线
        assertTrue(out.contains("点击收起"));
        block.toggle();
        // 收起后仅标题行（只有 1 个 \n）
        String collapsed = block.render(80);
        long lineCount = collapsed.chars().filter(c -> c == '\n').count();
        assertEquals(1, lineCount); // 收起后仅标题行
    }
}
