package com.jasoncode.ui.tui;

import java.util.List;

/**
 * 输入框的渲染布局（原始文本行 + 光标位置）。
 * <p>
 * 不包含样式，仅描述文本分行的结果和光标在屏幕上的位置，
 * 供 {@link LanternaTui} 渲染时添加颜色和提示符。
 */
public record InputLayout(
        List<String> rawLines,
        int cursorRow,
        int cursorCol,
        List<Integer> lineStarts,
        int startLine,
        int promptWidth) {
}
