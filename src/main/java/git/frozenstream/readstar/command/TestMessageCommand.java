package git.frozenstream.readstar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import git.frozenstream.readstar.network.NetworkHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 测试命令 - 用于测试服务端到客户端的网络通信
 */
public class TestMessageCommand {
    
    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("readstar")
            .then(Commands.literal("message")
                // 向所有玩家发送消息
                .then(Commands.literal("all")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> sendMessageToAll(context, StringArgumentType.getString(context, "message")))
                    )
                )
                // 向指定玩家发送消息
                .then(Commands.literal("player")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> sendMessageToPlayer(
                                context,
                                EntityArgument.getPlayer(context, "target"),
                                StringArgumentType.getString(context, "message")
                            ))
                        )
                    )
                )
            )
        );
    }

    /**
     * 向所有玩家发送消息
     */
    private static int sendMessageToAll(CommandContext<CommandSourceStack> context, String message) {
        NetworkHelper.sendMessageToAllPlayers(message);
        
        // 向命令执行者发送确认消息
        context.getSource().sendSuccess(
            () -> Component.literal("已向所有玩家发送消息: " + message),
            true
        );
        
        return 1;
    }

    /**
     * 向指定玩家发送消息
     */
    private static int sendMessageToPlayer(CommandContext<CommandSourceStack> context, ServerPlayer target, String message) {
        NetworkHelper.sendMessageToPlayer(target, message);
        
        // 向命令执行者发送确认消息
        context.getSource().sendSuccess(
            () -> Component.literal("已向玩家 " + target.getName().getString() + " 发送消息: " + message),
            true
        );
        
        return 1;
    }
}
