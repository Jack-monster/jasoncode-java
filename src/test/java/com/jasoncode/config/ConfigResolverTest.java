package com.jasoncode.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigResolverTest {

    @TempDir
    Path homeDir;

    @TempDir
    Path workDir;

    private Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    @Test
    void prefersHomeConfigWhenBothExist() throws IOException {
        Path home = write(ConfigResolver.homeConfigPath(homeDir), "default: h");
        write(ConfigResolver.workConfigPath(workDir), "default: w");

        assertEquals(home, ConfigResolver.findExisting(homeDir, workDir));
    }

    @Test
    void fallsBackToWorkDirConfig() throws IOException {
        Path work = write(ConfigResolver.workConfigPath(workDir), "default: w");

        assertEquals(work, ConfigResolver.findExisting(homeDir, workDir));
    }

    @Test
    void returnsNullWhenNeitherExists() {
        assertNull(ConfigResolver.findExisting(homeDir, workDir));
    }

    @Test
    void createTemplateWritesYamlWithPlaceholders() throws IOException {
        Path target = ConfigResolver.homeConfigPath(homeDir);

        ConfigResolver.createTemplate(target);

        assertTrue(Files.exists(target));
        String content = Files.readString(target);
        assertTrue(content.contains("default: kimi"));
        assertTrue(content.contains("api_key: sk-your-api-key-here"));
        assertTrue(content.contains("protocol: anthropic"));
    }

    @Test
    void createTemplateDoesNotOverwriteExistingFile() throws IOException {
        Path target = write(ConfigResolver.homeConfigPath(homeDir), "original");

        ConfigResolver.createTemplate(target);

        assertEquals("original", Files.readString(target));
    }
}
