package com.jasoncode.config;

/**
 * 供应商协议类型：决定请求走哪家的 API 格式。
 */
public enum Protocol {
    OPENAI,
    ANTHROPIC;

    /**
     * 从配置字符串解析协议，非法值抛 {@link ConfigException}。
     */
    public static Protocol parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ConfigException("protocol 字段不能为空");
        }
        return switch (value.trim().toLowerCase()) {
            case "openai" -> OPENAI;
            case "anthropic" -> ANTHROPIC;
            default -> throw new ConfigException(
                    "protocol 取值非法：" + value + "（仅支持 openai / anthropic）");
        };
    }
}
