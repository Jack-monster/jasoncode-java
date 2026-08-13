package com.jasoncode.ui;

import com.jasoncode.chat.ChatUi;
import com.jasoncode.chat.command.ChatCommand;
import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 终端门面：Banner、带色提示符输入（含 ↑/↓ 历史、输入背景色、/ 命令补全）、
 * 输入框上方状态行、错误/警告输出。
 * <p>
 * 基于 JLine：close 时恢复终端状态（N8）；管道环境自动降级为 dumb terminal。
 * 全部输出经 {@link IndentingWriter} 统一左侧留白（F3）。
 */
public final class ConsoleUi implements ChatUi, AutoCloseable {

    /** 输出左侧统一缩进（左右留白，F3）。 */
    private static final String INDENT = "  ";

    private final Terminal terminal;
    private final LineReader lineReader;
    private final AnsiColors colors;
    private final PrintWriter out;
    private final String prompt;

    private volatile Collection<ChatCommand> commands = List.of();
    private volatile Supplier<String> statusInfo;

    public ConsoleUi(AnsiColors colors) throws IOException {
        this.colors = colors;
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true) // 管道/非交互环境降级为 dumb terminal，不产生乱码
                .build();
        this.out = new PrintWriter(new IndentingWriter(terminal.writer(), INDENT), false);
        this.prompt = buildPrompt();
        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(this::completeCommands)
                .highlighter(inputHighlighter())
                .build();
    }

    /** 输出 writer（StreamRenderer 用它打印流式内容，含左侧留白）。 */
    public PrintWriter writer() {
        return out;
    }

    /** 注册可补全的会话命令（候选列表随注册表动态变化，F3）。 */
    public void setCommands(Collection<ChatCommand> commands) {
        this.commands = commands;
    }

    /** 注册状态行内容供应器，每次等待输入前刷新（F3）。 */
    public void setStatusInfo(Supplier<String> statusInfo) {
        this.statusInfo = statusInfo;
    }

    /** 启动横幅：动漫女孩 Banner + 软件信息 + 当前供应商/模型（F3）。 */
    public void printBanner(String version, String providerDescription) {
        Banner.print(out, colors, version, providerDescription);
    }

    /**
     * 读取一行输入。
     *
     * @return 用户输入；null 表示退出（EOF/Ctrl+D 或 Ctrl+C）
     */
    @Override
    public String readLine() {
        refreshStatus();
        try {
            return lineReader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    @Override
    public void showError(String message) {
        out.println(colors.red("✗ " + message));
        out.flush();
    }

    @Override
    public void showWarning(String message) {
        out.println(colors.yellow("⚠ " + message));
        out.flush();
    }

    @Override
    public void println(String message) {
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

    /** 固定带色提示符（F3）：You ❯，随输出一起内缩；无颜色环境降级为纯文本。 */
    private String buildPrompt() {
        if (!colors.isEnabled()) {
            return INDENT + "You ❯ ";
        }
        return new AttributedStringBuilder()
                .append(INDENT)
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append("You")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(" ❯ ")
                .toAnsi();
    }

    /** 输入文本背景色高亮（F3）：编辑中与提交后均与输出内容区分。 */
    private Highlighter inputHighlighter() {
        return new Highlighter() {
            @Override
            public AttributedString highlight(LineReader reader, String buffer) {
                if (!colors.isEnabled() || buffer.isEmpty()) {
                    return new AttributedString(buffer);
                }
                return new AttributedString(buffer,
                        AttributedStyle.DEFAULT
                                .foreground(AttributedStyle.WHITE)
                                .background(238)); // 深灰底
            }

            @Override
            public void setErrorPattern(Pattern errorPattern) {
                // 一期无错误定位需求
            }

            @Override
            public void setErrorIndex(int errorIndex) {
                // 一期无错误定位需求
            }
        };
    }

    /** "/" 命令 Tab 补全：候选来自命令注册表（F3）。 */
    private void completeCommands(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        if (word == null || !word.startsWith("/")) {
            return;
        }
        for (ChatCommand command : commands) {
            String value = "/" + command.name();
            candidates.add(new Candidate(value, value, null, command.description(), null, null, true));
        }
    }

    private void refreshStatus() {
        Supplier<String> info = statusInfo;
        if (info == null) {
            return;
        }
        // 状态行紧邻输入框上方，前置空行与上一块输出分隔（F3 留白）
        out.println();
        out.println(colors.dim("⚙ " + info.get()));
        out.flush();
    }
}
