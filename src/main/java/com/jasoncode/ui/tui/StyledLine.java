package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 一行由多个 {@link StyledSpan} 组成的文本（v0.4.0 Lanterna 迁移）。
 */
public final class StyledLine {

    private final List<StyledSpan> spans;

    public StyledLine(List<StyledSpan> spans) {
        this.spans = List.copyOf(spans);
    }

    public static StyledLine empty() {
        return new StyledLine(List.of());
    }

    public static StyledLine plain(String text) {
        return new StyledLine(List.of(new StyledSpan(text, Style.DEFAULT)));
    }

    public static StyledLine styled(String text, Style style) {
        return new StyledLine(List.of(new StyledSpan(text, style)));
    }

    public List<StyledSpan> spans() {
        return spans;
    }

    public String plain() {
        StringBuilder sb = new StringBuilder();
        for (StyledSpan span : spans) {
            sb.append(span.text());
        }
        return sb.toString();
    }

    public int width() {
        return TextWrap.width(plain());
    }

    /**
     * 按显示宽度折行，保持每个 span 的样式。
     * <p>
     * 仅按宽度切断，不自动添加缩进；需要缩进时由调用方在续行处补充。
     */
    public List<StyledLine> wrap(int maxWidth) {
        if (maxWidth <= 0) {
            return List.of(this);
        }
        List<StyledLine> lines = new ArrayList<>();
        List<StyledSpan> current = new ArrayList<>();
        int currentWidth = 0;
        for (StyledSpan span : spans) {
            if (span.text().isEmpty()) {
                continue;
            }
            int spanWidth = TextWrap.width(span.text());
            if (spanWidth <= 0) {
                continue;
            }
            if (currentWidth + spanWidth <= maxWidth) {
                current.add(span);
                currentWidth += spanWidth;
                continue;
            }
            // 当前 span 放不下了：先 flush 当前行
            if (!current.isEmpty()) {
                lines.add(new StyledLine(current));
                current = new ArrayList<>();
                currentWidth = 0;
            }
            // 拆分过长的 span
            List<StyledSpan> chunks = splitSpan(span, maxWidth);
            for (int i = 0; i < chunks.size(); i++) {
                StyledSpan chunk = chunks.get(i);
                if (i == chunks.size() - 1) {
                    current.add(chunk);
                    currentWidth = TextWrap.width(chunk.text());
                } else {
                    lines.add(new StyledLine(List.of(chunk)));
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(new StyledLine(current));
        }
        if (lines.isEmpty()) {
            lines.add(StyledLine.empty());
        }
        return lines;
    }

    private static List<StyledSpan> splitSpan(StyledSpan span, int maxWidth) {
        List<StyledSpan> chunks = new ArrayList<>();
        String text = span.text();
        Style style = span.style();
        StringBuilder current = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cw = TextWrap.charWidth(cp);
            if (w + cw > maxWidth && !current.isEmpty()) {
                chunks.add(new StyledSpan(current.toString(), style));
                current.setLength(0);
                w = 0;
            }
            current.appendCodePoint(cp);
            w += cw;
            i += Character.charCount(cp);
        }
        if (!current.isEmpty()) {
            chunks.add(new StyledSpan(current.toString(), style));
        }
        return chunks;
    }
}
