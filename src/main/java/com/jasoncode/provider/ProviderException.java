package com.jasoncode.provider;

/**
 * Provider 层异常：携带错误分类与人类可读消息。
 * <p>
 * 约定：message 构造路径中绝不包含完整 apiKey。
 */
public class ProviderException extends RuntimeException {

    public enum Category {
        /** 网络/连接层错误 */
        NETWORK,
        /** 认证失败（401/403） */
        AUTH,
        /** API 返回业务错误 */
        API,
        /** 响应解析失败 */
        PARSE
    }

    private final Category category;

    public ProviderException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public ProviderException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
