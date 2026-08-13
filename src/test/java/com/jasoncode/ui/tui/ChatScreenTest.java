package com.jasoncode.ui.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatScreen 屏幕模型测试（F3/F5/F9）：队列标记生命周期、思考/正文流转、鼠标命中映射。
 */
class ChatScreenTest {

    private static String plain(List<StyledLine> lines) {
        return String.join("\n", lines.stream().map(StyledLine::plain).toList());
    }

    @Test
    void queuedItemsShowMarkerUntilConsumed() {
        ChatScreen screen = new ChatScreen();
        screen.enqueue("第二条");
        screen.enqueue("第三条");
        assertEquals(2, screen.queueDepth());
        String out = plain(screen.render(80).lines());
        assertTrue(out.contains("⧗ queued: 第二条"));
        assertTrue(out.contains("⧗ queued: 第三条"));

        List<String> texts = screen.consumeQueued();
        assertEquals(List.of("第二条", "第三条"), texts);
        assertEquals(0, screen.queueDepth());
        assertFalse(plain(screen.render(80).lines()).contains("queued")); // 消费后标记消失
    }

    @Test
    void thinkingDeltaThenTextDeltaFinishesThinkingBlock() {
        ChatScreen screen = new ChatScreen();
        screen.addUser("你好");
        screen.beginTurn();
        screen.thinkingDelta("先分析");
        screen.thinkingDelta("再作答");
        // 思考进行中：标题为"思考中..."且内容展开
        String streaming = plain(screen.render(80).lines());
        assertTrue(streaming.contains("● 思考中..."));
        assertTrue(streaming.contains("先分析再作答"));

        screen.textDelta("回答");
        screen.textDelta("正文");
        String done = plain(screen.render(80).lines());
        assertFalse(done.contains("思考中...")); // 首个正文 delta 到达即收尾思考块
        assertTrue(done.contains("思考内容")); // 完成态标题（自动收起）
        assertTrue(done.contains("回答正文"));
        assertEquals("回答正文", screen.takeTurnText());
    }

    @Test
    void hitTargetsOnlyOnCollapsibleTitleLine() {
        ChatScreen screen = new ChatScreen();
        screen.beginTurn();
        screen.thinkingDelta("思考");
        screen.textDelta("正文内容");
        ChatScreen.Rendered rendered = screen.render(80);
        int titleLine = -1;
        for (int i = 0; i < rendered.lines().size(); i++) {
            if (rendered.lines().get(i).plain().contains("思考内容")) {
                titleLine = i;
            } else {
                assertNull(rendered.targetAt(i)); // 其余行不可点击
            }
        }
        assertTrue(titleLine >= 0);
        assertNotNull(rendered.targetAt(titleLine)); // 仅折叠块标题行命中
    }
}
