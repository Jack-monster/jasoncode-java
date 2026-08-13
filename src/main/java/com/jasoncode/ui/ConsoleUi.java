package com.jasoncode.ui;

import com.jasoncode.provider.ChatProvider;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 终端门面：LOGO 横幅、提示符输入（含 ↑/↓ 历史）、错误/警告输出。
 * <p>
 * 基于 JLine：close 时恢复终端状态（N8）；管道环境自动降级为 dumb terminal。
 */
public final class ConsoleUi implements AutoCloseable {

    private static final String LOGO = """
              ██╗  █████╗ ███████╗  ██████╗  ██████╗ ███╗   ██╗ ██████╗ ███████╗
              ██║ ██╔══██╗██╔════╝ ██╔═══██╗██╔════╝ ████╗  ██║ ██╔══██╗██╔════╝
              ██║ ███████║███████╗ ██║   ██║██║  ███╗██╔██╗ ██║ ██║  ██║█████╗
            ▄██║ ██╔══██║╚════██║ ██║   ██║██║   ██║██║╚██╗██║ ██║  ██║██╔══╝
            ▀██║ ██║  ██║███████║ ╚██████╔╝╚██████╔╝██║ ╚████║ ██████╔╝███████╗
             ╚═╝ ╚═╝  ╚═╝╚══════╝  ╚═════╝  ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚══════╝""";

    private final Terminal terminal;
    private final LineReader lineReader;
    private final AnsiColors colors;

    public ConsoleUi(AnsiColors colors) throws IOException {
        this.colors = colors;
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true) // 管道/非交互环境降级为 dumb terminal，不产生乱码
                .build();
        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    /** 输出 writer（StreamRenderer 用它打印流式内容）。 */
    public PrintWriter writer() {
        return terminal.writer();
    }

    /** 启动横幅：LOGO + 当前生效的供应商/模型信息（F3）。 */
    public void printBanner(ChatProvider provider) {
        PrintWriter out = terminal.writer();
        out.println(colors.cyan(LOGO));
        out.println();
        out.println(colors.bold("JasonCode") + " v0.1.0 — 终端 AI 助手（一期工程：纯对话）");
        out.println("当前供应商：" + colors.cyan(provider.describe()));
        out.println(colors.dim("输入消息开始对话；/help 查看命令；Ctrl+C 或 /exit 退出"));
        out.println();
        out.flush();
    }

    /**
     * 读取一行输入。
     *
     * @return 用户输入；null 表示退出（EOF/Ctrl+D 或 Ctrl+C）
     */
    public String readLine() {
        try {
            return lineReader.readLine(colors.cyan("❯ "));
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    public void showError(String message) {
        PrintWriter out = terminal.writer();
        out.println(colors.red("✗ " + message));
        out.flush();
    }

    public void showWarning(String message) {
        PrintWriter out = terminal.writer();
        out.println(colors.yellow("⚠ " + message));
        out.flush();
    }

    public void println(String message) {
        PrintWriter out = terminal.writer();
        out.println(message);
        out.flush();
    }

    /** 关闭终端，恢复原有终端状态（N8）。 */
    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignore) {
            // 退出阶段忽略
        }
    }
}
