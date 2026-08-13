package com.jasoncode.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置路径解析（F1）：
 * <ol>
 *   <li>默认路径按顺序搜索：用户目录 {@code ~/.jasoncode/config.yaml}，
 *       其次运行目录 {@code ./.jasoncode/config.yaml}；</li>
 *   <li>两处均不存在时，在用户目录生成带注释的配置模板，提示用户填写。</li>
 * </ol>
 */
public final class ConfigResolver {

    static final String TEMPLATE = """
            # JasonCode 配置文件
            # 请填写 api_key 等字段后重新运行 JasonCode。字段说明见 README.md。

            # 启动时默认使用的供应商名（必须是下方某个 provider 的 name）
            default: kimi

            providers:
              # 示例 1：OpenAI 协议（兼容端点把 base_url 换成对应地址即可）
              - name: kimi
                protocol: openai
                model: moonshot-v1-8k
                base_url: https://api.moonshot.cn/v1
                api_key: sk-your-api-key-here          # ← 请替换为真实密钥

              # 示例 2：Anthropic 协议
              - name: claude
                protocol: anthropic
                model: claude-sonnet-4-5
                base_url: https://api.anthropic.com
                api_key: sk-ant-your-api-key-here      # ← 请替换为真实密钥
                thinking: true                         # 可选：启用扩展思考
            """;

    private ConfigResolver() {
    }

    /** 用户目录下的默认配置路径：~/.jasoncode/config.yaml */
    public static Path homeConfigPath(Path homeDir) {
        return homeDir.resolve(".jasoncode").resolve("config.yaml");
    }

    /** 运行目录下的配置路径：./.jasoncode/config.yaml */
    public static Path workConfigPath(Path workDir) {
        return workDir.resolve(".jasoncode").resolve("config.yaml");
    }

    /**
     * 按顺序查找已存在的配置文件：用户目录 → 运行目录。
     *
     * @return 找到的路径；均不存在时返回 null
     */
    public static Path findExisting(Path homeDir, Path workDir) {
        Path home = homeConfigPath(homeDir);
        if (Files.exists(home)) {
            return home;
        }
        Path work = workConfigPath(workDir);
        if (Files.exists(work)) {
            return work;
        }
        return null;
    }

    /**
     * 在目标路径生成配置模板（不覆盖已存在的文件）。
     *
     * @return 模板文件路径
     */
    public static Path createTemplate(Path target) throws IOException {
        if (Files.exists(target)) {
            return target;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, TEMPLATE, StandardCharsets.UTF_8);
        return target;
    }
}
