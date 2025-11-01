package me.levikehh.hideandseek.commands;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import hu.nomindz.devkit.command.CommandBase;
import hu.nomindz.devkit.command.CommandProvider;
import me.levikehh.hideandseek.HideAndSeek;

public class HideAndSeekCommands implements CommandProvider {
    @Override
    public List<CommandBase> provide(JavaPlugin raw) {
        HideAndSeek plugin = (HideAndSeek) raw;

        CommandBase join = CommandBase.of("join")
                .playerOnly()
                .executor((server, sender, args) -> {
                    Player player = (Player) sender;

                    plugin.getGameManager().addToLobby(player);
                })
                .build();

        CommandBase leave = CommandBase.of("leave")
                .playerOnly()
                .executor((server, sender, args) -> {
                    Player player = (Player) sender;

                    plugin.getGameManager().removeFromLobby(player);
                })
                .build();

        CommandBase hideAndSeekRoot = CommandBase.of("hideandseek")
                .alias("hns")
                .alias("hs")
                .child(join)
                .child(leave)
                .build();

        return List.of(hideAndSeekRoot);
    }
}
