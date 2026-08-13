package com.jasoncode.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个供应商配置。
 * <p>
 * {@code toString()} 保证任何打印场景下 apiKey 只暴露末四位。
 */
public record ProviderConfig(
        String name,
        Protocol protocol,
        String model,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key") String apiKey,
        boolean thinking
) {

    @JsonCreator
    public static ProviderConfig fromYaml(
            @JsonProperty("name") String name,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("model") String model,
            @JsonProperty("base_url") String baseUrl,
            @JsonProperty("api_key") String apiKey,
            @JsonProperty("thinking") Boolean thinking
    ) {
        return new ProviderConfig(
                name,
                Protocol.parse(protocol),
                model,
                baseUrl,
                apiKey,
                Boolean.TRUE.equals(thinking)
        );
    }

    /**
     * 掩码后的密钥：**** + 末四位（不足四位全部掩码）。可安全打印。
     */
    @JsonIgnore
    public String maskedApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            return "****";
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public String toString() {
        return "ProviderConfig{name=" + name
                + ", protocol=" + protocol
                + ", model=" + model
                + ", base_url=" + baseUrl
                + ", api_key=" + maskedApiKey()
                + ", thinking=" + thinking
                + '}';
    }
}
