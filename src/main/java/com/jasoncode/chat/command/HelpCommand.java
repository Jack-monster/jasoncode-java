package com.jasoncode.chat.command;

/**
 * /help：列出所有已注册命令。
 */
public final class HelpCommand implements ChatCommand {

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "列出可用命令";
    }

    @Override
    public CommandResult execute(ChatContext ctx, String args) {
        ctx.ui().println("可用命令：");
        for (ChatCommand command : registry.all()) {
            ctx.ui().println("  /" + command.name() + " — " + command.description());
        }
        return CommandResult.CONTINUE;
    }
}
