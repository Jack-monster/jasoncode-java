package com.jasoncode.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 顶层配置：default 指向的供应商 + 供应商列表。
 */
public record JasonConfig(
        @JsonProperty("default") String defaultProvider,
        List<ProviderConfig> providers
) {

    /**
     * 按名称查找供应商，找不到返回 null。
     */
    public ProviderConfig findByName(String name) {
        if (providers == null || name == null) {
            return null;
        }
        return providers.stream()
                .filter(p -> name.equals(p.name()))
                .findFirst()
                .orElse(null);
    }
}
