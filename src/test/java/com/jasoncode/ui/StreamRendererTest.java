package com.jasoncode.ui;

import com.jasoncode.provider.StreamEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StreamRenderer 分块渲染测试（F5）：思考块标题/分隔线时机、无思考时无块结构。
 */
class StreamRendererTest {

    private StreamRenderer renderer;

    @AfterEach
    void tearDown() {
        if (renderer != null) {
            renderer.close();
        }
    }

    private StringWriter render(StreamEvent... events) {
        StringWriter out = new StringWriter();
        renderer = new StreamRenderer(new PrintWriter(out, true), new AnsiColors(false));
        renderer.start();
        renderer.beginTurn();
        for (StreamEvent event : events) {
            renderer.onEvent(event);
        }
        renderer.awaitDrain();
        return out;
    }

    @Test
    void thinkingThenText_printsBlockStructureInOrder() {
        StringWriter out = render(
                new StreamEvent.ThinkingDelta("让我想想"),
                new StreamEvent.ThinkingDelta("，答案是"),
                new StreamEvent.TextDelta("42"),
                new StreamEvent.Done());

        String result = out.toString();
        int header = result.indexOf("✦ Thinking");
        int thinkingText = result.indexOf("让我想想");
        int separator = result.indexOf("── Answer");
        int answerText = result.indexOf("42");
        assertTrue(header >= 0, "应有思考块标题");
        assertTrue(thinkingText > header, "思考内容在标题之后");
        assertTrue(separator > thinkingText, "分隔线在思考内容之后");
        assertTrue(answerText > separator, "正文在分隔线之后");
        // 标题只出现一次（多个 ThinkingDelta 不重复打印）
        assertEquals(header, result.lastIndexOf("✦ Thinking"));
        assertEquals("42", renderer.takeTurnText());
    }

    @Test
    void textOnly_printsNoBlockStructure() {
        StringWriter out = render(
                new StreamEvent.TextDelta("直接回答"),
                new StreamEvent.Done());

        String result = out.toString();
        assertTrue(result.contains("直接回答"));
        assertFalse(result.contains("✦ Thinking"), "无思考时不应打印思考标题");
        assertFalse(result.contains("── Answer"), "无思考时不应打印分隔线");
        assertEquals("直接回答", renderer.takeTurnText());
    }

    @Test
    void blockState_resetsBetweenTurns() {
        StringWriter out = new StringWriter();
        renderer = new StreamRenderer(new PrintWriter(out, true), new AnsiColors(false));
        renderer.start();

        // 第一轮：有思考
        renderer.beginTurn();
        renderer.onEvent(new StreamEvent.ThinkingDelta("思考"));
        renderer.onEvent(new StreamEvent.TextDelta("答1"));
        renderer.onEvent(new StreamEvent.Done());
        renderer.awaitDrain();

        // 第二轮：只有正文，不应残留第一轮的块状态
        renderer.beginTurn();
        renderer.onEvent(new StreamEvent.TextDelta("答2"));
        renderer.onEvent(new StreamEvent.Done());
        renderer.awaitDrain();

        String result = out.toString();
        assertEquals(result.indexOf("✦ Thinking"), result.lastIndexOf("✦ Thinking"),
                "思考标题只属于第一轮");
        assertTrue(result.indexOf("答2") > result.lastIndexOf("── Answer"),
                "第二轮正文前不应再出现分隔线");
    }
}
