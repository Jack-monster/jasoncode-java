package com.jasoncode.ui.tui;

import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatScreen 屏幕模型测试（F3/F5/F9）：队列标记生命周期、思考/正文流转、鼠标命中映射。
 * <p>
 * render() 返回 Rendered(String content, TreeMap headerLines)，
 * 测试通过 content 字符串断言文本内容，通过 targetAt(line) 检查命中映射。
 */
class ChatScreenTest {

    @BeforeAll
    static void setupTerminalInfo() {
        TerminalInfo.provide(() -> new TerminalInfo(false, null));
    }

    @Test
    void queuedItemsShowMarkerUntilConsumed() {
        ChatScreen screen = new ChatScreen();
        screen.enqueue("第二条");
        screen.enqueue("第三条");
        assertEquals(2, screen.queueDepth());
        String out = screen.render(80).content();
        assertTrue(out.contains("⧗ queued: 第二条"));
        assertTrue(out.contains("⧗ queued: 第三条"));

        var texts = screen.consumeQueued();
        assertEquals(java.util.List.of("第二条", "第三条"), texts);
        assertEquals(0, screen.queueDepth());
        assertFalse(screen.render(80).content().contains("queued")); // 消费后标记消失
    }

    @Test
    void thinkingDeltaThenTextDeltaFinishesThinkingBlock() {
        ChatScreen screen = new ChatScreen();
        screen.addUser("你好");
        screen.beginTurn();
        screen.thinkingDelta("先分析");
        screen.thinkingDelta("再作答");
        // 思考进行中：标题为"思考中..."且内容展开
        String streaming = screen.render(80).content();
        assertTrue(streaming.contains("● 思考中..."));
        assertTrue(streaming.contains("先分析再作答"));

        screen.textDelta("回答");
        screen.textDelta("正文");
        String done = screen.render(80).content();
        assertFalse(done.contains("思考中...")); // 首个正文 delta 到达即收尾思考块
        assertTrue(done.contains("思考内容")); // 完成态标题（自动收起）
        assertTrue(done.contains("回答"));
        assertTrue(done.contains("正文"));
        assertEquals("回答正文", screen.takeTurnText());
    }

    @Test
    void hitTargetsOnlyOnCollapsibleTitleLine() {
        ChatScreen screen = new ChatScreen();
        screen.beginTurn();
        screen.thinkingDelta("思考");
        screen.textDelta("正文内容");
        ChatScreen.Rendered rendered = screen.render(80);
        String[] lines = rendered.content().split("\n", -1);
        int titleLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("思考内容")) {
                titleLine = i;
            } else {
                assertNull(rendered.targetAt(i)); // 其余行不可点击
            }
        }
        assertTrue(titleLine >= 0);
        assertNotNull(rendered.targetAt(titleLine)); // 仅折叠块标题行命中
    }
}
