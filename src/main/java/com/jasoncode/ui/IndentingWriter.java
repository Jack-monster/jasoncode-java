package com.jasoncode.ui;

import java.io.IOException;
import java.io.Writer;

/**
 * 左侧留白写入器（F3）：在每个逻辑行行首插入统一缩进，
 * 让输出不贴住终端左边缘。空行不插入缩进。
 */
public final class IndentingWriter extends Writer {

    private final Writer delegate;
    private final String indent;
    private boolean atLineStart = true;

    public IndentingWriter(Writer delegate, String indent) {
        this.delegate = delegate;
        this.indent = indent;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            char c = cbuf[off + i];
            if (atLineStart && c != '\n' && c != '\r') {
                delegate.write(indent);
                atLineStart = false;
            }
            delegate.write(c);
            if (c == '\n') {
                atLineStart = true;
            }
        }
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        write(str.toCharArray(), off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
