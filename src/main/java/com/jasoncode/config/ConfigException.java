package com.jasoncode.config;

/**
 * 配置加载/校验错误，message 为人类可读原因。
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
