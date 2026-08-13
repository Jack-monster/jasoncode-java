package com.jasoncode.ui;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IndentingWriter 左侧留白测试（F3）：行首缩进、空行不缩进、跨次写入连续。
 */
class IndentingWriterTest {

    @Test
    void indentsEveryNonBlankLine() throws Exception {
        StringWriter target = new StringWriter();
        IndentingWriter writer = new IndentingWriter(target, "  ");

        writer.write("abc\ndef\n");
        writer.flush();

        assertEquals("  abc\n  def\n", target.toString());
    }

    @Test
    void blankLinesStayBlank() throws Exception {
        StringWriter target = new StringWriter();
        IndentingWriter writer = new IndentingWriter(target, "  ");

        writer.write("a\n\n\nb\n");
        writer.flush();

        assertEquals("  a\n\n\n  b\n", target.toString());
    }

    @Test
    void indentAppliesAcrossSeparateWrites() throws Exception {
        StringWriter target = new StringWriter();
        IndentingWriter writer = new IndentingWriter(target, ">>");

        writer.write("line1\n");
        writer.write("line2");
        writer.flush();

        assertEquals(">>line1\n>>line2", target.toString());
    }
}
