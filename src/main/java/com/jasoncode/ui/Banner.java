package com.jasoncode.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 启动 Banner（F3）：动漫女孩图案 + 清晰的程序名 + 软件信息区（版本、定位）
 * + 当前生效供应商/模型。图案存放于 classpath 的 banner.txt，换图不动代码。
 */
public final class Banner {

    private static final String ART_RESOURCE = "/banner.txt";

    private Banner() {
    }

    public static void print(PrintWriter out, AnsiColors colors,
                             String version, String providerDescription) {
        out.println(colors.magenta(loadArt().stripTrailing()));
        out.println();
        out.println(colors.bold(colors.cyan("JasonCode")) + "  " + colors.dim("v" + version));
        out.println("终端 AI 助手 · 一期工程（流式对话）");
        out.println("供应商：" + colors.cyan(providerDescription));
        out.println(colors.dim("输入消息开始对话；/ + Tab 查看命令；Ctrl+C 或 /exit 退出"));
        out.println();
        out.flush();
    }

    /** 读取图案资源；缺失时降级为纯文字，不阻断启动。 */
    static String loadArt() {
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
