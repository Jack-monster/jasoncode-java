package com.jasoncode.ui.tui;

import com.googlecode.lanterna.TextColor;

/**
 * 终端样式描述（v0.4.0 Lanterna 迁移）：仅含颜色/SGR 标记，不依赖具体渲染后端。
 * <p>
 * 该记录与 Lanterna 的 {@link TextColor} 解耦了“如何生成 ANSI 转义序列”，
 * 但保留了颜色语义；若未来再次替换 UI 框架，只需修改 {@link LanternaTui} 中的转换层。
 */
public record Style(TextColor foreground, TextColor background, boolean bold, boolean dim) {

    public Style withBackground(TextColor bg) {
        return new Style(foreground, bg, bold, dim);
    }

    public static final Style DEFAULT = new Style(TextColor.ANSI.DEFAULT, null, false, false);
    public static final Style DIM = new Style(new TextColor.Indexed(245), null, false, false);
    public static final Style YELLOW_BOLD = new Style(TextColor.ANSI.YELLOW, null, true, false);
    public static final Style USER_TEXT = new Style(TextColor.ANSI.WHITE, null, false, false);
    public static final Style STATUS_STYLE = new Style(new TextColor.Indexed(250), new TextColor.Indexed(236), false, false);
    public static final Style CYAN_BOLD = new Style(TextColor.ANSI.CYAN, null, true, false);
    public static final Style CYAN = new Style(TextColor.ANSI.CYAN, null, false, false);
    public static final Style MAGENTA = new Style(TextColor.ANSI.MAGENTA, null, false, false);
    public static final Style RED_BOLD = new Style(TextColor.ANSI.RED, null, true, false);
}
