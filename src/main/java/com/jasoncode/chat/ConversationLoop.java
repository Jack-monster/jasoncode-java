package com.jasoncode.chat;

import com.jasoncode.chat.command.ChatContext;
import com.jasoncode.chat.command.CommandRegistry;
import com.jasoncode.chat.command.CommandResult;
import com.jasoncode.history.HistoryStore;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ChatProvider;
import com.jasoncode.ui.StreamRenderer;

/**
 * 对话主循环：输入读取 → 命令分发 / 流式对话 → 错误兜底（F3/F6/F8）。
 */
public final class ConversationLoop {

    private final ChatUi ui;
    private final StreamRenderer renderer;
    private final ChatProvider provider;
    private final Conversation conversation;
    private final CommandRegistry registry;

    public ConversationLoop(ChatUi ui, StreamRenderer renderer, ChatProvider provider,
                            Conversation conversation, CommandRegistry registry) {
        this.ui = ui;
        this.renderer = renderer;
        this.provider = provider;
        this.conversation = conversation;
        this.registry = registry;
    }

    /** 运行主循环，直到用户退出。 */
    public void run() {
        ChatContext ctx = new ChatContext(ui);
        while (true) {
            String input = ui.readLine();
            if (input == null) {
                return; // EOF / Ctrl+C / Ctrl+D
            }
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (CommandRegistry.isCommand(trimmed)) {
                if (registry.dispatch(trimmed, ctx) == CommandResult.EXIT) {
                    return;
                }
                continue;
            }
            chatTurn(trimmed);
        }
    }

    private void chatTurn(String input) {
        HistoryStore history = conversation.history();
        history.append(ChatMessage.user(input));
        renderer.beginTurn();
        try {
            provider.streamChat(history.snapshot(), renderer).get();
            renderer.awaitDrain();
            history.append(ChatMessage.assistant(renderer.takeTurnText()));
        } catch (Exception e) {
            renderer.awaitDrain();
            // 回滚本轮 user 消息：Anthropic 要求 user/assistant 严格交替，
            // 失败轮次不能留下孤立的 user 消息（见 plan.md 单轮流程）
            history.removeLast();
            ui.showError(rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
