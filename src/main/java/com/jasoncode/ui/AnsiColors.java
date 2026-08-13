package com.jasoncode.ui;

/**
 * ANSI 颜色工具：非终端环境（如管道重定向）自动降级为纯文本（N4）。
 */
public final class AnsiColors {

    private static final String RESET_CODE = "\u001B[0m";
    private static final String DIM_CODE = "\u001B[2m";
    private static final String CYAN_CODE = "\u001B[36m";
    private static final String RED_CODE = "\u001B[31m";
    private static final String YELLOW_CODE = "\u001B[33m";
    private static final String BOLD_CODE = "\u001B[1m";

    private final boolean enabled;

    public AnsiColors() {
        this(System.console() != null);
    }

    /** 测试/管道场景可显式指定是否启用颜色。 */
    public AnsiColors(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String dim(String text) {
        return wrap(DIM_CODE, text);
    }

    public String cyan(String text) {
        return wrap(CYAN_CODE, text);
    }

    public String red(String text) {
        return wrap(RED_CODE, text);
    }

    public String yellow(String text) {
        return wrap(YELLOW_CODE, text);
    }

    public String bold(String text) {
        return wrap(BOLD_CODE, text);
    }

    private String wrap(String code, String text) {
        return enabled ? code + text + RESET_CODE : text;
    }
}
