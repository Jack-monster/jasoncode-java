package com.jasoncode.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动 Banner（F3）：动漫女孩图案 + 清晰的程序名 + 软件信息区（版本、定位）
 * + 当前生效供应商/模型。
 * <p>
 * 图案加载优先级：外部文件 {@code ~/.jasoncode/banner.txt}（可手动替换，免重新打包）
 * → classpath 内置 banner.txt（出厂默认）。
 */
public final class Banner {

    private static final String ART_RESOURCE = "/banner.txt";
    static final String EXTERNAL_ART_FILENAME = "banner.txt";

    private Banner() {
    }

    public static void print(PrintWriter out, AnsiColors colors,
                             String version, String providerDescription) {
        out.println(); // 顶部留白，避免贴住终端上边缘（F3）
        out.println(colors.magenta(loadArt().stripTrailing()));
        out.println();
        out.println(colors.bold(colors.cyan("JasonCode")) + "  " + colors.dim("v" + version));
        out.println("终端 AI 助手 · 一期工程（流式对话）");
        out.println("供应商：" + colors.cyan(providerDescription));
        out.println(colors.dim("输入消息开始对话；/ + Tab 查看命令；Ctrl+C 或 /exit 退出"));
        out.flush();
    }

    /** 用户目录下的自定义图案路径：~/.jasoncode/banner.txt。 */
    public static Path externalArtPath(Path homeDir) {
        return homeDir.resolve(".jasoncode").resolve(EXTERNAL_ART_FILENAME);
    }

    /** 加载图案：外部自定义文件优先，缺失时回退内置资源；均失败时降级为纯文字。 */
    public static String loadArt() {
        return loadArt(Path.of(System.getProperty("user.home")));
    }

    static String loadArt(Path homeDir) {
        Path external = externalArtPath(homeDir);
        try {
            if (Files.exists(external)) {
                String content = Files.readString(external, StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    return content;
                }
            }
        } catch (IOException e) {
            // 读取失败回退内置图案，不阻断启动
        }
        try (InputStream in = Banner.class.getResourceAsStream(ART_RESOURCE)) {
            if (in == null) {
                return "JasonCode";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "JasonCode";
        }
    }
}
