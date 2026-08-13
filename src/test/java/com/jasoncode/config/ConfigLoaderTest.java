package com.jasoncode.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private Path writeConfig(String yaml) throws IOException {
        Path path = tempDir.resolve("config.yaml");
        Files.writeString(path, yaml);
        return path;
    }

    private static final String VALID_YAML = """
            default: kimi
            providers:
              - name: kimi
                protocol: openai
                model: moonshot-v1-8k
                base_url: https://api.moonshot.cn/v1
                api_key: sk-moonshot-1234567890
              - name: claude
                protocol: anthropic
                model: claude-sonnet-4-5
                base_url: https://api.anthropic.com
                api_key: sk-ant-abcdef1234
                thinking: true
            """;

    @Test
    void loadsValidConfig() throws IOException {
        JasonConfig config = ConfigLoader.load(writeConfig(VALID_YAML));

        assertEquals("kimi", config.defaultProvider());
        assertEquals(2, config.providers().size());

        ProviderConfig kimi = config.findByName("kimi");
        assertEquals("kimi", kimi.name());
        assertEquals(Protocol.OPENAI, kimi.protocol());
        assertEquals("moonshot-v1-8k", kimi.model());
        assertEquals("https://api.moonshot.cn/v1", kimi.baseUrl());
        assertEquals("sk-moonshot-1234567890", kimi.apiKey());
        assertFalse(kimi.thinking());

        ProviderConfig claude = config.findByName("claude");
        assertEquals(Protocol.ANTHROPIC, claude.protocol());
        assertTrue(claude.thinking());
    }

    @Test
    void missingFileThrowsWithPathInMessage() {
        Path missing = tempDir.resolve("no-such-file.yaml");
        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(missing));
        assertTrue(e.getMessage().contains(missing.toString()));
    }

    @Test
    void missingApiKeyThrowsWithFieldAndProviderName() throws IOException {
        String yaml = """
                default: kimi
                providers:
                  - name: kimi
                    protocol: openai
                    model: moonshot-v1-8k
                    base_url: https://api.moonshot.cn/v1
                """;
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigLoader.load(writeConfig(yaml)));
        assertTrue(e.getMessage().contains("api_key"));
        assertTrue(e.getMessage().contains("kimi"));
    }

    @Test
    void illegalProtocolThrows() throws IOException {
        String yaml = """
                default: kimi
                providers:
                  - name: kimi
                    protocol: gemini
                    model: some-model
                    base_url: https://example.com
                    api_key: sk-1234
                """;
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigLoader.load(writeConfig(yaml)));
        assertTrue(e.getMessage().contains("protocol"));
        assertTrue(e.getMessage().contains("gemini"));
    }

    @Test
    void duplicateNamesThrow() throws IOException {
        String yaml = """
                default: kimi
                providers:
                  - name: kimi
                    protocol: openai
                    model: m1
                    base_url: https://a.example.com
                    api_key: sk-1
                  - name: kimi
                    protocol: anthropic
                    model: m2
                    base_url: https://b.example.com
                    api_key: sk-2
                """;
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigLoader.load(writeConfig(yaml)));
        assertTrue(e.getMessage().contains("重复"));
        assertTrue(e.getMessage().contains("kimi"));
    }

    @Test
    void defaultNotInProvidersThrows() throws IOException {
        String yaml = """
                default: notexist
                providers:
                  - name: kimi
                    protocol: openai
                    model: moonshot-v1-8k
                    base_url: https://api.moonshot.cn/v1
                    api_key: sk-1234
                """;
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigLoader.load(writeConfig(yaml)));
        assertTrue(e.getMessage().contains("notexist"));
    }

    @Test
    void toStringNeverExposesFullApiKey() throws IOException {
        JasonConfig config = ConfigLoader.load(writeConfig(VALID_YAML));
        String text = config.findByName("kimi").toString();
        assertFalse(text.contains("sk-moonshot-1234567890"), "toString 不得包含完整密钥");
        assertTrue(text.contains("****7890"), "toString 应只显示末四位");
    }

    @Test
    void shortApiKeyFullyMasked() {
        ProviderConfig p = new ProviderConfig(
                "x", Protocol.OPENAI, "m", "https://e.com", "abc", false);
        assertEquals("****", p.maskedApiKey());
    }
}
