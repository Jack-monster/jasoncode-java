package com.jasoncode.chat.command;

import com.jasoncode.chat.ChatUi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命令注册表与分发：输入以 "/" 开头时按名字查找命令执行；
 * 未知命令打印提示并继续。新增命令只需 register，不动循环逻辑。
 */
public final class CommandRegistry {

    private final Map<String, ChatCommand> commands = new LinkedHashMap<>();

    public void register(ChatCommand command) {
        commands.put(command.name(), command);
    }

    public Collection<ChatCommand> all() {
        return commands.values();
    }

    /** 判断输入是否为命令（以 / 开头）。 */
    public static boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }

    /**
     * 分发执行一条命令输入（如 "/exit" 或 "/help foo"）。
     */
    public CommandResult dispatch(String input, ChatContext ctx) {
        String body = input.substring(1).trim();
        String name;
        String args;
        int space = body.indexOf(' ');
        if (space < 0) {
            name = body;
            args = "";
        } else {
            name = body.substring(0, space);
            args = body.substring(space + 1).trim();
        }
        ChatCommand command = commands.get(name);
        if (command == null) {
            ChatUi ui = ctx.ui();
            ui.showError("未知命令：/" + name + "（输入 /help 查看可用命令）");
            return CommandResult.CONTINUE;
        }
        return command.execute(ctx, args);
    }

    /** 构建一期默认命令集：/exit、/help。 */
    public static CommandRegistry defaults() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new ExitCommand());
        registry.register(new HelpCommand(registry));
        return registry;
    }
}
