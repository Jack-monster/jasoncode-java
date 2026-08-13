package com.jasoncode.chat.command;

/**
 * /exit：退出对话。
 */
public final class ExitCommand implements ChatCommand {

    @Override
    public String name() {
        return "exit";
    }

    @Override
    public String description() {
        return "退出 JasonCode";
    }

    @Override
    public CommandResult execute(ChatContext ctx, String args) {
        ctx.ui().println("再见 👋");
        return CommandResult.EXIT;
    }
}
