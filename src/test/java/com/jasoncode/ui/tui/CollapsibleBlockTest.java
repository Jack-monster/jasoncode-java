package com.jasoncode.ui.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CollapsibleBlock 可折叠块测试（F5）：流式黄色标题、完成自动收起、点击切换仅完成态生效。
 */
class CollapsibleBlockTest {

    private static String plain(List<StyledLine> lines) {
        return String.join("\n", lines.stream().map(StyledLine::plain).toList());
    }

    @Test
    void streamingShowsYellowThinkingTitleAndLiveContent() {
        CollapsibleBlock block = new CollapsibleBlock("思考内容");
        block.append("正在推理");
        String out = plain(block.render(80));
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
        String out = plain(block.render(80));
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
        String out = plain(block.render(80));
        assertTrue(out.contains("│") && out.contains("思考细节")); // 展开后带左边界线
        assertTrue(out.contains("点击收起"));
        block.toggle();
        assertEquals(1, block.render(80).size()); // 收起后仅标题行
    }
}
