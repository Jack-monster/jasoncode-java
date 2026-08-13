package com.jasoncode.ui.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 宽度感知换行工具（全屏 TUI）：CJK/全角/Emoji 按 2 列计算，保证鼠标命中与布局一致。
 * <p>
 * 全部按 Unicode code point 处理，避免拆分 emoji 的 surrogate pair 导致乱码或 "??"。
 */
public final class TextWrap {

    private TextWrap() {
    }

    /** 单个 code point 的终端显示宽度（CJK/全角/Emoji 按 2 列）。 */
    public static int charWidth(int codePoint) {
        // CJK / 全角
        if ((codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)) {
            return 2;
        }
        // Emoji 及辅助平面字符大多占 2 列
        if (codePoint > 0xFFFF) {
            return 2;
        }
        // 常见的 BMP 表情/符号也按 2 列（终端通常如此）
        if (isBmpEmoji(codePoint)) {
            return 2;
        }
        return 1;
    }

    /** 兼容旧接口：单个 char（BMP）的显示宽度。 */
    public static int charWidth(char c) {
        return charWidth((int) c);
    }

    private static boolean isBmpEmoji(int codePoint) {
        return (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || (codePoint >= 0x2300 && codePoint <= 0x23FF)
                || (codePoint >= 0x2B50 && codePoint <= 0x2B55)
                || codePoint == 0x2764 || codePoint == 0x2122 || codePoint == 0x2190
                || codePoint == 0x2191 || codePoint == 0x2192 || codePoint == 0x2193;
    }

    public static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += charWidth(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    /** 按最大列宽换行（先按 \n 分段，再逐段贪心折行）；maxWidth 至少为 2。 */
    public static List<String> wrap(String text, int maxWidth) {
        int limit = Math.max(2, maxWidth);
        List<String> result = new ArrayList<>();
        for (String segment : text.split("\n", -1)) {
            if (segment.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            int w = 0;
            for (int i = 0; i < segment.length(); ) {
                int cp = segment.codePointAt(i);
                int cw = charWidth(cp);
                if (w + cw > limit) {
                    result.add(line.toString());
                    line.setLength(0);
                    w = 0;
                }
                line.appendCodePoint(cp);
                w += cw;
                i += Character.charCount(cp);
            }
            result.add(line.toString());
        }
        return result;
    }
}
