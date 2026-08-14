package com.jasoncode.ui.tui;

import com.jasoncode.chat.ChatEngine;
import com.jasoncode.chat.ChatUi;
import com.jasoncode.chat.command.ChatCommand;
import com.jasoncode.chat.command.ChatContext;
import com.jasoncode.chat.command.CommandRegistry;
import com.jasoncode.chat.command.CommandResult;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.PasteMessage;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseAction;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseButton;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage;
import com.williamcallahan.tui4j.compat.bubbles.viewport.Viewport;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 基于 tui4j 的全屏 TUI：Elm Architecture（Model/update/view），
 * 使用 Viewport 组件处理滚动内容区、自绘 {@link InputBox} 处理多行输入、
 * lipgloss 处理样式。
 * <p>
 * 不使用 tui4j Textarea：其单词级 wrap 无法处理超长无空格内容，
 * 光标样式与内部滚动也不可控；自绘输入框的折行/光标/高度完全自管理。
 * <p>
 * 架构上只替换 {@code ui/tui} 层，Agent 层（config/provider/chat/ChatEngine）零改动。
 */
public final class Tui4jChat implements Model {

    private static final int STATUS_HEIGHT = 1;
    private static final int COMPLETION_HEIGHT = 1;
    /** 历史区与分割线之间的留白行数。 */
    private static final int GAP = 1;

    private final ChatScreen chatScreen;
    private final ChatEngine engine;
    private final CommandRegistry registry;
    private final Supplier<String> contextInfo;

    private final InputBox inputBox = new InputBox();
    private Viewport viewport;
    private Program program;

    private volatile int width = 80;
    private volatile int height = 24;
    private volatile boolean running = true;
    private boolean shouldQuit;
    private Thread pollThread;

    private final List<String> inputHistory = new ArrayList<>();
    private int historyIndex = -1;
    private int completionIndex = -1;
    private List<String> completionMatches = new ArrayList<>();
    private String hint;
    private String lastContent = "";
    private String viewportContent = "";
    private ChatScreen.Rendered lastRendered;
    private volatile boolean stickToBottom = true;

    private final Style dimStyle = Style.newStyle().foreground(Color.color("245"));

    // ChatUi wrapper for command dispatch
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

    public Tui4jChat(ChatScreen screen, ChatEngine engine, CommandRegistry registry,
                     Supplier<String> contextInfo) {
        this.chatScreen = screen;
        this.engine = engine;
        this.registry = registry;
        this.contextInfo = contextInfo;
    }

    @Override
    public Command init() {
        inputBox.setPlaceholder("点击此处或直接按键开始输入");

        viewport = Viewport.create(width, viewportHeight());
        viewport.setMouseWheelEnabled(true);

        // 初始内容
        updateViewportContent();

        // 启动后台轮询线程：每 50ms 检测 ChatScreen 内容变化，变化时 send ChatUpdateMessage
        // tui4j 的 Command.every() 是单次触发而非持续 ticker，因此用后台线程实现持续轮询
        pollThread = new Thread(this::pollLoop, "tui4j-poll");
        pollThread.setDaemon(true);
        pollThread.start();

        return Command.none();
    }

    private void pollLoop() {
        while (running) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (program == null) {
                continue;
            }
            ChatScreen.Rendered rendered = chatScreen.render(width);
            // 仅在跟随底部时检测内容变化，避免用户上滑后频繁更新
            if (stickToBottom && !rendered.content().equals(lastContent)) {
                program.send(new ChatUpdateMessage());
            }
        }
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        shouldQuit = false;

        if (msg instanceof WindowSizeMessage w) {
            width = w.width();
            height = w.height();
            viewport.setWidth(width);
            updateViewportContent(true);
            return UpdateResult.from(this);
        }

        if (msg instanceof ChatUpdateMessage) {
            updateViewportContent();
            return UpdateResult.from(this);
        }

        if (msg instanceof KeyPressMessage key) {
            return handleKey(key);
        }

        if (msg instanceof PasteMessage paste) {
            inputBox.insert(paste.content());
            updateCompletion();
            return UpdateResult.from(this);
        }

        if (msg instanceof MouseMessage mouse) {
            return handleMouse(mouse);
        }

        return UpdateResult.from(this);
    }

    @Override
    public String view() {
        // 历史区与输入区的分界线：颜色 240（中灰）在浅色/深色终端均清晰可见
        String separator = Style.newStyle().foreground(Color.color("240"))
                .render("─".repeat(Math.max(1, width)));
        String completion = completionView();
        StringBuilder sb = new StringBuilder();
        // 每帧同步历史区高度：输入区高度随内容伸缩，同步调整后
        // viewport.view() 恰好 viewportHeight 行、inputBox.render(width)
        // 恰好 inputBox.height(width) 行，总行数恒等于 height，不会残影。
        viewport.setHeight(viewportHeight());
        sb.append(viewport.view());
        // 历史区与输入区之间留出一个空行，再以分割线分隔，避免视觉粘连
        sb.append("\n");
        sb.append(separator).append("\n");
        sb.append(statusBar()).append("\n");
        sb.append(completion).append("\n");
        sb.append(inputBox.render(width));
        // 精确补足到 height 行：任何行数不足都会让 RendererFlush 的
        // 逐行 diff 与 \033[J 清屏逻辑失配，底部残留上一帧内容。
        int rendered = countLines(sb.toString());
        for (int i = rendered; i < height; i++) {
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 统计字符串中的行数（以 \n 结尾的行计为一行）。 */
    private static int countLines(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private String completionView() {
        Style dim = Style.newStyle().foreground(Color.color("244"));
        if (!completionMatches.isEmpty()) {
            Style matchStyle = Style.newStyle().foreground(Color.color("39"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < completionMatches.size(); i++) {
                if (i > 0) {
                    sb.append(dim.render("  "));
                }
                String m = completionMatches.get(i);
                if (i == completionIndex && completionIndex >= 0) {
                    sb.append(Style.newStyle().foreground(Color.color("214")).bold(true).render(m));
                } else {
                    sb.append(matchStyle.render(m));
                }
            }
            sb.append(dim.render("  Tab 切换"));
            return sb.toString();
        }
        if (hint != null && !hint.isEmpty()) {
            return dim.render(hint);
        }
        return "";
    }

    /** 运行交互主循环，直到退出。 */
    public void run() {
        this.program = new Program(this).withAltScreen().withMouseCellMotion();
        program.run();
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }

    // ── 键盘处理 ──

    private UpdateResult<? extends Model> handleKey(KeyPressMessage key) {
        String k = key.key();
    
        if ("ctrl+c".equals(k) || "ctrl+d".equals(k)) {
            running = false;
            return UpdateResult.from(this, Command.quit());
        }
    
        if ("ctrl+t".equals(k)) {
            boolean wasStick = stickToBottom;
            toggleLastBlock();
            updateViewportContent(true);
            stickToBottom = wasStick;
            return UpdateResult.from(this);
        }
    
        if ("ctrl+p".equals(k)) {
            historyUp();
            updateCompletion();
            return UpdateResult.from(this);
        }
        
        if ("ctrl+n".equals(k)) {
            historyDown();
            updateCompletion();
            return UpdateResult.from(this);
        }
    
        if ("enter".equals(k)) {
            submit();
            if (shouldQuit) {
                return UpdateResult.from(this, Command.quit());
            }
            return UpdateResult.from(this);
        }
    
        // shift+enter / ctrl+enter / alt+enter → 插入换行
        if ("shift+enter".equals(k) || "ctrl+enter".equals(k) || "alt+enter".equals(k)) {
            inputBox.insert("\n");
            updateCompletion();
            return UpdateResult.from(this);
        }
    
        if ("tab".equals(k)) {
            complete();
            return UpdateResult.from(this);
        }
    
        // 页面滚动键交给 viewport（历史区）
        if ("pgup".equals(k) || "pgdown".equals(k)) {
            viewport.update(key);
            return UpdateResult.from(this);
        }

        // 光标移动与编辑（自绘输入框，光标按 code point 移动，不会卡在半个中文里）
        switch (k) {
            case "left" -> inputBox.cursorLeft();
            case "right" -> inputBox.cursorRight();
            case "up" -> inputBox.cursorUp();
            case "down" -> inputBox.cursorDown();
            case "home" -> inputBox.home();
            case "end" -> inputBox.end();
            case "backspace" -> {
                inputBox.backspace();
                updateCompletion();
            }
            case "delete" -> {
                inputBox.deleteForward();
                updateCompletion();
            }
            default -> {
                // 可打印字符（含 IME 上屏的多字符）插入光标处
                char[] runes = key.runes();
                if (runes != null && runes.length > 0) {
                    inputBox.insert(new String(runes));
                    updateCompletion();
                }
            }
        }
        return UpdateResult.from(this);
    }

    // ── 鼠标处理 ──

    private UpdateResult<? extends Model> handleMouse(MouseMessage mouse) {
        // Viewport.update() 不处理 MouseMessage，需手动调用 scrollUp/scrollDown
        if (mouse.getButton() == MouseButton.MouseButtonWheelUp) {
            viewport.scrollUp(3);
            stickToBottom = false;
            return UpdateResult.from(this);
        }
        if (mouse.getButton() == MouseButton.MouseButtonWheelDown) {
            viewport.scrollDown(3);
            if (viewport.atBottom()) {
                stickToBottom = true;
                updateViewportContent(true);
            }
            return UpdateResult.from(this);
        }

        // 左键点击：检查是否命中折叠块标题行
        if (mouse.getButton() == MouseButton.MouseButtonLeft
                && mouse.getAction() == MouseAction.MouseActionPress) {
            int clickLine = viewport.getYOffset() + mouse.row();
            if (lastRendered != null) {
                CollapsibleBlock target = lastRendered.targetAt(clickLine);
                if (target != null) {
                    // 切换折叠块时不强制滚到底部
                    boolean wasStick = stickToBottom;
                    target.toggle();
                    updateViewportContent(true);
                    stickToBottom = wasStick;
                    return UpdateResult.from(this);
                }
            }
            // 点击其他区域无需处理（自绘输入框始终聚焦）
        }

        return UpdateResult.from(this);
    }

    // ── 业务逻辑 ──

    private void submit() {
        String text = inputBox.value().trim();
        inputBox.reset();
        completionIndex = -1;
        completionMatches.clear();
        hint = null;
        stickToBottom = true;
        if (text.isEmpty()) {
            return;
        }
        addHistory(text);
        if (CommandRegistry.isCommand(text)) {
            if (registry.dispatch(text, new ChatContext(screenUi)) == CommandResult.EXIT) {
                shouldQuit = true;
            }
            return;
        }
        if (engine.tryDirectSubmit(text)) {
            return;
        }
        chatScreen.enqueue(text);
        engine.enqueue(text);
    }

    private void complete() {
        if (completionMatches.isEmpty()) {
            return;
        }
        completionIndex = (completionIndex + 1) % completionMatches.size();
        inputBox.setValue(completionMatches.get(completionIndex));
    }

    /** 实时前缀匹配：每次按键后检查输入内容。 */
    private void updateCompletion() {
        String text = inputBox.value();
        if (text.isEmpty() || text.charAt(0) != '/') {
            completionMatches.clear();
            completionIndex = -1;
            hint = null;
            return;
        }
        List<String> newMatches = new ArrayList<>();
        for (ChatCommand command : registry.all()) {
            String name = "/" + command.name();
            if (name.startsWith(text)) {
                newMatches.add(name);
            }
        }
        // 匹配列表变化时重置索引
        if (!newMatches.equals(completionMatches)) {
            completionIndex = -1;
        }
        completionMatches = newMatches;
        if (completionMatches.isEmpty()) {
            hint = "无匹配命令（/help 查看可用命令）";
        } else if (completionMatches.size() == 1 && completionMatches.get(0).equals(text)) {
            hint = null; // 精确匹配，无需提示
        } else {
            hint = "";  // 非 null 触发 completionView() 显示候选列表
        }
        // 输入内容变化后重新匹配（内容宽度由 InputBox 自行硬折行，无需额外守护）
    }

    private void historyUp() {
        if (historyIndex > 0) {
            historyIndex--;
            inputBox.setValue(inputHistory.get(historyIndex));
        }
    }

    private void historyDown() {
        if (historyIndex < inputHistory.size() - 1) {
            historyIndex++;
            inputBox.setValue(inputHistory.get(historyIndex));
        } else {
            historyIndex = inputHistory.size();
            inputBox.setValue("");
        }
    }

    private void addHistory(String text) {
        if (inputHistory.isEmpty() || !inputHistory.get(inputHistory.size() - 1).equals(text)) {
            inputHistory.add(text);
        }
        historyIndex = inputHistory.size();
    }

    private void toggleLastBlock() {
        if (lastRendered == null || lastRendered.headerLines().isEmpty()) {
            return;
        }
        Integer lastKey = lastRendered.headerLines().lastKey();
        CollapsibleBlock target = lastRendered.headerLines().get(lastKey);
        if (target != null) {
            target.toggle();
        }
    }

    /** 历史区高度 = 总高 - 留白 - 分割线 - 状态栏 - 补全行 - 输入区（随内容伸缩）。 */
    private int viewportHeight() {
        return Math.max(1, height - GAP - 1 - STATUS_HEIGHT - COMPLETION_HEIGHT - inputBox.height(width));
    }

    private void updateViewportContent() {
        updateViewportContent(false);
    }

    /**
     * 更新 viewport 内容。
     *
     * @param force true 时即使 stickToBottom=false 也更新（用于用户交互：
     *              点击展开/收起、Ctrl+T、滚到底部、窗口缩放）
     */
    private void updateViewportContent(boolean force) {
        ChatScreen.Rendered rendered = chatScreen.render(width);
        boolean contentChanged = !rendered.content().equals(viewportContent);
        if ((stickToBottom || force) && contentChanged) {
            viewportContent = rendered.content();
            viewport.setContent(rendered.content());
            lastRendered = rendered;
            if (stickToBottom) {
                viewport.gotoBottom();
            }
        }
        // 始终同步 lastContent 供 pollLoop 检测
        lastContent = rendered.content();
    }

    private String statusBar() {
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
        return dimStyle.render(sb.toString());
    }
}
