package com.jasoncode.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banner 图案加载测试：外部自定义文件优先 → 内置资源回退 → 空白文件回退。
 */
class BannerTest {

    @TempDir
    Path homeDir;

    @Test
    void prefersExternalArtFile() throws IOException {
        Path external = Banner.externalArtPath(homeDir);
        Files.createDirectories(external.getParent());
        Files.writeString(external, "自定义图案");

        assertEquals("自定义图案", Banner.loadArt(homeDir));
    }

    @Test
    void fallsBackToBuiltinResourceWhenExternalMissing() {
        String art = Banner.loadArt(homeDir);

        assertTrue(art.contains("█"), "内置图案应包含 Banner 字符");
    }

    @Test
    void blankExternalFileFallsBackToBuiltin() throws IOException {
        Path external = Banner.externalArtPath(homeDir);
        Files.createDirectories(external.getParent());
        Files.writeString(external, "   \n  ");

        assertTrue(Banner.loadArt(homeDir).contains("█"), "空白自定义文件应回退内置图案");
    }
}
