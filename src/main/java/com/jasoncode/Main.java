package com.jasoncode;

import com.jasoncode.chat.ChatEngine;
import com.jasoncode.chat.Conversation;
import com.jasoncode.chat.ConversationLoop;
import com.jasoncode.chat.command.CommandRegistry;
import com.jasoncode.config.ConfigException;
import com.jasoncode.config.ConfigLoader;
import com.jasoncode.config.ConfigResolver;
import com.jasoncode.config.JasonConfig;
import com.jasoncode.config.Protocol;
import com.jasoncode.config.ProviderConfig;
import com.jasoncode.history.InMemoryHistoryStore;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ChatProvider;
import com.jasoncode.provider.ProviderFactory;
import com.jasoncode.ui.AnsiColors;
import com.jasoncode.ui.Banner;
import com.jasoncode.ui.ConsoleUi;
import com.jasoncode.ui.StreamRenderer;
import com.jasoncode.ui.tui.ChatScreen;
import com.jasoncode.ui.tui.LanternaTui;
import com.jasoncode.ui.tui.ScreenItem;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * JasonCode 入口：参数解析 → 配置加载 → 供应商选择 → 组装 → 对话循环。
 */
@Command(
        name = "jasoncode",
        mixinStandardHelpOptions = true,
        version = "JasonCode 0.4.0（一期工程：终端流式对话）",
        description = "终端 AI 助手：支持 OpenAI / Anthropic 协议的流式多轮对话。"
)
public final class Main implements Callable<Integer> {

    public static final String VERSION = "0.4.0";

    @Option(names = {"-p", "--provider"},
            description = "指定本次会话使用的供应商名（覆盖配置文件中的 default）")
    private String providerName;

    @Option(names = "--config",
            description = "指定配置文件路径（默认 ~/.jasoncode/config.yaml）")
    private Path configPath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            return run();
        } catch (Throwable t) {
            // 全局兜底：可读消息，绝不泄露密钥（密钥仅存在于配置对象，错误路径不打印配置）
            System.err.println("JasonCode 运行出错：" + t.getMessage());
            return 1;
        }
    }

    private int run() throws IOException {
        Path path = resolveConfigPath();
        if (path == null) {
            return 1; // 已生成模板并提示用户，本次不进入对话
        }

        JasonConfig config;
        try {
            config = ConfigLoader.load(path);
        } catch (ConfigException e) {
            System.err.println("配置错误：" + e.getMessage());
            return 1;
        }

        String selectedName = providerName != null ? providerName : config.defaultProvider();
        ProviderConfig providerConfig = config.findByName(selectedName);
        if (providerConfig == null) {
            System.err.println("供应商 \"" + selectedName + "\" 不在配置文件 " + path + " 中。"
                    + "可用的供应商：" + config.providers().stream().map(ProviderConfig::name)
                    .reduce((a, b) -> a + ", " + b).orElse("（无）"));
            return 1;
        }

        AnsiColors colors = new AnsiColors();
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true) // 管道/非交互环境降级为 dumb terminal，不产生乱码
                .build();
        try {
            if (Terminal.TYPE_DUMB.equals(terminal.getType())) {
                return runLineMode(terminal, colors, providerConfig); // 降级：逐行纯文本
            }
        } finally {
            terminal.close(); // 关闭 JLine 终端，让 Lanterna 重新接管（N8）
        }
        return runFullScreen(providerConfig); // 交互：基于 Lanterna 的全屏 TUI
    }

    /** 全屏 TUI（F3/F5/F9）：基于 Lanterna 的三区布局 + 异步对话引擎 + 输入队列。 */
    private int runFullScreen(ProviderConfig providerConfig) throws IOException {
        ChatProvider provider = ProviderFactory.create(providerConfig);
        CommandRegistry registry = CommandRegistry.defaults();
        Conversation conversation = new Conversation(new InMemoryHistoryStore());
        ChatScreen screen = new ChatScreen();
        screen.addBanner(new ScreenItem.BannerItem(Banner.loadArt(), VERSION, provider.describe()));
        // openai 协议不支持扩展思考：一次性警告，不阻断（F5）
        if (providerConfig.thinking() && providerConfig.protocol() == Protocol.OPENAI) {
            screen.note("⚠ openai 协议不支持扩展思考，供应商 " + providerConfig.name()
                    + " 的 thinking 配置将被忽略");
        }
        try (ChatEngine engine = new ChatEngine(provider, conversation.history(), screen)) {
            engine.start();
            LanternaTui tui = new LanternaTui(screen, engine, registry,
                    () -> statusLine(providerConfig, conversation));
            tui.run();
        }
        return 0;
    }

    /** 行模式降级（管道/哑终端，F3）：逐行纯文本，无全屏接管。 */
    private int runLineMode(Terminal terminal, AnsiColors colors,
                            ProviderConfig providerConfig) throws IOException {
        try (ConsoleUi ui = new ConsoleUi(terminal, colors)) {
            // openai 协议不支持扩展思考：一次性警告，不阻断（F5）
            if (providerConfig.thinking() && providerConfig.protocol() == Protocol.OPENAI) {
                ui.showWarning("openai 协议不支持扩展思考，供应商 " + providerConfig.name()
                        + " 的 thinking 配置将被忽略");
            }
            ChatProvider provider = ProviderFactory.create(providerConfig);
            CommandRegistry registry = CommandRegistry.defaults();
            Conversation conversation = new Conversation(new InMemoryHistoryStore());
            ui.setCommands(registry.all());
            ui.setStatusInfo(() -> statusLine(providerConfig, conversation));
            try (StreamRenderer renderer = new StreamRenderer(ui.writer(), colors)) {
                renderer.start();
                ui.printBanner(VERSION, provider.describe());
                ConversationLoop loop = new ConversationLoop(ui, renderer, provider, conversation, registry);
                loop.run();
            }
            return 0;
        }
    }

    /** 状态栏内容（F3）：供应商·模型（协议）│ 轮次 │ 上下文占用（历史总字符数）。 */
    static String statusLine(ProviderConfig providerConfig, Conversation conversation) {
        List<ChatMessage> history = conversation.history().snapshot();
        int chars = history.stream().mapToInt(m -> m.content().length()).sum();
        int turns = history.size() / 2;
        return String.format("%s · %s (%s) │ 轮次: %d │ 上下文: %s",
                providerConfig.name(), providerConfig.model(),
                providerConfig.protocol().name().toLowerCase(), turns, humanCount(chars));
    }

    private static String humanCount(int n) {
        if (n < 1000) {
            return n + "字符";
        }
        return String.format("%.1fk字符", n / 1000.0);
    }

    /**
     * 解析配置文件路径（F1）：
     * 显式 --config 直接用；否则按 用户目录 → 运行目录 搜索；
     * 均不存在时在用户目录生成模板并提示填写，返回 null。
     */
    private Path resolveConfigPath() {
        if (configPath != null) {
            return configPath;
        }
        Path homeDir = Path.of(System.getProperty("user.home"));
        Path workDir = Path.of("").toAbsolutePath();
        Path found = ConfigResolver.findExisting(homeDir, workDir);
        if (found != null) {
            return found;
        }
        Path template = ConfigResolver.homeConfigPath(homeDir);
        try {
            ConfigResolver.createTemplate(template);
        } catch (IOException e) {
            System.err.println("生成配置模板失败：" + template + "（" + e.getMessage() + "）");
            return null;
        }
        System.err.println("未找到配置文件，已生成模板：" + template);
        System.err.println("请打开该文件填写 api_key 等字段，然后重新运行 JasonCode。");
        return null;
    }
}
