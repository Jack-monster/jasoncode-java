package com.jasoncode.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * YAML 配置加载与校验。
 * <p>
 * 校验项：文件存在可读、providers 非空、必填字段非空、protocol 合法、
 * name 不重复、default 存在于 providers。
 */
public final class ConfigLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ConfigLoader() {
    }

    public static JasonConfig load(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigException("配置文件不存在：" + path
                    + "（请创建该文件，或用 --config 指定其他路径）");
        }
        if (!Files.isReadable(path)) {
            throw new ConfigException("配置文件不可读：" + path);
        }

        JasonConfig config;
        try {
            config = YAML.readValue(path.toFile(), JasonConfig.class);
        } catch (ConfigException e) {
            throw e;
        } catch (IOException e) {
            throw new ConfigException("配置文件解析失败：" + path + "（" + firstLine(e.getMessage()) + "）", e);
        }
        if (config == null) {
            throw new ConfigException("配置文件内容为空：" + path);
        }
        validate(config, path);
        return config;
    }

    private static void validate(JasonConfig config, Path path) {
        if (config.providers() == null || config.providers().isEmpty()) {
            throw new ConfigException("配置文件 " + path + " 中 providers 不能为空");
        }
        Set<String> names = new HashSet<>();
        for (ProviderConfig p : config.providers()) {
            String where = p.name() == null || p.name().isBlank() ? "某个供应商" : "供应商 " + p.name();
            requireNonBlank(p.name(), "name", where, path);
            if (!names.add(p.name())) {
                throw new ConfigException("配置文件 " + path + " 中供应商名重复：" + p.name());
            }
            if (p.protocol() == null) {
                throw new ConfigException("配置文件 " + path + " 中 " + where + " 缺少 protocol");
            }
            requireNonBlank(p.model(), "model", where, path);
            requireNonBlank(p.baseUrl(), "base_url", where, path);
            requireNonBlank(p.apiKey(), "api_key", where, path);
        }
        if (config.defaultProvider() == null || config.defaultProvider().isBlank()) {
            throw new ConfigException("配置文件 " + path + " 缺少 default 字段（指定默认供应商名）");
        }
        if (config.findByName(config.defaultProvider()) == null) {
            throw new ConfigException("配置文件 " + path + " 中 default 指向的供应商不存在："
                    + config.defaultProvider());
        }
    }

    private static void requireNonBlank(String value, String field, String where, Path path) {
        if (value == null || value.isBlank()) {
            throw new ConfigException("配置文件 " + path + " 中 " + where + " 缺少字段 " + field);
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "未知错误";
        }
        int idx = message.indexOf('\n');
        return idx < 0 ? message : message.substring(0, idx);
    }
}
