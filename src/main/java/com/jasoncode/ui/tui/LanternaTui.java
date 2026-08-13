package com.jasoncode.ui.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.ExtendedTerminal;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.jasoncode.chat.ChatEngine;
import com.jasoncode.chat.ChatUi;
import com.jasoncode.chat.command.ChatCommand;
import com.jasoncode.chat.command.ChatContext;
import com.jasoncode.chat.command.CommandRegistry;
import com.jasoncode.chat.command.CommandResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 基于 Lanterna 的全屏 TUI（v0.4.0）：使用专业终端 UI 库替代手写转义序列，
 * 负责全屏绘制、输入聚焦、鼠标/键盘事件循环。
 * <p>
 * 架构上仍只替换 {@code ui/tui} 层，Agent 层（config/provider/chat/ChatEngine）零改动。
 */
public final class LanternaTui implements AutoCloseable {

    private static final TextColor BG_238 = new TextColor.Indexed(238);
    private static final TextColor BG_236 = new TextColor.Indexed(236);

    private static final int TOP_MARGIN = 1;
    private static final int BOTTOM_MARGIN = 3;
    private static final int RIGHT_MARGIN = 2;
    private static final int MAX_INPUT_ROWS = 5;

    private final Terminal terminal;
    private final Screen screen;
    private final ChatScreen chatScreen;
    private final ChatEngine engine;
    private final CommandRegistry registry;
    private final Supplier<String> contextInfo;

    private final InputBox inputBox = new InputBox();
    private int completionIndex = -1;
    private String hint;
    private volatile boolean running = true;

    private ChatScreen.Rendered lastRendered;
    private int lastViewportOffset;
    private int lastHistRows;
    private int lastInputRows;
    /** 上一次输入区布局，用于鼠标定位光标。 */
    private InputLayout lastInputLayout;
    private int lastContentCols;
    /** 历史区滚动位置：0 表示顶部；默认 autoScroll 到底部。 */
    private int viewportTop = 0;
    private boolean autoScroll = true;

    private final ChatUi screenUi = new ChatUi() {
        @Override
        public String readLine() {
            throw new UnsupportedOperationException("全屏模式不经 readLine 读取");
        }

        @Override
        public void showError(String message) {
            chatScreen.error(message);
        }

        @Override
        public void showWarning(String message) {
            chatScreen.note("⚠ " + message);
        }

        @Override
        public void println(String message) {
            chatScreen.note(message);
        }
    };

    public LanternaTui(ChatScreen screen, ChatEngine engine, CommandRegistry registry,
                       Supplier<String> contextInfo) throws IOException {
        this.chatScreen = screen;
        this.engine = engine;
        this.registry = registry;
        this.contextInfo = contextInfo;
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setForceTextTerminal(true);
        factory.setUnixTerminalCtrlCBehaviour(com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal.CtrlCBehaviour.TRAP);
        this.terminal = factory.createTerminal();
        this.screen = new TerminalScreen(terminal);
    }

    /** 运行交互主循环，直到退出。 */
    public void run() throws IOException {
        try {
            if (terminal instanceof ExtendedTerminal et) {
                et.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE);
            }
            screen.startScreen();
            while (running) {
                try {
                    // 一次性处理当前队列中所有事件，避免轮询间隔造成滚轮/按键卡顿
                    KeyStroke key;
                    while ((key = terminal.pollInput()) != null) {
                        handle(key);
                    }
                    redraw();
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            try {
                terminal.setCursorVisible(true);
            } catch (IOException ignored) {
            }
            if (terminal instanceof ExtendedTerminal et) {
                try { et.setMouseCaptureMode(null); } catch (IOException ignored) {}
            }
            screen.stopScreen();
        }
    }

    @Override
    public void close() {
        // 终端生命周期由调用方（Main）管理
    }

    private void handle(KeyStroke key) {
        if (key.getKeyType() == KeyType.MouseEvent) {
            if (key instanceof MouseAction mouse) {
                handleMouse(mouse);
            }
            return;
        }
        switch (key.getKeyType()) {
            case Escape -> inputBox.setFocused(false);
            case Enter -> {
                inputBox.setFocused(true);
                if (key.isShiftDown() || key.isCtrlDown() || key.isAltDown()) {
                    inputBox.insertNewline();
                    completionIndex = -1;
                } else {
                    submit();
                }
            }
            case Backspace -> {
                if (inputBox.isFocused()) {
                    inputBox.backspace();
                }
                hint = null;
            }
            case Delete -> {
                if (inputBox.isFocused()) {
                    inputBox.delete();
                }
                hint = null;
            }
            case Home -> {
                if (inputBox.isFocused()) {
                    inputBox.moveCursorHome();
                }
            }
            case End -> {
                if (inputBox.isFocused()) {
                    inputBox.moveCursorEnd();
                }
            }
            case ArrowLeft -> {
                if (inputBox.isFocused()) {
                    inputBox.moveCursorLeft();
                }
            }
            case ArrowRight -> {
                if (inputBox.isFocused()) {
                    inputBox.moveCursorRight();
                }
            }
            case Tab -> complete();
            case ArrowUp -> {
                if (inputBox.isFocused()) {
                    inputBox.moveCursorUp(lastContentCols);
                }
            }
            case ArrowDown -> {
                if (inputBox.isFocused()) {
                    inputBox.moveCursorDown(lastContentCols);
                }
            }
            case Character -> handleCharacter(key.getCharacter(), key.isCtrlDown());
            default -> {
                // 忽略其他键
            }
        }
    }

    private void handleCharacter(char c, boolean ctrl) {
        if (ctrl && (c == 'c' || c == 'C' || c == 'd' || c == 'D')) {
            running = false;
            return;
        }
        if (ctrl && c == 't') { // Ctrl+T
            toggleLastBlock();
            return;
        }
        if (ctrl && (c == 'p' || c == 'P')) { // Ctrl+P 上一条历史
            inputBox.historyUp();
            return;
        }
        if (ctrl && (c == 'n' || c == 'N')) { // Ctrl+N 下一条历史
            inputBox.historyDown();
            return;
        }
        if (c >= 32) {
            inputBox.setFocused(true);
            inputBox.insert(c);
            hint = null;
            completionIndex = -1;
        }
    }

    private void handleMouse(MouseAction mouse) {
        MouseActionType type = mouse.getActionType();
        if (type == MouseActionType.SCROLL_UP) {
            autoScroll = false;
            viewportTop = Math.max(0, viewportTop - 3);
            return;
        }
        if (type == MouseActionType.SCROLL_DOWN) {
            autoScroll = false;
            viewportTop += 3;
            return;
        }
        if (type != MouseActionType.CLICK_DOWN) {
            return; // 忽略移动、释放
        }
        int row = mouse.getPosition().getRow();
        int inputRow = TOP_MARGIN + lastHistRows + 1;
        if (row >= inputRow && row < inputRow + lastInputRows) {
            inputBox.setFocused(true);
            int contentCols = Math.max(20, screen.getTerminalSize().getColumns() - RIGHT_MARGIN);
            inputBox.moveCursorTo(row - inputRow, mouse.getPosition().getColumn(), contentCols);
            return;
        }
        if (row >= TOP_MARGIN && row < TOP_MARGIN + lastHistRows && lastRendered != null) {
            int lineIndex = lastViewportOffset + (row - TOP_MARGIN);
            CollapsibleBlock target = lastRendered.targetAt(lineIndex);
            if (target != null) {
                target.toggle();
                return;
            }
        }
        inputBox.setFocused(false); // 点击其他区域：取消聚焦
    }

    private void complete() {
        String text = inputBox.text();
        if (text.isEmpty() || text.charAt(0) != '/') {
            return;
        }
        List<String> matches = new ArrayList<>();
        for (ChatCommand command : registry.all()) {
            String name = "/" + command.name();
            if (name.startsWith(text) || text.equals(name)) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            hint = "无匹配命令（/help 查看可用命令）";
            return;
        }
        completionIndex = (completionIndex + 1) % matches.size();
        inputBox.setText(matches.get(completionIndex));
        hint = "候选：" + String.join("  ", matches) + "（Tab 切换）";
    }

    private void submit() {
        String text = inputBox.text().trim();
        inputBox.clear();
        completionIndex = -1;
        hint = null;
        autoScroll = true;
        if (text.isEmpty()) {
            return;
        }
        inputBox.addHistory(text);
        if (CommandRegistry.isCommand(text)) {
            if (registry.dispatch(text, new ChatContext(screenUi)) == CommandResult.EXIT) {
                running = false;
            }
            return;
        }
        if (engine.tryDirectSubmit(text)) {
            return;
        }
        chatScreen.enqueue(text);
        engine.enqueue(text);
    }

    private void toggleLastBlock() {
        if (lastRendered == null) {
            return;
        }
        for (int i = lastRendered.lines().size() - 1; i >= 0; i--) {
            CollapsibleBlock target = lastRendered.targetAt(i);
            if (target != null) {
                target.toggle();
                return;
            }
        }
    }

    private void redraw() throws IOException {
        screen.doResizeIfNecessary();

        TerminalSize size = screen.getTerminalSize();
        int rows = Math.max(8, size.getRows());
        int cols = Math.max(30, size.getColumns());
        int contentCols = Math.max(20, cols - RIGHT_MARGIN);
        lastContentCols = contentCols;
        int histRows = Math.max(1, rows - 2 - TOP_MARGIN - BOTTOM_MARGIN);

        ChatScreen.Rendered rendered = chatScreen.render(contentCols);
        lastRendered = rendered;
        List<StyledLine> all = rendered.lines();
        int bottomOffset = Math.max(0, all.size() - histRows);
        if (autoScroll) {
            viewportTop = bottomOffset;
        } else {
            viewportTop = Math.max(0, Math.min(viewportTop, bottomOffset));
        }
        lastViewportOffset = viewportTop;
        lastHistRows = histRows;

        TextGraphics g = screen.newTextGraphics();

        // 整个界面使用终端默认背景；每帧先清屏，避免中文 IME / CJK 双宽字符残留旧内容
        screen.clear();

        // 历史区（底部对齐滚动）
        for (int i = 0; i < histRows; i++) {
            int lineIndex = viewportTop + i;
            StyledLine line = lineIndex < all.size() ? all.get(lineIndex) : StyledLine.empty();
            drawLine(g, 0, TOP_MARGIN + i, line);
        }

        // 状态栏
        int statusRow = TOP_MARGIN + histRows;
        drawLine(g, 0, statusRow, StyledLine.styled(ScreenItem.INDENT + statusText(), Style.DIM));

        // 输入框（无色背景，与终端默认背景一致）
        int inputRow = statusRow + 1;
        InputLayout inputLayout = inputBox.build(contentCols);
        lastInputLayout = inputLayout;
        List<StyledLine> inputLines = renderInputLines(inputLayout);
        int inputRows = inputLines.size();
        lastInputRows = inputRows;
        for (int r = 0; r < inputRows; r++) {
            drawLine(g, 0, inputRow + r, inputLines.get(r));
        }

        // 光标
        if (inputBox.isFocused()) {
            int cursorCol = inputLayout.cursorCol();
            int cursorRow = inputRow + inputLayout.cursorRow();
            screen.setCursorPosition(new TerminalPosition(cursorCol, cursorRow));
            terminal.setCursorVisible(true);
        } else {
            terminal.setCursorVisible(false);
        }

        screen.refresh();
    }

    /**
     * 将 {@link InputLayout} 渲染为带样式的 {@link StyledLine} 列表。
     * <p>
     * 第一行显示提示符（You ❯），后续行用等宽空格缩进；
     * 未聚焦且空输入时显示占位提示。
     */
    private List<StyledLine> renderInputLines(InputLayout layout) {
        List<StyledLine> result = new ArrayList<>();
        boolean showPlaceholder = !inputBox.isFocused() && inputBox.text().isEmpty();
        for (int i = 0; i < layout.rawLines().size(); i++) {
            result.add(buildInputLine(layout, i, showPlaceholder));
        }
        return result;
    }

    private StyledLine buildInputLine(InputLayout layout, int lineIndex, boolean showPlaceholder) {
        String rawText = layout.rawLines().get(lineIndex);
        boolean isPromptLine = (lineIndex == 0 && layout.startLine() == 0);
        String displayText = (showPlaceholder && lineIndex == 0) ? "点击此处或直接按键开始输入" : rawText;
        String indentSpaces = " ".repeat(layout.promptWidth());

        List<StyledSpan> spans = new ArrayList<>();
        if (inputBox.isFocused()) {
            if (isPromptLine) {
                spans.add(new StyledSpan(ScreenItem.INDENT, Style.DEFAULT));
                spans.add(new StyledSpan("You", Style.CYAN_BOLD));
                spans.add(new StyledSpan(" ❯ ", Style.CYAN));
            } else {
                spans.add(new StyledSpan(indentSpaces, Style.DEFAULT));
            }
            spans.add(new StyledSpan(displayText, Style.USER_TEXT));
        } else {
            Style dim = new Style(new TextColor.Indexed(245), null, false, false);
            if (isPromptLine) {
                spans.add(new StyledSpan(ScreenItem.INDENT, dim));
                spans.add(new StyledSpan("You ❯ ", dim));
            } else {
                spans.add(new StyledSpan(indentSpaces, dim));
            }
            spans.add(new StyledSpan(displayText, dim));
        }
        return new StyledLine(spans);
    }

    private void fillBackground(TextGraphics g, int startCol, int row, int cols, TextColor bg) {
        if (cols <= 0) return;
        TextColor prev = g.getBackgroundColor();
        g.setBackgroundColor(bg);
        g.fillRectangle(new TerminalPosition(startCol, row), new TerminalSize(cols, 1), ' ');
        g.setBackgroundColor(prev);
    }

    private int drawLine(TextGraphics g, int col, int row, StyledLine line) {
        int x = col;
        for (StyledSpan span : line.spans()) {
            applyStyle(g, span.style());
            g.putString(x, row, span.text());
            x += TextWrap.width(span.text());
        }
        return x;
    }

    /** 用默认背景填充一行中从 startCol 到右边界，避免旧内容残留。 */
    private void fillRestOfLine(TextGraphics g, int row, int startCol, int cols) {
        if (startCol >= cols) {
            return;
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
        g.disableModifiers(SGR.BOLD);
        g.fillRectangle(new TerminalPosition(startCol, row), new TerminalSize(cols - startCol, 1), ' ');
    }

    /** 用指定背景色填充一个矩形区域。 */
    private void fillRectangle(TextGraphics g, int col, int row, int width, int height, TextColor bg) {
        if (width <= 0 || height <= 0) {
            return;
        }
        TextColor prev = g.getBackgroundColor();
        g.setBackgroundColor(bg);
        g.fillRectangle(new TerminalPosition(col, row), new TerminalSize(width, height), ' ');
        g.setBackgroundColor(prev);
    }

    private void applyStyle(TextGraphics g, Style style) {
        if (style.foreground() != null) {
            g.setForegroundColor(style.foreground());
        } else {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
        }
        if (style.background() != null) {
            g.setBackgroundColor(style.background());
        } else {
            g.setBackgroundColor(TextColor.ANSI.DEFAULT);
        }
        if (style.bold()) {
            g.enableModifiers(SGR.BOLD);
        } else {
            g.disableModifiers(SGR.BOLD);
        }
    }

    private String statusText() {
        StringBuilder sb = new StringBuilder("⚙ ");
        sb.append(contextInfo.get());
        if (engine.isBusy()) {
            sb.append(" │ 生成中…");
        }
        int depth = chatScreen.queueDepth();
        if (depth > 0) {
            sb.append(" │ 队列: ").append(depth);
        }
        if (hint != null) {
            sb.append("   ").append(hint);
        }
        return sb.toString();
    }
}
